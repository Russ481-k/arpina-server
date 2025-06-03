package cms.kispg.service.impl;

import cms.common.exception.BusinessRuleException;
import cms.common.exception.ErrorCode;
import cms.common.exception.ResourceNotFoundException;
import cms.enroll.domain.Enroll;
import cms.enroll.domain.MembershipType;
import cms.enroll.repository.EnrollRepository;
import cms.kispg.dto.KispgInitParamsDto;
import cms.kispg.service.KispgPaymentService;
import cms.locker.service.LockerService;
import cms.swimming.domain.Lesson;
import cms.swimming.repository.LessonRepository;
import cms.user.domain.User;
import cms.swimming.dto.EnrollRequestDto;
import cms.payment.domain.Payment;
import cms.payment.repository.PaymentRepository;
import cms.mypage.dto.EnrollDto;
import cms.kispg.dto.PaymentApprovalRequestDto;
import cms.kispg.dto.KispgPaymentResultDto;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.client.RestTemplate;

import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.LocalDateTime;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.time.ZoneOffset;
import java.time.temporal.TemporalAdjusters;

import java.util.HashMap;
import java.util.Map;
import java.util.List;
import java.util.Arrays;

import com.fasterxml.jackson.databind.ObjectMapper;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class KispgPaymentServiceImpl implements KispgPaymentService {

    private static final Logger logger = LoggerFactory.getLogger(KispgPaymentServiceImpl.class);
    private static final DateTimeFormatter KISPG_DATE_FORMATTER = DateTimeFormatter.ofPattern("yyyyMMddHHmmss");
    
    private final EnrollRepository enrollRepository;
    private final LessonRepository lessonRepository;
    private final LockerService lockerService;
    private final PaymentRepository paymentRepository;

    @Value("${kispg.mid}")
    private String kispgMid;

    @Value("${kispg.merchantKey}")
    private String merchantKey;

    @Value("${app.api.base-url}")
    private String baseUrl;

    @Value("${app.locker.fee:5000}")
    private int lockerFee;

    @Override
    @Transactional(readOnly = true)
    public KispgInitParamsDto generateInitParams(Long enrollId, User currentUser, String userIp) {
        // 1. Enroll 조회 및 권한 확인
        Enroll enroll = enrollRepository.findById(enrollId)
                .orElseThrow(() -> new ResourceNotFoundException("수강 신청 정보를 찾을 수 없습니다: " + enrollId, ErrorCode.ENROLLMENT_NOT_FOUND));

        if (!enroll.getUser().getUuid().equals(currentUser.getUuid())) {
            throw new BusinessRuleException(ErrorCode.ACCESS_DENIED, "해당 수강 신청에 대한 권한이 없습니다.");
        }

        // 2. 결제 가능 상태 확인
        if (!"UNPAID".equalsIgnoreCase(enroll.getPayStatus())) {
            throw new BusinessRuleException(ErrorCode.NOT_UNPAID_ENROLLMENT_STATUS, "결제 대기 상태가 아닙니다: " + enroll.getPayStatus());
        }

        if (enroll.getExpireDt() == null || enroll.getExpireDt().isBefore(LocalDateTime.now())) {
            throw new BusinessRuleException(ErrorCode.ENROLLMENT_PAYMENT_EXPIRED, "결제 가능 시간이 만료되었습니다.");
        }

        // 3. 정원 확인 (최종 안전장치)
        Lesson lesson = enroll.getLesson();
        long paidCount = enrollRepository.countByLessonLessonIdAndPayStatus(lesson.getLessonId(), "PAID");
        long unpaidActiveCount = enrollRepository.countByLessonLessonIdAndStatusAndPayStatusAndExpireDtAfter(
                lesson.getLessonId(), "APPLIED", "UNPAID", LocalDateTime.now());
        
        long availableSlots = lesson.getCapacity() - paidCount - unpaidActiveCount;
        if (availableSlots <= 0) {
            // This case should ideally be prevented by the frontend based on /payment/details/{enrollId} info
            // and also by earlier logic in enrollment process if payment page access slots are managed.
            // This is a final backend check.
            logger.warn("Payment slot unavailable for enrollId: {} (lesson: {}, capacity: {}, paid: {}, unpaidActive: {})",
                enrollId, lesson.getLessonId(), lesson.getCapacity(), paidCount, unpaidActiveCount);
            throw new BusinessRuleException(ErrorCode.PAYMENT_PAGE_SLOT_UNAVAILABLE, "현재 해당 강습의 결제 페이지 접근 슬롯이 가득 찼습니다.");
        }

        // 4. KISPG 파라미터 생성
        String moid = generateMoid(enrollId);
        int totalAmount = calculateTotalAmount(enroll); // This is VAT inclusive amount

        // VAT Calculation (assuming 10% VAT, KISPG expects amounts as strings)
        // KISPG 명세에 따르면 금액은 모두 정수 문자열로 전달.
        // 부가세 계산: 총액 / 11 (소수점 버림), 공급가액: 총액 - 부가세
        // 정확한 VAT 정책 및 KISPG 필드 요구사항(소수점 처리)에 따라 조정 필요
        int vatAmount = totalAmount / 11; // 부가세 (10% 가정 시)
        int supplyAmount = totalAmount - vatAmount; // 공급가액

        String goodsSplAmt = String.valueOf(supplyAmount);
        String goodsVat = String.valueOf(vatAmount);

        String itemName = lesson.getTitle(); // Assuming lesson.getTitle() is specific enough
        String buyerName = currentUser.getName();
        String buyerTel = currentUser.getPhone(); // Ensure this is populated
        String buyerEmail = currentUser.getEmail(); // Ensure this is populated
        logger.info("For enrollId: {}, KISPG buyerEmail from currentUser.getEmail(): '{}'", enrollId, buyerEmail);
        String returnUrl = baseUrl + "/payment/kispg-return";
        String notifyUrl = baseUrl + "/api/v1/kispg/payment-notification";

        String ediDate = generateEdiDate(); // 전문 생성일시
        String mbsUsrId = currentUser.getUsername(); // 가맹점 고객 ID (사용자 username 사용으로 변경)
        String mbsReserved1 = enrollId.toString(); // 가맹점 예약필드1 (수강신청 ID)


        // 5. 해시 생성 - KISPG 표준에 맞게 mid + ediDate + amt + merchantKey
        String requestHash = generateRequestHash(kispgMid, ediDate, String.valueOf(totalAmount));

        logger.info("KISPG Init Params for enrollId: {}. MID: {}, MOID: {}, Amt: {}, ItemName: '{}', BuyerName: '{}', BuyerTel: '{}', BuyerEmail: '{}', EdiDate: {}, UserIP: '{}', MbsUsrId: '{}', MbsReserved1: '{}', ReturnURL: '{}', NotifyURL: '{}', Hash: '[length:{}], GoodsSplAmt: {}, GoodsVat: {}",
                enrollId, kispgMid, moid, String.valueOf(totalAmount), itemName, buyerName, buyerTel, buyerEmail, ediDate, userIp, mbsUsrId, mbsReserved1, returnUrl, notifyUrl, requestHash.length(), goodsSplAmt, goodsVat);
        logger.info("VAT details - goodsSplAmt: {}, goodsVat: {}", goodsSplAmt, goodsVat);


        return KispgInitParamsDto.builder()
                .mid(kispgMid)
                .moid(moid)
                .amt(String.valueOf(totalAmount))
                .itemName(itemName)
                .buyerName(buyerName)
                .buyerTel(buyerTel)
                .buyerEmail(buyerEmail)
                .returnUrl(returnUrl)
                .notifyUrl(notifyUrl)
                .ediDate(ediDate)
                .requestHash(requestHash)
                .goodsSplAmt(goodsSplAmt)     // 공급가액
                .goodsVat(goodsVat)         // 부가세액
                .userIp(userIp)             // 사용자 IP
                .mbsUsrId(mbsUsrId)         // 가맹점 고객 ID
                .mbsReserved1(mbsReserved1) // 가맹점 예약필드1
                .build();
    }

    @Override
    @Transactional(readOnly = true)
    public KispgInitParamsDto preparePaymentWithoutEnroll(EnrollRequestDto enrollRequest, User currentUser, String userIp) {
        logger.info("Preparing KISPG payment for user: {} without creating enrollment record. LessonId: {}, usesLocker: {}, membershipType: {}", 
                currentUser.getUsername(), enrollRequest.getLessonId(), enrollRequest.getUsesLocker(), enrollRequest.getMembershipType());

        // 1. Lesson 조회 및 검증
        Lesson lesson = lessonRepository.findById(enrollRequest.getLessonId())
                .orElseThrow(() -> new ResourceNotFoundException("강습을 찾을 수 없습니다: " + enrollRequest.getLessonId(), ErrorCode.LESSON_NOT_FOUND));

        // 2. 강습 등록 가능 상태 확인
        LocalDateTime now = LocalDateTime.now();
        if (lesson.getRegistrationStartDateTime() != null && now.isBefore(lesson.getRegistrationStartDateTime())) {
            throw new BusinessRuleException(ErrorCode.REGISTRATION_PERIOD_INVALID, "아직 등록 시작 시간이 되지 않았습니다.");
        }
        if (lesson.getRegistrationEndDateTime() != null && now.isAfter(lesson.getRegistrationEndDateTime())) {
            throw new BusinessRuleException(ErrorCode.REGISTRATION_PERIOD_INVALID, "등록 마감 시간이 지났습니다.");
        }

        // 3. 정원 확인
        long paidCount = enrollRepository.countByLessonLessonIdAndPayStatus(lesson.getLessonId(), "PAID");
        long unpaidActiveCount = enrollRepository.countByLessonLessonIdAndStatusAndPayStatusAndExpireDtAfter(
                lesson.getLessonId(), "APPLIED", "UNPAID", LocalDateTime.now());
        
        long availableSlots = lesson.getCapacity() - paidCount - unpaidActiveCount;
        if (availableSlots <= 0) {
            throw new BusinessRuleException(ErrorCode.LESSON_CAPACITY_EXCEEDED, "정원이 초과되었습니다.");
        }

        // 4. 사용자 중복 등록 확인
        boolean hasExistingEnrollment = enrollRepository.findByUserUuidAndLessonLessonIdAndPayStatusAndExpireDtAfter(
                currentUser.getUuid(), lesson.getLessonId(), "UNPAID", now).isPresent();
        if (hasExistingEnrollment) {
            throw new BusinessRuleException(ErrorCode.DUPLICATE_ENROLLMENT, "이미 해당 강습에 대한 미결제 신청이 있습니다.");
        }

        // 5. 사물함 재고 확인 (요청 시)
        if (enrollRequest.getUsesLocker()) {
            if (currentUser.getGender() == null || currentUser.getGender().trim().isEmpty()) {
                throw new BusinessRuleException(ErrorCode.LOCKER_GENDER_REQUIRED, "사물함 배정을 위해 성별 정보가 필요합니다.");
            }
            // 사물함 재고는 결제 완료 후 실제 할당 시점에 최종 확인
        }

        // 6. 결제 금액 계산
        int lessonPrice = lesson.getPrice();
        int totalAmount = lessonPrice; // 기본 강습료로 시작

        // 멤버십 할인 적용
        if (enrollRequest.getMembershipType() != null && !enrollRequest.getMembershipType().isEmpty()) {
            try {
                MembershipType membership = MembershipType.fromValue(enrollRequest.getMembershipType());
                if (membership != null && membership.getDiscountPercentage() > 0) {
                    int discountPercentage = membership.getDiscountPercentage();
                    int discountedLessonPrice = lessonPrice - (lessonPrice * discountPercentage / 100);
                    totalAmount = discountedLessonPrice; // 할인된 강습료로 업데이트
                    logger.info("Applied discount: {}% for membership type: {}. Original lesson price: {}, Discounted lesson price: {}",
                                discountPercentage, enrollRequest.getMembershipType(), lessonPrice, discountedLessonPrice);
                }
            } catch (IllegalArgumentException e) {
                logger.warn("Invalid membership type '{}' received in enrollRequest. No discount applied. Error: {}", enrollRequest.getMembershipType(), e.getMessage());
                // 유효하지 않은 멤버십 타입이면 할인은 적용되지 않고, totalAmount는 lessonPrice로 유지됩니다.
            }
        }

        if (enrollRequest.getUsesLocker()) {
            totalAmount += lockerFee; // 사물함 비용 추가
        }

        // 7. KISPG 파라미터 생성 (임시 moid 생성)
        String tempMoid = generateTempMoid(lesson.getLessonId(), currentUser.getUuid());
        int vatAmount = totalAmount / 11;
        int supplyAmount = totalAmount - vatAmount;

        String goodsSplAmt = String.valueOf(supplyAmount);
        String goodsVat = String.valueOf(vatAmount);

        String itemName = lesson.getTitle();
        String buyerName = currentUser.getName();
        String buyerTel = currentUser.getPhone();
        String buyerEmail = currentUser.getEmail();
        String returnUrl = baseUrl + "/payment/kispg-return";
        String notifyUrl = baseUrl + "/api/v1/kispg/payment-notification";

        String ediDate = generateEdiDate();
        String mbsUsrId = currentUser.getUsername();
        String mbsReserved1 = "temp_" + lesson.getLessonId(); // 임시 예약 필드

        // 8. 해시 생성
        String requestHash = generateRequestHash(kispgMid, ediDate, String.valueOf(totalAmount));

        logger.info("Generated KISPG init params for user: {}, lesson: {}, tempMoid: {}, amt: {}, usesLocker: {}", 
                currentUser.getUsername(), lesson.getLessonId(), tempMoid, totalAmount, enrollRequest.getUsesLocker());

        return KispgInitParamsDto.builder()
                .mid(kispgMid)
                .moid(tempMoid)
                .amt(String.valueOf(totalAmount))
                .itemName(itemName)
                .buyerName(buyerName)
                .buyerTel(buyerTel)
                .buyerEmail(buyerEmail)
                .returnUrl(returnUrl)
                .notifyUrl(notifyUrl)
                .ediDate(ediDate)
                .requestHash(requestHash)
                .goodsSplAmt(goodsSplAmt)
                .goodsVat(goodsVat)
                .userIp(userIp)
                .mbsUsrId(mbsUsrId)
                .mbsReserved1(mbsReserved1)
                .build();
    }

    private String generateMoid(Long enrollId) {
        long timestamp = System.currentTimeMillis();
        return String.format("enroll_%d_%d", enrollId, timestamp);
    }

    private int calculateTotalAmount(Enroll enroll) {
        int lessonPrice = enroll.getLesson().getPrice();
        int totalAmount = lessonPrice; // 기본 강습료로 시작

        MembershipType membership = enroll.getMembershipType();
        if (membership != null && membership.getDiscountPercentage() > 0) {
            int discountPercentage = membership.getDiscountPercentage();
            int discountedLessonPrice = lessonPrice - (lessonPrice * discountPercentage / 100);
            totalAmount = discountedLessonPrice; // 할인된 강습료로 업데이트
            logger.info("Applied discount: {}% for membership type: {}. Original lesson price: {}, Discounted lesson price: {} for enrollId: {}",
                        discountPercentage, membership.getValue(), lessonPrice, discountedLessonPrice, enroll.getEnrollId());
        }

        // 사물함 선택 시 추가 요금 (현재 usesLocker 상태 기반)
        if (enroll.isUsesLocker()) {
            totalAmount += lockerFee; // 사물함 비용 추가
        }
        return totalAmount;
    }

    private String generateEdiDate() {
        return LocalDateTime.now().format(KISPG_DATE_FORMATTER);
    }

    private String generateRequestHash(String mid, String ediDate, String amt) {
        try {
            // KISPG 규격에 따른 해시 생성: mid + ediDate + amt + merchantKey
            String hashData = mid + ediDate + amt + merchantKey;
            
            logger.debug("Hash generation - mid: {}, ediDate: {}, amt: {}", mid, ediDate, amt);
            
            MessageDigest md = MessageDigest.getInstance("SHA-256");
            byte[] hashBytes = md.digest(hashData.getBytes());
            
            // Hex 문자열로 변환
            StringBuilder sb = new StringBuilder();
            for (byte b : hashBytes) {
                sb.append(String.format("%02x", b));
            }
            
            String hash = sb.toString();
            logger.debug("Generated hash: {}", hash);
            
            return hash;
        } catch (NoSuchAlgorithmException e) {
            logger.error("SHA-256 알고리즘을 찾을 수 없습니다", e);
            throw new RuntimeException("해시 생성 중 오류가 발생했습니다", e);
        }
    }

    private String generateTempMoid(Long lessonId, String userUuid) {
        long timestamp = System.currentTimeMillis();
        return String.format("temp_%d_%s_%d", lessonId, userUuid.substring(0, 8), timestamp);
    }

    @Override
    @Transactional(readOnly = true)
    public EnrollDto verifyAndGetEnrollment(String moid, User currentUser) {
        logger.info("Verifying payment and retrieving enrollment for moid: {}, user: {}", moid, currentUser.getUsername());

        // 1. moid로 Payment 조회
        Payment payment = paymentRepository.findByMoid(moid)
                .orElseThrow(() -> new ResourceNotFoundException("결제 정보를 찾을 수 없습니다: " + moid, ErrorCode.PAYMENT_INFO_NOT_FOUND));

        // 2. 결제 성공 상태 확인
        if (!"PAID".equals(payment.getStatus())) {
            throw new BusinessRuleException(ErrorCode.INVALID_PAYMENT_STATUS_FOR_OPERATION, 
                "결제가 완료되지 않았습니다. 현재 상태: " + payment.getStatus());
        }

        // 3. 연결된 수강신청 조회
        Enroll enroll = payment.getEnroll();
        if (enroll == null) {
            throw new ResourceNotFoundException("결제에 연결된 수강신청 정보를 찾을 수 없습니다.", ErrorCode.ENROLLMENT_NOT_FOUND);
        }

        // 4. 사용자 권한 확인
        if (!enroll.getUser().getUuid().equals(currentUser.getUuid())) {
            throw new BusinessRuleException(ErrorCode.ACCESS_DENIED, "해당 수강신청에 대한 권한이 없습니다.");
        }

        // 5. EnrollDto로 변환하여 반환
        return convertToMypageEnrollDto(enroll);
    }

    @Override
    @Transactional
    public EnrollDto approvePaymentAndCreateEnrollment(PaymentApprovalRequestDto approvalRequest, User currentUser) {
        KispgPaymentResultDto kispgResult = approvalRequest.getKispgPaymentResult();
        String moid = approvalRequest.getMoid(); // System's MOID

        // Log incoming request details
        logger.info("Approving KISPG payment and creating enrollment. MOID: {}, User: {}", 
            moid, currentUser.getUsername());
        if (kispgResult != null) {
            logger.info("KISPG Result Details - TID: {}, KISPG MOID (ordNo): {}, Amt: {}, ResultCd: {}, ResultMsg: {}, PayMethod: {}, EdiDate: {}",
                kispgResult.getTid(), kispgResult.getOrdNo(), kispgResult.getAmt(), kispgResult.getResultCd(),
                kispgResult.getResultMsg(), kispgResult.getPayMethod(), kispgResult.getEdiDate());
        } else {
            // This case should ideally be validated at the controller or by DTO validation
            logger.error("KispgPaymentResultDto is null for MOID: {}", moid);
            throw new BusinessRuleException(ErrorCode.INVALID_INPUT_VALUE, "KISPG 결제 결과가 누락되었습니다.");
        }
        
        // Use KISPG's TID and AMT for the approval call, but our system's MOID for internal logic.
        String kispgTid = kispgResult.getTid();
        String kispgAmt = kispgResult.getAmt();

        // 1. moid 파싱 - 두 가지 형식 지원: temp_ 또는 enroll_
        Long lessonId;
        String userUuidPrefix;
        Enroll existingEnrollForUpdate = null; // enroll_ 형식에서 재사용할 기존 Enroll
        
        if (moid.startsWith("temp_")) {
            // 새로운 temp_ 형식: temp_lessonId_userUuidPrefix_timestamp
            String[] parts = moid.substring("temp_".length()).split("_");
            if (parts.length < 3) {
                throw new BusinessRuleException(ErrorCode.INVALID_INPUT_VALUE, "temp moid 형식이 올바르지 않습니다: " + moid);
            }
            lessonId = Long.parseLong(parts[0]);
            userUuidPrefix = parts[1];
        } else if (moid.startsWith("enroll_")) {
            // 기존 enroll_ 형식: enroll_enrollId_timestamp
            String[] parts = moid.substring("enroll_".length()).split("_");
            if (parts.length < 2) {
                throw new BusinessRuleException(ErrorCode.INVALID_INPUT_VALUE, "enroll moid 형식이 올바르지 않습니다: " + moid);
            }
            Long enrollId = Long.parseLong(parts[0]);
            
            // enroll_id로 기존 Enroll 조회하여 lesson_id 가져오기
            existingEnrollForUpdate = enrollRepository.findById(enrollId)
                    .orElseThrow(() -> new ResourceNotFoundException("수강신청을 찾을 수 없습니다: " + enrollId, ErrorCode.ENROLLMENT_NOT_FOUND));
            
            // 기존 Enroll의 사용자 권한 확인
            if (!existingEnrollForUpdate.getUser().getUuid().equals(currentUser.getUuid())) {
                throw new BusinessRuleException(ErrorCode.ACCESS_DENIED, "해당 수강신청에 대한 권한이 없습니다.");
            }
            
            lessonId = existingEnrollForUpdate.getLesson().getLessonId();
            userUuidPrefix = null;
            
            logger.info("Found existing enrollment: enrollId={}, lessonId={}, user={}", 
                    enrollId, lessonId, currentUser.getUsername());
        } else {
            throw new BusinessRuleException(ErrorCode.INVALID_INPUT_VALUE, "지원되지 않는 moid 형식입니다: " + moid);
        }

        // 2. 사용자 확인 (temp_ 형식인 경우에만)
        if (userUuidPrefix != null && !currentUser.getUuid().startsWith(userUuidPrefix)) {
            throw new BusinessRuleException(ErrorCode.ACCESS_DENIED, "사용자 UUID가 일치하지 않습니다.");
        }

        // 3. Lesson 조회
        Lesson lesson = lessonRepository.findById(lessonId)
                .orElseThrow(() -> new ResourceNotFoundException("강습을 찾을 수 없습니다: " + lessonId, ErrorCode.LESSON_NOT_FOUND));

        // ========== 중복 신청 체크 (결제 승인 전에 먼저 확인) ==========
        if (moid.startsWith("temp_")) {
            // temp_ 형식인 경우에만 중복 체크 (enroll_ 형식은 이미 기존 enrollment 업데이트이므로 제외)
            logger.info("🔍 중복 신청 체크 시작: user={}, lesson={}", currentUser.getUuid(), lessonId);
            
            // 해당 사용자가 해당 강습에 이미 APPLIED, PAID 상태의 신청이 있는지 확인
            List<String> activePayStatuses = Arrays.asList("PAID", "UNPAID"); // UNPAID도 포함 (만료되지 않은 경우)
            List<String> activeStatuses = Arrays.asList("APPLIED");
            
            // 현재 시간보다 만료시간이 미래인 UNPAID 신청 또는 PAID 신청이 있는지 확인
            List<Enroll> existingEnrolls = enrollRepository.findByUserUuidAndLessonLessonId(currentUser.getUuid(), lessonId);
            
            for (Enroll existingEnroll : existingEnrolls) {
                boolean isActivePaid = "PAID".equals(existingEnroll.getPayStatus());
                boolean isActiveUnpaid = "UNPAID".equals(existingEnroll.getPayStatus()) && 
                                       "APPLIED".equals(existingEnroll.getStatus()) &&
                                       existingEnroll.getExpireDt() != null && 
                                       existingEnroll.getExpireDt().isAfter(LocalDateTime.now());
                
                if (isActivePaid || isActiveUnpaid) {
                    logger.warn("❌ 중복 신청 감지: enrollId={}, status={}, payStatus={}, expireDt={}", 
                        existingEnroll.getEnrollId(), existingEnroll.getStatus(), 
                        existingEnroll.getPayStatus(), existingEnroll.getExpireDt());
                    throw new BusinessRuleException(ErrorCode.DUPLICATE_ENROLLMENT, 
                        "이미 해당 강습에 신청 내역이 존재합니다. 기존 신청을 확인해 주세요.");
                }
            }
            
            logger.info("✅ 중복 신청 체크 통과: 신청 가능");
        }

        // ****** ADD KISPG APPROVAL API CALL HERE ******
        boolean kispgApprovalSuccess = callKispgApprovalApi(kispgTid, moid, kispgAmt); // Use system's MOID for approval call if that's what KISPG expects, or kispgResult.getOrdNo() if KISPG's MOID is needed. Check KISPG docs.
                                                                                      // For now, using `moid` (system's MOID) as per the existing callKispgApprovalApi signature.

        if (!kispgApprovalSuccess) {
            logger.error("KISPG payment approval failed for TID: {}, MOID: {}. Enrollment will not be processed.", kispgTid, moid);
            throw new BusinessRuleException(ErrorCode.PAYMENT_GATEWAY_APPROVAL_FAILED, "KISPG 결제 승인에 실패했습니다.");
        }
        logger.info("KISPG payment approval successful for TID: {}, MOID: {}", kispgTid, moid);
        // ****** END KISPG APPROVAL API CALL ******

        // 4. 결제 금액으로부터 사물함 사용 여부 판단
        boolean usesLocker = false;
        int paidAmount = Integer.parseInt(kispgResult.getAmt());
        int lessonPrice = lesson.getPrice();
        if (paidAmount > lessonPrice) {
            usesLocker = true;
        }

        // 5. 사물함 배정 (사용하는 경우)
        boolean lockerAllocated = false;
        if (usesLocker) {
            if (currentUser.getGender() != null && !currentUser.getGender().trim().isEmpty()) {
                try {
                    // 성별 코드를 문자열로 변환 (1: MALE, 2: FEMALE)
                    String genderStr = convertGenderCodeToString(currentUser.getGender());
                    lockerService.incrementUsedQuantity(genderStr);
                    lockerAllocated = true;
                    logger.info("Locker allocated for user: {} (gender code: {} -> {})", 
                        currentUser.getUsername(), currentUser.getGender(), genderStr);
                } catch (Exception e) {
                    logger.error("Failed to allocate locker for user: {}. Error: {}", currentUser.getUsername(), e.getMessage());
                    // 사물함 배정 실패 시에도 수강신청은 생성하되 사물함 없이 진행
                    usesLocker = false;
                }
            } else {
                logger.warn("User {} has no gender info. Cannot allocate locker.", currentUser.getUsername());
                usesLocker = false;
            }
        }

        // 6. 최종 금액 계산
        int finalAmount = lessonPrice;
        if (usesLocker && lockerAllocated) {
            finalAmount += 5000; // 기본 사물함 요금
        }

        // 결제 완료된 수강신청의 만료일은 현재 월의 말일로 설정
        LocalDateTime enrollExpireDate = LocalDate.now()
                .with(TemporalAdjusters.lastDayOfMonth())
                .atTime(23, 59, 59);

        Enroll savedEnroll;
        
        if (moid.startsWith("enroll_")) {
            // 8-A. 기존 Enroll 업데이트 (enroll_ 형식)
            String[] parts = moid.substring("enroll_".length()).split("_");
            Long enrollId = Long.parseLong(parts[0]);
            
            // 결제 상태 확인 - 이미 결제된 경우 처리 방지
            if ("PAID".equals(existingEnrollForUpdate.getPayStatus())) {
                logger.warn("Enrollment {} is already paid. Current status: {}", enrollId, existingEnrollForUpdate.getPayStatus());
                // 이미 결제된 경우에도 기존 정보 반환 (중복 결제 방지)
                return convertToMypageEnrollDto(existingEnrollForUpdate);
            }
            
            // 기존 Enroll 업데이트 시 finalAmount는 KISPG 실제 결제 금액으로 유지하거나,
            // 또는 여기서도 멤버십 기준으로 재계산할지 정책 결정 필요.
            // 현재는 KISPG 실제 결제 금액을 반영하고, 멤버십 유형/할인율은 정보성으로만 업데이트.
            existingEnrollForUpdate.setPayStatus("PAID");
            existingEnrollForUpdate.setExpireDt(enrollExpireDate);
            existingEnrollForUpdate.setUsesLocker(usesLocker); // KISPG 결제액 기반으로 판단된 사물함 사용 여부
            existingEnrollForUpdate.setLockerAllocated(lockerAllocated);
            // existingEnrollForUpdate.setFinalAmount(paidAmount); // KISPG 실제 결제 금액 (Payment.paidAmt와 동일)
            // 만약 Enroll의 finalAmount를 멤버십 할인 기준으로 저장하고 싶다면 여기서 재계산
            // 예:
            // MembershipType currentMembership = existingEnrollForUpdate.getMembershipType(); // 기존 멤버십 유형
            // int calculatedFinalAmount = lessonPrice;
            // if (currentMembership != null && currentMembership.getDiscountPercentage() > 0) {
            //     calculatedFinalAmount -= (lessonPrice * currentMembership.getDiscountPercentage() / 100);
            // }
            // if (usesLocker && lockerAllocated) {
            //     calculatedFinalAmount += lockerFee;
            // }
            // existingEnrollForUpdate.setFinalAmount(calculatedFinalAmount); // 할인 적용된 자체 계산 금액

            existingEnrollForUpdate.setFinalAmount(paidAmount); // 일단 KISPG 결제 금액으로 설정
            
            // 멤버십 정보는 approvalRequest에서 온 것으로 업데이트 (만약 클라이언트가 변경했을 수도 있으므로)
            MembershipType requestedMembership = MembershipType.GENERAL; // 기본값
            int requestedDiscount = 0;
            if (approvalRequest.getMembershipType() != null && !approvalRequest.getMembershipType().isEmpty()) {
                try {
                    requestedMembership = MembershipType.fromValue(approvalRequest.getMembershipType());
                    requestedDiscount = requestedMembership.getDiscountPercentage();
                } catch (IllegalArgumentException e) {
                    logger.warn("Invalid membership type '{}' received in approvalRequest for existing enroll. Using GENERAL. Error: {}", approvalRequest.getMembershipType(), e.getMessage());
                }
            }
            existingEnrollForUpdate.setMembershipType(requestedMembership);
            existingEnrollForUpdate.setDiscountAppliedPercentage(requestedDiscount);

            existingEnrollForUpdate.setUpdatedAt(LocalDateTime.now());
            existingEnrollForUpdate.setUpdatedBy(currentUser.getUuid());
            
            savedEnroll = enrollRepository.save(existingEnrollForUpdate);
            logger.info("Successfully updated existing enrollment: enrollId={}, user={}, lesson={}, usesLocker={}, lockerAllocated={}, membershipType={}, discountApplied={}%", 
                    savedEnroll.getEnrollId(), currentUser.getUsername(), lesson.getLessonId(), usesLocker, lockerAllocated, savedEnroll.getMembershipType(), savedEnroll.getDiscountAppliedPercentage());
            
        } else {
            // 8-B. 새로운 Enroll 생성 (temp_ 형식)
            MembershipType selectedMembership = MembershipType.GENERAL; // 기본값
            int discountPercentage = 0;

            if (approvalRequest.getMembershipType() != null && !approvalRequest.getMembershipType().isEmpty()) {
                try {
                    selectedMembership = MembershipType.fromValue(approvalRequest.getMembershipType());
                    discountPercentage = selectedMembership.getDiscountPercentage();
                } catch (IllegalArgumentException e) {
                    logger.warn("Invalid membership type '{}' received in approvalRequest. Using GENERAL. Error: {}", approvalRequest.getMembershipType(), e.getMessage());
                    // selectedMembership은 GENERAL, discountPercentage는 0으로 유지됨
                }
            }
            
            // Enroll에 기록될 finalAmount는 멤버십 할인을 적용하여 자체 계산
            int calculatedEnrollFinalAmount = lessonPrice;
            if (discountPercentage > 0) {
                calculatedEnrollFinalAmount -= (lessonPrice * discountPercentage / 100);
            }
            if (usesLocker && lockerAllocated) { // KISPG 결제액 기반으로 판단된 사물함 사용 여부 및 실제 배정 성공 여부
                calculatedEnrollFinalAmount += lockerFee;
            }

            savedEnroll = Enroll.builder()
                    .user(currentUser)
                    .lesson(lesson)
                    .status("APPLIED")
                    .payStatus("PAID")
                    .expireDt(enrollExpireDate)
                    .usesLocker(usesLocker) // KISPG 결제액 기반으로 판단된 사물함 사용 여부
                    .lockerAllocated(lockerAllocated) // 실제 사물함 배정 성공 여부
                    .membershipType(selectedMembership) // DTO에서 전달받은 멤버십 적용
                    .finalAmount(calculatedEnrollFinalAmount) // 할인 및 사물함 비용 적용된 자체 계산 금액
                    .discountAppliedPercentage(discountPercentage) // 적용된 할인율
                    .createdBy(currentUser.getUuid())
                    .createdIp("KISPG_APPROVAL_SERVICE")
                    .build();

            savedEnroll = enrollRepository.save(savedEnroll);
            logger.info("Successfully created new enrollment: enrollId={}, user={}, lesson={}, usesLocker={}, lockerAllocated={}, membershipType={}, discountApplied={}%, calculatedFinalAmount={}", 
                    savedEnroll.getEnrollId(), currentUser.getUsername(), lesson.getLessonId(), usesLocker, lockerAllocated, selectedMembership.getValue(), discountPercentage, calculatedEnrollFinalAmount);
        }

        // 9. Payment 엔티티 생성
        LocalDateTime paidAt = LocalDateTime.now(); // Default to now
        if (kispgResult.getEdiDate() != null && !kispgResult.getEdiDate().isEmpty()) {
            try {
                paidAt = LocalDateTime.parse(kispgResult.getEdiDate(), KISPG_DATE_FORMATTER);
            } catch (Exception e) {
                logger.warn("Failed to parse KISPG ediDate '{}'. Defaulting paidAt to current time. Error: {}", kispgResult.getEdiDate(), e.getMessage());
            }
        }

        Payment payment = Payment.builder()
                .enroll(savedEnroll)
                .tid(kispgResult.getTid()) // Use KISPG TID
                .moid(moid) // Use system MOID (could be original enroll_ or temp_)
                .paidAmt(paidAmount)
                .lessonAmount(lessonPrice)
                .lockerAmount(usesLocker && lockerAllocated ? lockerFee : 0) // Use configured lockerFee
                .status("PAID") // If we reach here, it's paid
                .payMethod(kispgResult.getPayMethod() != null ? kispgResult.getPayMethod().toUpperCase() : "UNKNOWN") // Use KISPG payMethod
                .pgResultCode(kispgResult.getResultCd()) // Use KISPG resultCd
                .pgResultMsg(kispgResult.getResultMsg()) // Use KISPG resultMsg
                .paidAt(paidAt) // Use parsed ediDate or current time
                .createdBy(currentUser.getUuid())
                .createdIp("KISPG_APPROVAL_SERVICE") // Default IP as it's not in DTO, or obtain from request if available
                .build();

        paymentRepository.save(payment);
        logger.info("Successfully created payment record for enrollId: {}, System MOID: {}, KISPG TID: {}", 
                savedEnroll.getEnrollId(), moid, kispgResult.getTid());

        return convertToMypageEnrollDto(savedEnroll);
    }

    /**
     * KISPG 승인 API 호출 (공식 문서 준수)
     */
    private boolean callKispgApprovalApi(String tid, String moid, String amt) {
        try {
            logger.info("=== KISPG 승인 API 호출 시작 ===");
            logger.info("📋 입력 파라미터:");
            logger.info("  - TID: {}", tid);
            logger.info("  - MOID: {}", moid);
            logger.info("  - AMT: {}", amt);
            
            // 1. ediDate 생성 (현재 시각, KISPG는 현재 시각 -10분까지 유효)
            String ediDate = LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyyMMddHHmmss"));
            
            // 2. KISPG 공식 문서에 따른 해시 생성: mid + ediDate + goodsAmt + merchantKey
            String hashData = kispgMid + ediDate + amt + merchantKey;
            String encData = generateHash(hashData);

            logger.info("📋 KISPG 승인 요청 구성:");
            logger.info("  - MID: {}", kispgMid);
            logger.info("  - TID: {}", tid);
            logger.info("  - goodsAmt: {}", amt);
            logger.info("  - ediDate: {} (현재시각)", ediDate);
            logger.info("  - HashData: {} (길이: {})", hashData, hashData.length());
            logger.info("  - encData: {} (길이: {})", encData, encData.length());

            // 3. KISPG 승인 요청 파라미터 생성 (공식 문서 순서대로)
            Map<String, String> requestParams = new HashMap<>();
            requestParams.put("mid", kispgMid);           // 가맹점ID (필수)
            requestParams.put("tid", tid);                // 거래번호 (필수) - KISPG에서 제공한 실제 TID
            requestParams.put("goodsAmt", amt);           // 결제금액 (필수)
            requestParams.put("ediDate", ediDate);        // 전문요청일시 (필수)
            requestParams.put("encData", encData);        // 해시값 (필수)
            requestParams.put("charset", "UTF-8");        // 인코딩방식

            // 4. JSON 요청 본문 생성
            ObjectMapper objectMapper = new ObjectMapper();
            String jsonBody = objectMapper.writeValueAsString(requestParams);
            
            logger.info("📤 KISPG 승인 API 요청:");
            logger.info("  - URL: https://api.kispg.co.kr/v2/payment");
            logger.info("  - Method: POST");
            logger.info("  - Content-Type: application/json");
            logger.info("  - Body: {}", jsonBody);

            // 5. HTTP 요청 설정
            RestTemplate restTemplate = new RestTemplate();
            HttpHeaders headers = new HttpHeaders();
            headers.setContentType(MediaType.APPLICATION_JSON);
            headers.set("User-Agent", "ARPINA-CMS/1.0");
            
            HttpEntity<String> entity = new HttpEntity<>(jsonBody, headers);
            
            // 6. KISPG 승인 API 호출 (운영 URL 고정)
            String apiUrl = "https://api.kispg.co.kr/v2/payment";
            
            long startTime = System.currentTimeMillis();
            ResponseEntity<String> response = restTemplate.postForEntity(apiUrl, entity, String.class);
            long responseTime = System.currentTimeMillis() - startTime;
            
            logger.info("📥 KISPG 승인 API 응답 ({}ms):", responseTime);
            logger.info("  - Status Code: {}", response.getStatusCode());
            logger.info("  - Response Body: {}", response.getBody());
            
            // 7. 응답 처리
            if (response.getStatusCode() == HttpStatus.OK) {
                String responseBody = response.getBody();
                if (responseBody != null && !responseBody.trim().isEmpty()) {
                    // JSON 응답을 Map으로 파싱
                    Map<String, Object> responseMap = objectMapper.readValue(responseBody, Map.class);
                    String resultCd = (String) responseMap.get("resultCd");
                    String resultMsg = (String) responseMap.get("resultMsg");
                    
                    logger.info("📋 KISPG 승인 결과 파싱:");
                    logger.info("  - resultCd: {}", resultCd);
                    logger.info("  - resultMsg: {}", resultMsg);
                    
                    // 모든 응답 필드 로깅
                    responseMap.forEach((key, value) -> 
                        logger.info("  - {}: {}", key, value)
                    );
                    
                    // 성공 코드 확인 (KISPG 문서에 따라 "0000"이 성공, "3001"도 성공으로 간주)
                    if ("0000".equals(resultCd) || "3001".equals(resultCd)) {
                        logger.info("✅ KISPG 승인 성공! (resultCd: {})", resultCd);
                        return true;
                    } else {
                        logger.error("❌ KISPG 승인 실패: [{}] {}", resultCd, resultMsg);
                        return false;
                    }
                } else {
                    logger.error("❌ KISPG API 응답 본문이 비어있습니다.");
                    return false;
                }
            } else {
                logger.error("❌ KISPG 승인 API HTTP 오류 - Status: {}", response.getStatusCode());
                return false;
            }
            
        } catch (Exception e) {
            logger.error("❌ KISPG 승인 API 호출 중 예외 발생:", e);
            logger.error("  - 예외 타입: {}", e.getClass().getSimpleName());
            logger.error("  - 예외 메시지: {}", e.getMessage());
            return false;
        }
    }

    private String generateHash(String data) {
        try {
            MessageDigest md = MessageDigest.getInstance("SHA-256");
            byte[] hash = md.digest(data.getBytes("UTF-8"));
            return bytesToHex(hash);
        } catch (Exception e) {
            logger.error("Failed to generate hash: {}", e.getMessage(), e);
            return "";
        }
    }

    private String bytesToHex(byte[] bytes) {
        StringBuilder result = new StringBuilder();
        for (byte b : bytes) {
            result.append(String.format("%02x", b));
        }
        return result.toString();
    }

    private EnrollDto convertToMypageEnrollDto(Enroll enroll) {
        if (enroll == null) return null;

        Lesson lesson = enroll.getLesson();
        
        // LessonDetails 생성
        EnrollDto.LessonDetails lessonDetails = EnrollDto.LessonDetails.builder()
                .lessonId(lesson.getLessonId())
                .title(lesson.getTitle())
                .name(lesson.getTitle()) // title과 동일하게 설정
                .startDate(lesson.getStartDate().toString())
                .endDate(lesson.getEndDate().toString())
                .capacity(lesson.getCapacity())
                .price(java.math.BigDecimal.valueOf(lesson.getPrice()))
                .instructor(null) // Lesson 엔티티에 해당 필드가 없으면 null로 설정
                .location(null) // Lesson 엔티티에 해당 필드가 없으면 null로 설정
                .build();

        return EnrollDto.builder()
                .enrollId(enroll.getEnrollId())
                .lesson(lessonDetails)
                .status(enroll.getPayStatus()) // pay_status를 status로 사용
                .applicationDate(enroll.getCreatedAt() != null ? enroll.getCreatedAt().atOffset(ZoneOffset.UTC) : null)
                .paymentExpireDt(enroll.getExpireDt() != null ? enroll.getExpireDt().atOffset(ZoneOffset.UTC) : null)
                .usesLocker(enroll.isUsesLocker())
                .membershipType(enroll.getMembershipType() != null ? enroll.getMembershipType().name() : null)
                .cancelStatus(enroll.getCancelStatus() != null ? enroll.getCancelStatus().name() : "NONE")
                .cancelReason(enroll.getCancelReason())
                .canAttemptPayment(false) // 이미 결제 완료된 상태이므로 false
                .paymentPageUrl(null) // 결제 완료된 상태이므로 null
                .build();
    }

    /**
     * 성별 코드를 DB 테이블에서 사용하는 문자열로 변환
     * @param genderCode 성별 코드 (1: 남성, 2: 여성)
     * @return MALE 또는 FEMALE
     */
    private String convertGenderCodeToString(String genderCode) {
        if (genderCode == null || genderCode.trim().isEmpty()) {
            throw new IllegalArgumentException("Gender code cannot be null or empty");
        }
        
        String trimmedCode = genderCode.trim();
        switch (trimmedCode) {
            case "1":
                return "MALE";
            case "2":
                return "FEMALE";
            case "MALE":
                return "MALE"; // 이미 문자열인 경우
            case "FEMALE":
                return "FEMALE"; // 이미 문자열인 경우
            default:
                logger.warn("Unknown gender code: {}. Defaulting to MALE", genderCode);
                return "MALE"; // 기본값으로 MALE 사용
        }
    }
} 