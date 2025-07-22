package cms.kispg.service.impl;

import cms.common.exception.BusinessRuleException;
import cms.common.exception.ErrorCode;
import cms.common.exception.ResourceNotFoundException;
import cms.enroll.domain.Enroll;
import cms.enroll.domain.MembershipType;
import cms.enroll.repository.EnrollRepository;
import cms.kispg.dto.KispgInitParamsDto;
import cms.kispg.dto.PaymentApprovalRequestDto;
import cms.kispg.dto.KispgPaymentResultDto;
import cms.kispg.service.KispgPaymentService;
import cms.locker.service.LockerService;
import cms.mypage.dto.EnrollDto;
import cms.payment.domain.Payment;
import cms.payment.domain.PaymentStatus;
import cms.payment.repository.PaymentRepository;
import cms.swimming.domain.Lesson;
import cms.swimming.dto.EnrollRequestDto;
import cms.swimming.repository.LessonRepository;
import cms.user.domain.User;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.client.RestTemplate;
import org.springframework.web.client.HttpClientErrorException;
import cms.kispg.dto.KispgCancelRequestDto;
import cms.kispg.dto.KispgCancelResponseDto;
import cms.admin.payment.dto.KispgQueryRequestDto;

import javax.annotation.PostConstruct;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.Map;
import java.util.List;
import java.util.HashMap;
import org.apache.commons.codec.binary.Hex;
import java.time.YearMonth;

