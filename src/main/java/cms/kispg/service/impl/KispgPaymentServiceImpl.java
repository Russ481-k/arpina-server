package cms.kispg.service.impl;

import cms.common.exception.BusinessRuleException;
import cms.common.exception.ErrorCode;
import cms.common.exception.ResourceNotFoundException;
import cms.enroll.domain.Enroll;
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
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.LocalDateTime;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.time.ZoneOffset;
import java.time.temporal.TemporalAdjusters;

import java.util.HashMap;
import java.util.Map;

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
        logger.info("Preparing KISPG payment for user: {} without creating enrollment record. LessonId: {}, usesLocker: {}", 
                currentUser.getUsername(), enrollRequest.getLessonId(), enrollRequest.getUsesLocker());

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
        int totalAmount = lesson.getPrice();
        if (enrollRequest.getUsesLocker()) {
            totalAmount += lockerFee;
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
        // 사물함 선택 시 추가 요금 (현재 usesLocker 상태 기반)
        int totalAmount = lessonPrice;
        if (enroll.isUsesLocker()) {
            totalAmount += lockerFee;
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
    public EnrollDto approvePaymentAndCreateEnrollment(String tid, String moid, String amt, User currentUser) {
        logger.info("Approving KISPG payment and creating enrollment for tid: {}, moid: {}, amt: {}, user: {}", 
            tid, moid, amt, currentUser.getUsername());

        // 1. KISPG 승인 API 호출
        boolean approvalSuccess = callKispgApprovalApi(tid, moid, amt);
        if (!approvalSuccess) {
            throw new BusinessRuleException(ErrorCode.PAYMENT_REFUND_FAILED, "KISPG 승인 API 호출에 실패했습니다.");
        }

        // 2. temp moid 파싱
        if (!moid.startsWith("temp_")) {
            throw new BusinessRuleException(ErrorCode.INVALID_INPUT_VALUE, "잘못된 temp moid 형식입니다: " + moid);
        }

        String[] parts = moid.substring("temp_".length()).split("_");
        if (parts.length < 3) {
            throw new BusinessRuleException(ErrorCode.INVALID_INPUT_VALUE, "temp moid 형식이 올바르지 않습니다: " + moid);
        }

        Long lessonId = Long.parseLong(parts[0]);
        String userUuidPrefix = parts[1];

        // 3. 사용자 확인
        if (!currentUser.getUuid().startsWith(userUuidPrefix)) {
            throw new BusinessRuleException(ErrorCode.ACCESS_DENIED, "사용자 UUID가 일치하지 않습니다.");
        }

        // 4. Lesson 조회
        Lesson lesson = lessonRepository.findById(lessonId)
                .orElseThrow(() -> new ResourceNotFoundException("강습을 찾을 수 없습니다: " + lessonId, ErrorCode.LESSON_NOT_FOUND));

        // 5. 결제 금액으로부터 사물함 사용 여부 판단
        boolean usesLocker = false;
        int paidAmount = Integer.parseInt(amt);
        int lessonPrice = lesson.getPrice();
        if (paidAmount > lessonPrice) {
            usesLocker = true;
        }

        // 6. 사물함 배정 (사용하는 경우)
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

        // 7. Enroll 엔티티 생성
        int finalAmount = lessonPrice;
        if (usesLocker && lockerAllocated) {
            finalAmount += 5000; // 기본 사물함 요금
        }

        // 결제 완료된 수강신청의 만료일은 현재 월의 말일로 설정
        LocalDateTime enrollExpireDate = LocalDate.now()
                .with(TemporalAdjusters.lastDayOfMonth())
                .atTime(23, 59, 59);

        Enroll enroll = Enroll.builder()
                .user(currentUser)
                .lesson(lesson)
                .status("APPLIED")
                .payStatus("PAID")
                .expireDt(enrollExpireDate)
                .usesLocker(usesLocker)
                .lockerAllocated(lockerAllocated)
                .membershipType(cms.enroll.domain.MembershipType.GENERAL)
                .finalAmount(finalAmount)
                .discountAppliedPercentage(0)
                .createdBy(currentUser.getUuid())
                .createdIp("KISPG_APPROVAL")
                .build();

        Enroll savedEnroll = enrollRepository.save(enroll);
        logger.info("Successfully created enrollment: enrollId={}, user={}, lesson={}, usesLocker={}, lockerAllocated={}", 
                savedEnroll.getEnrollId(), currentUser.getUsername(), lesson.getLessonId(), usesLocker, lockerAllocated);

        // 8. Payment 엔티티 생성
        Payment payment = Payment.builder()
                .enroll(savedEnroll)
                .tid(tid)
                .moid(moid)
                .paidAmt(paidAmount)
                .lessonAmount(lessonPrice)
                .lockerAmount(usesLocker && lockerAllocated ? 5000 : 0)
                .status("PAID")
                .payMethod("CARD")
                .pgResultCode("0000")
                .pgResultMsg("SUCCESS")
                .paidAt(LocalDateTime.now())
                .createdBy(currentUser.getUuid())
                .createdIp("KISPG_APPROVAL")
                .build();

        paymentRepository.save(payment);
        logger.info("Successfully created payment record for enrollId: {}, tid: {}, moid: {}", 
                savedEnroll.getEnrollId(), tid, moid);

        return convertToMypageEnrollDto(savedEnroll);
    }

    /**
     * KISPG 승인 API 호출
     */
    private boolean callKispgApprovalApi(String tid, String moid, String amt) {
        try {
            // 샘플 코드 기반으로 KISPG 승인 API 호출 로직 구현
            String mid = "kistest00m"; // 설정에서 가져와야 함
            String merchantKey = "test-key"; // 설정에서 가져와야 함
            String ediDate = LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyyMMddHHmmss"));
            String encData = generateHash(mid + ediDate + amt + merchantKey);

            // JSON 요청 생성
            Map<String, String> requestMap = new HashMap<>();
            requestMap.put("mid", mid);
            requestMap.put("tid", tid);
            requestMap.put("goodsAmt", amt);
            requestMap.put("ediDate", ediDate);
            requestMap.put("encData", encData);
            requestMap.put("charset", "UTF-8");

            // KISPG API 호출 (실제 구현에서는 RestTemplate 등 사용)
            logger.info("KISPG approval API request: {}", requestMap.toString());
            
            // TODO: 실제 KISPG API 호출 구현
            // 현재는 테스트를 위해 true 반환
            return true;
            
        } catch (Exception e) {
            logger.error("Failed to call KISPG approval API: {}", e.getMessage(), e);
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