@Slf4j
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class KispgPaymentServiceImpl implements KispgPaymentService {

    private static final DateTimeFormatter KISPG_DATE_FORMATTER = DateTimeFormatter.ofPattern("yyyyMMddHHmmss");

    private final EnrollRepository enrollRepository;
    private final LessonRepository lessonRepository;
    private final LockerService lockerService;
    private final PaymentRepository paymentRepository;
    private final ObjectMapper objectMapper;

    @Value("${kispg.url}")
    private String kispgUrl;

    @Value("${kispg.mid}")
    private String kispgMid;

    @Value("${kispg.merchantKey}")
    private String merchantKey;

    @Value("${app.api.base-url}")
    private String baseUrl;

    @Value("${app.locker.fee:5000}")
    private int lockerFee;

    @PostConstruct
    public void init() {
        log.info("KISPG Service Initialized. API URL: [{}], MID: [{}]", kispgUrl, kispgMid);
    }

    /**
     * KISPG API 엔드포인트 URL을 생성합니다.
     * 환경변수 KISPG_URL에는 기본 URL(예: https://api.kispg.co.kr)만 설정되어야 합니다.
     * 
     * @param endpoint payment, cancel, order 등
     */
    private String getKispgApiUrl(String endpoint) {
        if (kispgUrl == null) {
            throw new IllegalStateException("KISPG URL이 설정되지 않았습니다.");
        }

        String baseUrl = kispgUrl;

        // 혹시 기존 설정에 /v2/xxx가 포함되어 있다면 제거 (하위 호환성)
        if (baseUrl.contains("/v2/")) {
            baseUrl = baseUrl.substring(0, baseUrl.indexOf("/v2/"));
        }

        // 마지막 슬래시 제거
        if (baseUrl.endsWith("/")) {
            baseUrl = baseUrl.substring(0, baseUrl.length() - 1);
        }

        return baseUrl + "/v2/" + endpoint;
    }

    @Override
    @Transactional(readOnly = true)
    public KispgInitParamsDto generateInitParams(Long enrollId, User currentUser, String userIp) {
        Enroll enroll = enrollRepository.findById(enrollId)
                .orElseThrow(() -> new ResourceNotFoundException("수강 신청 정보를 찾을 수 없습니다: " + enrollId,
                        ErrorCode.ENROLLMENT_NOT_FOUND));

        if (!enroll.getUser().getUuid().equals(currentUser.getUuid())) {
            throw new BusinessRuleException(ErrorCode.ACCESS_DENIED, "해당 수강 신청에 대한 권한이 없습니다.");
        }

        if (!"UNPAID".equalsIgnoreCase(enroll.getPayStatus())) {
            throw new BusinessRuleException(ErrorCode.NOT_UNPAID_ENROLLMENT_STATUS,
                    "결제 대기 상태가 아닙니다: " + enroll.getPayStatus());
        }

        if (enroll.getExpireDt() == null || enroll.getExpireDt().isBefore(LocalDateTime.now())) {
            throw new BusinessRuleException(ErrorCode.ENROLLMENT_PAYMENT_EXPIRED, "결제 가능 시간이 만료되었습니다.");
        }

        Lesson lesson = enroll.getLesson();

        // [추가] 월별 중복 신청 방지 로직
        long monthlyEnrollments = enrollRepository.countUserEnrollmentsInMonth(currentUser.getUuid(),
                lesson.getStartDate());
        if (monthlyEnrollments > 0) {
            throw new BusinessRuleException(ErrorCode.MONTHLY_ENROLLMENT_LIMIT_EXCEEDED,
                    "이미 해당 월에 신청한 강습이 있습니다. 한 달에 한 개의 강습만 신청 가능합니다.");
        }

        long paidCount = enrollRepository.countByLessonLessonIdAndPayStatus(lesson.getLessonId(), "PAID");
        long unpaidActiveCount = enrollRepository.countByLessonLessonIdAndStatusAndPayStatusAndExpireDtAfter(
                lesson.getLessonId(), "APPLIED", "UNPAID", LocalDateTime.now());

        long availableSlots = lesson.getCapacity() - paidCount - unpaidActiveCount;
        if (availableSlots <= 0) {
            log.warn(
                    "Payment slot unavailable for enrollId: {} (lesson: {}, capacity: {}, paid: {}, unpaidActive: {})",
                    enrollId, lesson.getLessonId(), lesson.getCapacity(), paidCount, unpaidActiveCount);
            throw new BusinessRuleException(ErrorCode.PAYMENT_PAGE_SLOT_UNAVAILABLE,
                    "현재 해당 강습의 결제 페이지 접근 슬롯이 가득 찼습니다.");
        }

        String moid = generateMoid(enrollId);
        int totalAmount = calculateTotalAmount(enroll);

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
        String mbsReserved1 = enrollId.toString();

        String requestHash = generateRequestHash(kispgMid, ediDate, String.valueOf(totalAmount));

        log.info(
                "KISPG Init Params for enrollId: {}. MID: {}, MOID: {}, Amt: {}, ItemName: '{}', BuyerName: '{}', BuyerTel: '{}', BuyerEmail: '{}', EdiDate: {}, UserIP: '{}', MbsUsrId: '{}', MbsReserved1: '{}', ReturnURL: '{}', NotifyURL: '{}', Hash: '[length:{}], GoodsSplAmt: {}, GoodsVat: {}",
                enrollId, kispgMid, moid, String.valueOf(totalAmount), itemName, buyerName, buyerTel, buyerEmail,
                ediDate, userIp, mbsUsrId, mbsReserved1, returnUrl, notifyUrl, requestHash.length(), goodsSplAmt,
                goodsVat);

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
                .goodsSplAmt(goodsSplAmt)
                .goodsVat(goodsVat)
                .userIp(userIp)
                .mbsUsrId(mbsUsrId)
                .mbsReserved1(mbsReserved1)
                .build();
    }

    @Override
    @Transactional(readOnly = true)
    public KispgInitParamsDto preparePaymentWithoutEnroll(EnrollRequestDto enrollRequest, User currentUser,
            String userIp) {
        log.info(
                "Preparing KISPG payment for user: {} without creating enrollment record. LessonId: {}, usesLocker: {}, membershipType: {}",
                currentUser.getUsername(), enrollRequest.getLessonId(), enrollRequest.getUsesLocker(),
                enrollRequest.getMembershipType());

        Lesson lesson = lessonRepository.findById(enrollRequest.getLessonId())
                .orElseThrow(() -> new ResourceNotFoundException("강습을 찾을 수 없습니다: " + enrollRequest.getLessonId(),
                        ErrorCode.LESSON_NOT_FOUND));

        LocalDateTime now = LocalDateTime.now();
        if (lesson.getRegistrationStartDateTime() != null && now.isBefore(lesson.getRegistrationStartDateTime())) {
            throw new BusinessRuleException(ErrorCode.REGISTRATION_PERIOD_INVALID, "아직 등록 시작 시간이 되지 않았습니다.");
        }
        if (lesson.getRegistrationEndDateTime() != null && now.isAfter(lesson.getRegistrationEndDateTime())) {
            throw new BusinessRuleException(ErrorCode.REGISTRATION_PERIOD_INVALID, "등록 마감 시간이 지났습니다.");
        }

        long paidCount = enrollRepository.countByLessonLessonIdAndPayStatus(lesson.getLessonId(), "PAID");
        long unpaidActiveCount = enrollRepository.countByLessonLessonIdAndStatusAndPayStatusAndExpireDtAfter(
                lesson.getLessonId(), "APPLIED", "UNPAID", LocalDateTime.now());

        long availableSlots = lesson.getCapacity() - paidCount - unpaidActiveCount;
        if (availableSlots <= 0) {
            throw new BusinessRuleException(ErrorCode.LESSON_CAPACITY_EXCEEDED, "정원이 초과되었습니다.");
        }

        boolean hasExistingEnrollment = enrollRepository.findByUserUuidAndLessonLessonIdAndPayStatusAndExpireDtAfter(
                currentUser.getUuid(), lesson.getLessonId(), "UNPAID", now).isPresent();
        if (hasExistingEnrollment) {
            throw new BusinessRuleException(ErrorCode.DUPLICATE_ENROLLMENT, "이미 해당 강습에 대한 미결제 신청이 있습니다.");
        }

        if (enrollRequest.getUsesLocker()) {
            if (currentUser.getGender() == null || currentUser.getGender().trim().isEmpty()) {
                throw new BusinessRuleException(ErrorCode.LOCKER_GENDER_REQUIRED, "사물함 배정을 위해 성별 정보가 필요합니다.");
            }
        }

        int lessonPrice = lesson.getPrice();
        int totalAmount = lessonPrice;

        if (enrollRequest.getMembershipType() != null && !enrollRequest.getMembershipType().isEmpty()) {
            try {
                MembershipType membership = MembershipType.fromValue(enrollRequest.getMembershipType());
                if (membership != null && membership.getDiscountPercentage() > 0) {
                    int discountPercentage = membership.getDiscountPercentage();
                    int discountedLessonPrice = lessonPrice - (lessonPrice * discountPercentage / 100);
                    totalAmount = discountedLessonPrice;
                    log.info(
                            "Applied discount: {}% for membership type: {}. Original lesson price: {}, Discounted lesson price: {}",
                            discountPercentage, enrollRequest.getMembershipType(), lessonPrice, discountedLessonPrice);
                }
            } catch (IllegalArgumentException e) {
                log.warn("Invalid membership type '{}' received in enrollRequest. No discount applied. Error: {}",
                        enrollRequest.getMembershipType(), e.getMessage());
            }
        }

        if (enrollRequest.getUsesLocker()) {
            totalAmount += lockerFee;
        }

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

        // mbsReserved1 필드에 사용자 선택 정보(JSON) 저장
        Map<String, Object> reservedData = new HashMap<>();
        reservedData.put("usesLocker", enrollRequest.getUsesLocker());
        reservedData.put("membershipType", enrollRequest.getMembershipType());
        String mbsReserved1;
        try {
            mbsReserved1 = objectMapper.writeValueAsString(reservedData);
        } catch (JsonProcessingException e) {
            log.error("Failed to serialize reserved data for KISPG", e);
            throw new RuntimeException("결제 정보 생성 중 오류가 발생했습니다.", e);
        }

        String requestHash = generateRequestHash(kispgMid, ediDate, String.valueOf(totalAmount));

        log.info(
                "Generated KISPG init params for user: {}, lesson: {}, tempMoid: {}, amt: {}, usesLocker: {}, mbsReserved1: {}",
                currentUser.getUsername(), lesson.getLessonId(), tempMoid, totalAmount, enrollRequest.getUsesLocker(),
                mbsReserved1);

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
        int totalAmount = lessonPrice;

        MembershipType membership = enroll.getMembershipType();
        if (membership != null && membership.getDiscountPercentage() > 0) {
            int discountPercentage = membership.getDiscountPercentage();
            int discountedLessonPrice = lessonPrice - (lessonPrice * discountPercentage / 100);
            totalAmount = discountedLessonPrice;
            log.info(
                    "Applied discount: {}% for membership type: {}. Original lesson price: {}, Discounted lesson price: {} for enrollId: {}",
                    discountPercentage, membership.getValue(), lessonPrice, discountedLessonPrice,
                    enroll.getEnrollId());
        }

        if (enroll.isUsesLocker()) {
            totalAmount += lockerFee;
        }
        return totalAmount;
    }

    private String generateEdiDate() {
        return LocalDateTime.now().format(KISPG_DATE_FORMATTER);
    }

    private String generateRequestHash(String mid, String ediDate, String amt) {
        if (merchantKey == null || merchantKey.trim().isEmpty()) {
            log.error("KISPG Merchant Key is not configured.");
            throw new IllegalStateException("KISPG Merchant Key가 설정되어 있지 않습니다.");
        }
        String rawHash = mid + ediDate + amt + merchantKey;
        return generateHash(rawHash);
    }

    private String generateApprovalHash(String mid, String ediDate, String amt) {
        if (merchantKey == null || merchantKey.trim().isEmpty()) {
            log.error("KISPG Merchant Key is not configured.");
            throw new IllegalStateException("KISPG Merchant Key가 설정되어 있지 않습니다.");
        }
        String rawHash = mid + ediDate + amt + merchantKey;
        return generateHash(rawHash);
    }

    private String generateTempMoid(Long lessonId, String userUuid) {
        long timestamp = System.currentTimeMillis();
        return String.format("temp_%d_%s_%d", lessonId, userUuid.length() > 8 ? userUuid.substring(0, 8) : userUuid,
                timestamp);
    }

    @Override
    @Transactional(readOnly = true)
    public EnrollDto verifyAndGetEnrollment(String moid, User currentUser) {
        log.info("Verifying payment and retrieving enrollment for moid: {}, user: {}", moid,
                currentUser.getUsername());

        Payment payment = paymentRepository.findByMoid(moid)
                .orElseThrow(() -> new ResourceNotFoundException("결제 정보를 찾을 수 없습니다: " + moid,
                        ErrorCode.PAYMENT_INFO_NOT_FOUND));

        if (payment.getStatus() != PaymentStatus.PAID) {
            throw new BusinessRuleException(ErrorCode.INVALID_PAYMENT_STATUS_FOR_OPERATION,
                    "결제가 완료되지 않았습니다. 현재 상태: " + payment.getStatus().getDescription());
        }

        Enroll enroll = payment.getEnroll();
        if (enroll == null) {
            throw new ResourceNotFoundException("결제에 연결된 수강신청 정보를 찾을 수 없습니다.", ErrorCode.ENROLLMENT_NOT_FOUND);
        }

        if (!enroll.getUser().getUuid().equals(currentUser.getUuid())) {
            throw new BusinessRuleException(ErrorCode.ACCESS_DENIED, "해당 수강신청에 대한 권한이 없습니다.");
        }
        return convertToMypageEnrollDto(enroll);
    }

    @Override
    @Transactional
    public EnrollDto approvePaymentAndCreateEnrollment(PaymentApprovalRequestDto approvalRequest, User currentUser,
            String userIp) {
        log.info("Starting payment approval and enrollment creation for MOID: {}", approvalRequest.getMoid());

        // 1. KISPG에 결제 승인 요청
        boolean isApprovedAtPg = callKispgApprovalApi(
                approvalRequest.getTid(),
                approvalRequest.getMoid(),
                approvalRequest.getAmt(),
                approvalRequest.getKispgPaymentResult().getEdiDate());

        if (!isApprovedAtPg) {
            log.error("KISPG 결제 승인 실패. TID: {}, MOID: {}", approvalRequest.getTid(), approvalRequest.getMoid());
            throw new BusinessRuleException(ErrorCode.PAYMENT_FAILED, "KISPG 결제 승인에 실패했습니다.");
        }
        log.info("KISPG 결제 승인 성공. MOID: {}", approvalRequest.getMoid());

        // 2. 예약 데이터 파싱 및 정보 추출
        boolean usesLocker = false;
        String membershipTypeStr = "general"; // 기본값
        try {
            String reservedDataJson = approvalRequest.getKispgPaymentResult().getMbsReserved();
            if (reservedDataJson != null && !reservedDataJson.isEmpty()) {
                Map<String, Object> reservedData = objectMapper.readValue(reservedDataJson,
                        new TypeReference<Map<String, Object>>() {
                        });
                usesLocker = (Boolean) reservedData.getOrDefault("usesLocker", false);
                membershipTypeStr = (String) reservedData.getOrDefault("membershipType", "general");
            }
        } catch (Exception e) {
            log.error("Failed to parse mbsReserved data. MOID: {}, JSON: {}. Error: {}",
                    approvalRequest.getMoid(), approvalRequest.getKispgPaymentResult().getMbsReserved(),
                    e.getMessage());
            // 파싱 실패 시 기본값으로 계속 진행하거나, 비즈니스 규칙에 따라 예외 처리 가능
        }

        // 3. 임시 MOID에서 강습 ID 추출
        Long lessonId = parseLessonIdFromTempMoid(approvalRequest.getMoid());
        Lesson lesson = lessonRepository.findById(lessonId)
                .orElseThrow(
                        () -> new ResourceNotFoundException("강습을 찾을 수 없습니다: " + lessonId, ErrorCode.LESSON_NOT_FOUND));

        // 4. 할인율 결정
        MembershipType selectedMembership = MembershipType.fromValue(membershipTypeStr);
        int discountPercentage = (selectedMembership != null) ? selectedMembership.getDiscountPercentage() : 0;

        // ... (이하 로직은 추출된 usesLocker와 selectedMembership을 사용하여 Enroll 객체 생성)
        boolean lockerAllocated = false;
        if (usesLocker) {
            // ... (기존의 라커 할당 로직)
        }

        // 5. Enroll 객체 생성 또는 업데이트
        Enroll savedEnroll = createOrUpdateEnrollment(currentUser, lesson, usesLocker, lockerAllocated,
                selectedMembership, discountPercentage);

        // 6. Payment 객체 생성
        createAndSavePayment(approvalRequest, savedEnroll, usesLocker && lockerAllocated, currentUser, userIp);

        log.info("Successfully created/updated enrollment and payment record for MOID: {}", approvalRequest.getMoid());

        return convertToMypageEnrollDto(savedEnroll);
    }

    private Long parseLessonIdFromTempMoid(String tempMoid) {
        if (tempMoid == null || !tempMoid.startsWith("temp_")) {
            throw new IllegalArgumentException("유효하지 않은 임시 MOID 형식입니다: " + tempMoid);
        }
        try {
            String[] parts = tempMoid.split("_");
            return Long.parseLong(parts[1]);
        } catch (Exception e) {
            log.error("임시 MOID에서 강습 ID를 파싱할 수 없습니다: {}", tempMoid, e);
            throw new NumberFormatException("임시 MOID에서 강습 ID를 파싱할 수 없습니다: " + tempMoid);
        }
    }

    private Enroll createOrUpdateEnrollment(User user, Lesson lesson, boolean usesLocker, boolean lockerAllocated,
            MembershipType membershipType, int discountPercentage) {

        // 재수강 여부 판단
        boolean isRenewal = isRenewal(user, lesson);

        LocalDateTime expireDt = lesson.getEndDate().atTime(23, 59, 59);

        Enroll newEnroll = Enroll.builder()
                .user(user)
                .lesson(lesson)
                .status("APPLIED")
                .payStatus("PAID")
                .expireDt(expireDt)
                .usesLocker(usesLocker)
                .lockerAllocated(lockerAllocated)
                .membershipType(membershipType)
                .renewalFlag(isRenewal) // 재수강 여부 설정
                .discountAppliedPercentage(discountPercentage)
                .createdBy(user.getUuid())
                .createdIp("N/A") // IP 주소 필요시 전달받아야 함
                .build();
        return enrollRepository.save(newEnroll);
    }

    private boolean isRenewal(User user, Lesson currentLesson) {
        YearMonth currentLessonMonth = YearMonth.from(currentLesson.getStartDate());
        YearMonth previousMonth = currentLessonMonth.minusMonths(1);
        LocalDate previousMonthStart = previousMonth.atDay(1);
        LocalDate previousMonthEnd = previousMonth.atEndOfMonth();

        List<Enroll> previousMonthEnrollments = enrollRepository.findPaidByUserForPreviousMonthLesson(
                user.getUuid(),
                PaymentStatus.PAID.name(),
                currentLesson.getTitle(),
                currentLesson.getLessonTime(),
                previousMonthStart,
                previousMonthEnd);

        return !previousMonthEnrollments.isEmpty();
    }

    private void createAndSavePayment(PaymentApprovalRequestDto approvalRequest, Enroll enroll, boolean lockerUsed,
            User user, String userIp) {
        int totalAmount = Integer.parseInt(approvalRequest.getAmt());
        int lessonAmount;
        int lockerAmountValue;

        if (lockerUsed) {
            lockerAmountValue = this.lockerFee;
            lessonAmount = totalAmount - lockerAmountValue;
        } else {
            lockerAmountValue = 0;
            lessonAmount = totalAmount;
        }

        Payment payment = Payment.builder()
                .enroll(enroll)
                .status(PaymentStatus.PAID)
                .paidAt(LocalDateTime.now())
                .moid(approvalRequest.getMoid())
                .tid(approvalRequest.getTid())
                .paidAmt(Integer.parseInt(approvalRequest.getAmt()))
                .payMethod(approvalRequest.getKispgPaymentResult().getPayMethod())
                .lockerAmount(lockerAmountValue)
                .createdBy(user.getUuid())
                .createdIp(userIp)
                .build();
        paymentRepository.save(payment);
    }

    private boolean callKispgApprovalApi(String tid, String moid, String amt, String ediDateFromAuth) {
        log.info("=== KISPG 승인 API 호출 시작 ===");
        log.info("📋 입력 파라미터 (인증 결과):");
        log.info("  - TID: {}", tid);
        log.info("  - MOID: {}", moid);
        log.info("  - AMT: {}", amt);
        log.info("  - Auth EdiDate: {}", ediDateFromAuth);

        String ediDate = generateEdiDate();
        String encData = generateApprovalHash(kispgMid, ediDate, amt);

        log.info("📋 KISPG 승인 요청 구성:");
        log.info("  - MID: {}", kispgMid);
        log.info("  - TID: {}", tid);
        log.info("  - goodsAmt: {}", amt);
        log.info("  - ediDate: {} (신규 생성)", ediDate);
        log.info("  - HashData (Raw): {}{}{}{}", kispgMid, ediDate, amt, merchantKey);
        log.info("  - encData (Hashed): {} (길이: {})", encData, encData.length());

        RestTemplate restTemplate = new RestTemplate();
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);

        Map<String, String> body = new HashMap<>();
        body.put("mid", kispgMid);
        body.put("tid", tid);
        body.put("goodsAmt", amt);
        body.put("ediDate", ediDate);
        body.put("encData", encData);
        body.put("charset", "UTF-8");

        HttpEntity<Map<String, String>> requestEntity = new HttpEntity<>(body, headers);

        String url = getKispgApiUrl("payment");
        log.info("📤 KISPG 승인 API 요청:");
        log.info("  - URL: {}", url);
        log.info("  - Method: POST");
        log.info("  - Content-Type: application/json");
        log.info("  - Body: {}", body);

        long startTime = System.currentTimeMillis();
        try {
            ResponseEntity<String> response = restTemplate.postForEntity(url, requestEntity, String.class);
            long endTime = System.currentTimeMillis();

            log.info("📥 KISPG 승인 API 응답 ({}ms):", endTime - startTime);
            log.info("  - Status Code: {}", response.getStatusCode());
            log.info("  - Response Body: {}", response.getBody());

            if (response.getStatusCode() == HttpStatus.OK) {
                try {
                    ObjectMapper objectMapper = new ObjectMapper();
                    KispgPaymentResultDto resultDto = objectMapper.readValue(response.getBody(),
                            KispgPaymentResultDto.class);

                    log.info("📋 KISPG 승인 결과 파싱:");
                    log.info("  - resultCd: {}", resultDto.getResultCd());
                    log.info("  - resultMsg: {}", resultDto.getResultMsg());
                    log.debug(objectMapper.writeValueAsString(resultDto));

                    if ("0000".equals(resultDto.getResultCd()) || "3001".equals(resultDto.getResultCd())
                            || "6001".equals(resultDto.getResultCd())) {
                        log.info("✅ KISPG 승인 성공: [{}] {}", resultDto.getResultCd(), resultDto.getResultMsg());
                        return true;
                    } else {
                        log.error("❌ KISPG 승인 실패: [{}] {}", resultDto.getResultCd(), resultDto.getResultMsg());
                        throw new BusinessRuleException(ErrorCode.PAYMENT_GATEWAY_APPROVAL_FAILED,
                                "결제 게이트웨이 승인에 실패했습니다: " + resultDto.getResultMsg());
                    }
                } catch (Exception e) {
                    log.error("KISPG 응답 파싱 중 에러 발생", e);
                    throw new BusinessRuleException(ErrorCode.PAYMENT_FAILED, "결제 게이트웨이 응답 처리 중 오류가 발생했습니다.");
                }
            } else {
                log.error("KISPG 승인 API 호출 실패. 응답 코드: {}", response.getStatusCode());
                return false;
            }
        } catch (HttpClientErrorException e) {
            log.error("KISPG 승인 API 호출 중 클라이언트 에러 발생: {} - {}", e.getStatusCode(), e.getResponseBodyAsString(), e);
            throw new BusinessRuleException(ErrorCode.PAYMENT_GATEWAY_ERROR,
                    "결제 게이트웨이 통신 중 오류가 발생했습니다: " + e.getMessage());
        } catch (Exception e) {
            log.error("KISPG 승인 API 호출 중 알 수 없는 에러 발생", e);
            e.printStackTrace();

            throw new BusinessRuleException(ErrorCode.PAYMENT_FAILED, "결제 게이트웨이 처리 중 알 수 없는 오류가 발생했습니다.");
        }
    }

    private String generateHash(String input) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] hash = digest.digest(input.getBytes(StandardCharsets.UTF_8));
            return Hex.encodeHexString(hash);
        } catch (NoSuchAlgorithmException e) {
            log.error("SHA-256 알고리즘을 찾을 수 없습니다.", e);
            throw new RuntimeException("SHA-256 알고리즘을 찾을 수 없습니다.", e);
        }
    }

    private EnrollDto convertToMypageEnrollDto(Enroll enroll) {
        if (enroll == null)
            return null;
        Lesson lesson = enroll.getLesson();

        EnrollDto.LessonDetails lessonDetails = EnrollDto.LessonDetails.builder()
                .lessonId(lesson.getLessonId())
                .title(lesson.getTitle())
                .name(lesson.getTitle())
                .startDate(lesson.getStartDate().toString())
                .endDate(lesson.getEndDate().toString())
                .capacity(lesson.getCapacity())
                .price(java.math.BigDecimal.valueOf(lesson.getPrice()))
                .instructor(null)
                .location(null)
                .build();

        return EnrollDto.builder()
                .enrollId(enroll.getEnrollId())
                .lesson(lessonDetails)
                .status(enroll.getPayStatus())
                .applicationDate(enroll.getCreatedAt() != null ? enroll.getCreatedAt().atOffset(ZoneOffset.UTC) : null)
                .paymentExpireDt(enroll.getExpireDt() != null ? enroll.getExpireDt().atOffset(ZoneOffset.UTC) : null)
                .usesLocker(enroll.isUsesLocker())
                .membershipType(enroll.getMembershipType() != null ? enroll.getMembershipType().name() : null)
                .cancelStatus(enroll.getCancelStatus() != null ? enroll.getCancelStatus().name() : "NONE")
                .cancelReason(enroll.getCancelReason())
                .canAttemptPayment(false)
                .paymentPageUrl(null)
                .build();
    }

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
                return "MALE";
            case "FEMALE":
                return "FEMALE";
            default:
                log.warn("Unknown gender code: {}. Defaulting to MALE for locker assignment.", genderCode);
                return "MALE";
        }
    }

    @Override
    @Transactional
    public KispgCancelResponseDto cancelPayment(String tid, String moid, String payMethod, int cancelAmount,
            String reason, boolean isPartial) {
        String ediDate = generateEdiDate();
        String cancelAmountStr = String.valueOf(cancelAmount);
        String hashData = generateCancelHash(this.kispgMid, ediDate, cancelAmountStr);

        String partCanFlg = isPartial ? "1" : "0";

        KispgCancelRequestDto cancelRequest = KispgCancelRequestDto.builder()
                .mid(this.kispgMid)
                .tid(tid)
                .payMethod(payMethod)
                .ordNo(null)
                .canAmt(cancelAmountStr)
                .canMsg(reason)
                .ediDate(ediDate)
                .encData(hashData)
                .charset("UTF-8")
                .partCanFlg(partCanFlg)
                .build();

        return callKispgCancelApi(cancelRequest);
    }

    private KispgCancelResponseDto callKispgCancelApi(KispgCancelRequestDto requestDto) {
        String url = getKispgApiUrl("cancel");
        log.info("KISPG 취소 API 호출. URL: {}, 요청 데이터: {}", url, requestDto);

        try {
            HttpHeaders headers = new HttpHeaders();
            headers.setContentType(MediaType.APPLICATION_JSON);

            ObjectMapper objectMapper = new ObjectMapper();
            String jsonBody = objectMapper.writeValueAsString(requestDto);

            HttpEntity<String> entity = new HttpEntity<>(jsonBody, headers);
            RestTemplate restTemplate = new RestTemplate();

            ResponseEntity<KispgCancelResponseDto> response = restTemplate.postForEntity(url, entity,
                    KispgCancelResponseDto.class);

            if (response.getStatusCode() == HttpStatus.OK && response.getBody() != null) {
                KispgCancelResponseDto responseBody = response.getBody();
                log.info("KISPG 취소 API 응답 성공. 응답: {}", responseBody);

                if ("2001".equals(responseBody.getResultCd()) || "2002".equals(responseBody.getResultCd())) {
                    return responseBody;
                } else {
                    log.error("KISPG 취소 실패. Result Code: {}, Message: {}", responseBody.getResultCd(),
                            responseBody.getResultMsg());
                    String pgErrorMessage = responseBody.getResultMsg() != null ? responseBody.getResultMsg()
                            : "알 수 없는 PG사 오류";
                    throw new BusinessRuleException(ErrorCode.PAYMENT_CANCEL_FAILED, pgErrorMessage);
                }
            } else {
                log.error("KISPG 취소 API 호출 실패. 응답 코드: {}, 응답 본문: {}", response.getStatusCode(), response.getBody());
                throw new BusinessRuleException(ErrorCode.PAYMENT_GATEWAY_ERROR, "PG사로부터 응답을 받지 못했습니다.");
            }
        } catch (Exception e) {
            log.error("KISPG 취소 API 호출 중 예외 발생", e);
            throw new BusinessRuleException(ErrorCode.PAYMENT_CANCEL_FAILED, "PG사 취소 처리 중 오류 발생: " + e.getMessage());
        }
    }

    private String generateCancelHash(String mid, String ediDate, String canAmt) {
        if (merchantKey == null || merchantKey.trim().isEmpty()) {
            log.error("KISPG Merchant Key is not configured.");
            throw new IllegalStateException("KISPG Merchant Key가 설정되어 있지 않습니다.");
        }
        String rawHash = mid + ediDate + canAmt + merchantKey;
        log.info("Generated Cancel Hash from: mid={}, ediDate={}, canAmt={}", mid, ediDate, canAmt);
        return generateHash(rawHash);
    }

    @Override
    @Transactional(readOnly = true)
    public Map<String, Object> queryTransactionAtPg(KispgQueryRequestDto requestDto) {
        log.info("KISPG 결제 내역 조회 요청. TID: {}, MOID: {}", requestDto.getTid(), requestDto.getMoid());

        if ((requestDto.getTid() == null || requestDto.getTid().trim().isEmpty()) &&
                (requestDto.getMoid() == null || requestDto.getMoid().trim().isEmpty())) {
            throw new BusinessRuleException(ErrorCode.INVALID_INPUT_VALUE, "TID 또는 MOID 중 하나는 필수입니다.");
        }

        String ediDate = generateEdiDate();
        String amt = requestDto.getAmt();
        String tid = requestDto.getTid();
        String moid = requestDto.getMoid();

        String requestHashData = kispgMid + ediDate + amt + merchantKey;
        log.info("[KISPG 거래조회 요청] 요청 해시 데이터: mid + ediDate + amt + merchantKey = {}", requestHashData);
        String encData = generateHash(requestHashData);
        log.info("[KISPG 거래조회 요청] 생성된 해시: {}", encData);

        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);

        Map<String, String> body = new HashMap<>();
        body.put("mid", kispgMid);
        body.put("ver", "2");
        if (requestDto.getTid() != null && !requestDto.getTid().trim().isEmpty()) {
            body.put("tid", requestDto.getTid());
        }
        if (requestDto.getMoid() != null && !requestDto.getMoid().trim().isEmpty()) {
            body.put("moid", requestDto.getMoid());
        }
        body.put("amt", amt);
        body.put("ediDate", ediDate);
        body.put("encData", encData);
        body.put("signData", "");

        try {
            ObjectMapper objectMapper = new ObjectMapper();
            String jsonBody = objectMapper.writeValueAsString(body);
            HttpEntity<String> request = new HttpEntity<>(jsonBody, headers);
            log.info("[KISPG 거래조회 요청] PG 요청 전문: {}", request.toString());

            RestTemplate restTemplate = new RestTemplate();
            String orderUrl = getKispgApiUrl("order");
            ResponseEntity<Map> response = restTemplate.postForEntity(orderUrl, request, Map.class);
            log.info("[KISPG 거래조회 요청] PG 응답 전문: {}", response.toString());
            Map<String, Object> responseBody = response.getBody();

            if (responseBody == null) {
                log.error("KISPG 조회 API 응답 본문이 비어있습니다.");
                throw new BusinessRuleException(ErrorCode.PAYMENT_GATEWAY_ERROR,
                        "PG사 조회 실패: 응답 본문이 비어있습니다.");
            }

            String resultCd = (String) responseBody.get("resultCd");

            if ("0000".equals(resultCd)) {
                log.info("KISPG 조회 성공. 응답: {}", responseBody);
                String resultMoid = (String) responseBody.get("moid");
                String resultAmt = (String) responseBody.get("amt");
                String resultTid = (String) responseBody.get("tid");
                String receivedEdiDate = (String) responseBody.get("ediDate");

                String verificationHashData = kispgMid + resultMoid + resultAmt + receivedEdiDate + merchantKey;
                log.info("[KISPG 거래조회 응답] 해시 검증 데이터: {}", verificationHashData);
                String verificationHash = generateHash(verificationHashData);
                log.info("[KISPG 거래조회 응답] 생성된 검증 해시: {}", verificationHash);

                return responseBody;
            } else {
                String resultMsg = (String) responseBody.get("resultMsg");
                log.error("KISPG 조회 실패. Result Code: {}, Message: {}", resultCd, resultMsg);
                throw new BusinessRuleException(ErrorCode.PG_TRANSACTION_NOT_FOUND, "PG사 조회 실패: " + resultMsg);
            }
        } catch (Exception e) {
            log.error("KISPG 조회 API 호출 중 예외 발생", e);
            throw new BusinessRuleException(ErrorCode.PAYMENT_GATEWAY_ERROR,
                    "PG사 조회 처리 중 오류 발생: " + e.getMessage());
        }
    }
}