package cms.kispg.service.impl;

import cms.common.exception.BusinessRuleException;
import cms.common.exception.ErrorCode;
import cms.common.exception.ResourceNotFoundException;
import cms.enroll.domain.Enroll;
import cms.enroll.repository.EnrollRepository;
import cms.kispg.dto.KispgNotificationRequest;
import cms.kispg.service.KispgWebhookService;
import cms.kispg.util.KispgSecurityUtil;
import cms.locker.service.LockerService;
import cms.payment.domain.Payment;
import cms.payment.repository.PaymentRepository;
import cms.user.domain.User;
import cms.user.repository.UserRepository;
import cms.swimming.domain.Lesson;
import cms.swimming.repository.LessonRepository;
import cms.payment.domain.PaymentStatus;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import cms.payment.service.PaymentService;

import java.time.LocalDateTime;
import java.time.LocalDate;
import java.time.YearMonth;
import java.util.Arrays;
import java.util.List;
import java.util.Optional;
import java.util.Collections;

@Service
@RequiredArgsConstructor
public class KispgWebhookServiceImpl implements KispgWebhookService {

    private static final Logger logger = LoggerFactory.getLogger(KispgWebhookServiceImpl.class);

    private final EnrollRepository enrollRepository;
    private final PaymentRepository paymentRepository; // Assuming this is injected
    private final LockerService lockerService;
    private final KispgSecurityUtil kispgSecurityUtil; // 해시 검증 유틸리티
    private final UserRepository userRepository;
    private final LessonRepository lessonRepository;
    private final PaymentService paymentService;

    @Value("${kispg.merchantKey}") // Example: load merchantKey from properties
    private String merchantKey;

    @Value("${cors.allowed-origins}") // Example: load allowed IPs from properties, comma-separated
    private String allowedWebhookIps;
    private List<String> allowedIpList;

    private static final String KISPG_SUCCESS_CODE = "0000"; // KISPG's typical success code

    @Value("${spring.profiles.active:dev}")
    private String activeProfile;

    @Value("${app.locker.fee:5000}") // 사물함 기본 요금 주입
    private int defaultLockerFee;

    @javax.annotation.PostConstruct
    public void init() {
        if (allowedWebhookIps != null && !allowedWebhookIps.isEmpty() && !"dev".equalsIgnoreCase(activeProfile)) {
            // 프로덕션 환경에서만 IP 허용 목록 사용 (CORS URL이 아닌 실제 IP 주소 필요)
            allowedIpList = Arrays.asList(allowedWebhookIps.split(","));
        } else {
            allowedIpList = Collections.emptyList(); // 개발 환경에서는 모든 IP 허용
        }
        logger.info("KISPG Webhook Service initialized. Active profile: {}. IP Whitelist enabled: {}. Allowed IPs: {}",
                activeProfile, !allowedIpList.isEmpty(), allowedIpList.isEmpty() ? "ANY (DEV MODE)" : allowedIpList);
    }

    @Override
    @Transactional
    public String processPaymentNotification(KispgNotificationRequest notification, String clientIp) {
        logger.info(
                "[KISPG Webhook START] Processing notification for moid: {}, tid: {}, resultCode: {}, resultMsg: '{}', clientIp: {}",
                notification.getMoid(), notification.getTid(), notification.getResultCode(),
                notification.getResultMsg(), clientIp);
        logger.debug("[KISPG Webhook DETAIL] Full notification: {}", notification);

        // 1. Security Validation
        // IP Whitelisting (only in prod, if configured)
        if ("prod".equalsIgnoreCase(activeProfile) && !allowedIpList.isEmpty() && !allowedIpList.contains(clientIp)) {
            logger.warn("[KISPG Webhook] Denied access from unauthorized IP: {} for moid: {}. Allowed IPs: {}",
                    clientIp, notification.getMoid(), allowedIpList);
            // KISPG might expect specific error string or just a non-200 response.
            // Returning "FAIL" or "ERROR" as a generic failure indicator.
            return "FAIL";
        }

        // Hash validation (encData) - 실제 해시 검증 활성화
        boolean isValidSignature = kispgSecurityUtil.verifyNotificationHash(notification);
        if (!isValidSignature) {
            logger.warn("[KISPG Webhook] Invalid signature (encData) for moid: {}. IP: {}", notification.getMoid(),
                    clientIp);
            return "FAIL";
        }
        logger.info("[KISPG Webhook] Signature validation successful for moid: {}", notification.getMoid());

        // 2. Parameter & Enrollment/Payment Record Check
        // Attempt to parse moid to get enrollment information
        // New format: temp_{lessonId}_{userUuid_prefix}_{timestamp} OR existing:
        // enroll_{enrollId}_{timestamp}
        Long enrollId;
        final Long lessonId;
        final String userUuidPrefix; // temp moid에서 추출한 사용자 UUID prefix
        final boolean isTempMoid;

        try {
            String moid = notification.getMoid();
            if (moid == null) {
                throw new IllegalArgumentException("MOID is null");
            }

            if (moid.startsWith("temp_")) {
                // New temporary format: temp_{lessonId}_{userUuid_prefix}_{timestamp}
                isTempMoid = true;
                String[] parts = moid.substring("temp_".length()).split("_");
                if (parts.length < 3) {
                    throw new IllegalArgumentException("Invalid temp MOID format: " + moid);
                }
                lessonId = Long.parseLong(parts[0]);
                enrollId = null;
                userUuidPrefix = parts[1]; // 사용자 UUID prefix 저장
                // We need to find the user by UUID prefix - this is a limitation of the temp
                // approach
                // For now, we'll get it from the notification's mbsUsrId field instead
                logger.info("[KISPG Webhook] Parsed temp moid - lessonId: {}, userUuidPrefix: {}", lessonId,
                        userUuidPrefix);
            } else if (moid.startsWith("enroll_")) {
                // Existing format: enroll_{enrollId}_{timestamp}
                isTempMoid = false;
                lessonId = null;
                userUuidPrefix = null;
                String enrollIdStr = moid.substring("enroll_".length()).split("_")[0];
                enrollId = Long.parseLong(enrollIdStr);
                logger.info("[KISPG Webhook] Parsed existing moid - enrollId: {}", enrollId);
            } else {
                throw new IllegalArgumentException("Unknown MOID format: " + moid);
            }
        } catch (NumberFormatException | NullPointerException | ArrayIndexOutOfBoundsException e) {
            logger.error("[KISPG Webhook] Could not parse moid: {}. Error: {}", notification.getMoid(), e.getMessage());
            return "FAIL"; // Invalid moid format
        }

        Enroll enroll = null;
        if (isTempMoid) {
            // For temp moid, we need to create the enrollment during payment success
            logger.info("[KISPG Webhook] Processing temp moid payment for lessonId: {}", lessonId);

            // temp moid에서는 결제 성공 시에만 수강신청을 생성하므로 여기서는 아직 생성하지 않고
            // 결제 성공 처리 블록에서 생성하도록 함
            // 일단 enroll은 null로 두고 결제 성공 시 처리
        } else {
            // Existing flow - find the enrollment
            enroll = enrollRepository.findById(enrollId)
                    .orElseThrow(() -> {
                        logger.error(
                                "[KISPG Webhook] Enroll not found for moid (parsed enrollId: {}). Original moid: {}",
                                enrollId, notification.getMoid());
                        return new ResourceNotFoundException("Enrollment not found for moid: " + notification.getMoid(),
                                ErrorCode.ENROLLMENT_NOT_FOUND);
                    });
        }

        if (enroll != null) {
            logger.info(
                    "[KISPG Webhook] Found Enroll record (enrollId: {}) with status: {}, usesLocker: {}, isRenewal: {}, lockerAllocated: {}",
                    enroll.getEnrollId(), enroll.getPayStatus(), enroll.isUsesLocker(), enroll.isRenewalFlag(),
                    enroll.isLockerAllocated());
        }

        // Check for duplicate TIDs to ensure idempotency
        Optional<Payment> existingPaymentWithTid = paymentRepository.findByTid(notification.getTid());
        if (existingPaymentWithTid.isPresent()) {
            Payment p = existingPaymentWithTid.get();
            // If it's for the same enrollment and already marked as PAID, it's a duplicate
            // notification.
            if (!isTempMoid && enroll != null && p.getEnroll() != null &&
                    p.getEnroll().getEnrollId().equals(enroll.getEnrollId()) && "PAID".equals(p.getStatus())) {
                logger.info(
                        "[KISPG Webhook] Duplicate successful notification received for tid: {} and moid: {}. Already processed.",
                        notification.getTid(), notification.getMoid());
                return "OK"; // Acknowledge duplicate, but don't reprocess. KISPG expects "OK" or "SUCCESS"
                             // for success.
            } else if (isTempMoid && "PAID".equals(p.getStatus()) && notification.getMoid().equals(p.getMoid())) {
                // For temp moid, check if same moid and status is already processed
                logger.info(
                        "[KISPG Webhook] Duplicate temp moid notification received for tid: {} and moid: {}. Already processed.",
                        notification.getTid(), notification.getMoid());
                return "OK";
            } else {
                // Different enroll or not PAID: This is an issue. Maybe TID reuse or an error
                // state.
                logger.error(
                        "[KISPG Webhook] TID {} already exists but for a different enroll ({}) or status ({}). Current moid: {}. Halting.",
                        notification.getTid(), p.getEnroll() != null ? p.getEnroll().getEnrollId() : "NULL_ENROLL",
                        p.getStatus(), notification.getMoid());
                return "FAIL"; // Potentially problematic
            }
        }

        // 3. Process based on KISPG Result Code
        // **** 결제 성공/실패 처리를 PaymentService로 위임 ****
        if (isTempMoid) {
            // temp_moid 로직은 Enroll 생성 책임이 있어 Webhook 서비스에 남겨둠
            if (KISPG_SUCCESS_CODE.equals(notification.getResultCode())) {
                // **** PAYMENT SUCCESS for temp_moid ****
                logger.info("[KISPG Webhook] Payment success for temp moid: {}", notification.getMoid());
                try {
                    enroll = createEnrollmentFromTempMoid(notification, lessonId, userUuidPrefix);
                    logger.info("[KISPG Webhook] Successfully created enrollment from temp moid. New enrollId: {}",
                            enroll.getEnrollId());
                    // Enroll 생성 후, 실제 Payment 처리
                    processSuccess(notification, enroll);
                } catch (Exception e) {
                    logger.error("[KISPG Webhook] Failed to create enrollment or payment from temp moid: {}. Error: {}",
                            notification.getMoid(), e.getMessage(), e);
                    return "FAIL";
                }
            } else {
                // temp_moid 결제 실패 시, 별도 처리 없이 로그만 남기고 정상 종료 (Enroll 생성 안됨)
                logger.warn("[KISPG Webhook] Payment failed for temp moid: {}. No action taken.",
                        notification.getMoid());
            }
        } else {
            // enroll_moid 로직은 중앙화된 PaymentService 호출
            try {
                paymentService.createPaymentFromWebhook(notification);
                logger.info("[KISPG Webhook] Successfully processed webhook via PaymentService for moid: {}",
                        notification.getMoid());
            } catch (Exception e) {
                logger.error(
                        "[KISPG Webhook] Error while processing webhook via PaymentService for moid: {}. Error: {}",
                        notification.getMoid(), e.getMessage(), e);
                return "FAIL";
            }
        }

        return "OK";
    }

    private void processSuccess(KispgNotificationRequest notification, Enroll enroll) {
        // 기존 processSuccess 로직에서 Payment 생성 및 저장 부분을 가져옴.
        // 이 부분도 장기적으로는 PaymentService로 통합하는 것을 고려.
        Payment payment = Payment.builder()
                .enroll(enroll)
                .status(PaymentStatus.PAID)
                .paidAt(LocalDateTime.now())
                .moid(notification.getMoid())
                .tid(notification.getTid())
                .paidAmt(Integer.parseInt(notification.getAmt()))
                .lessonAmount(Integer.parseInt(notification.getAmt()))
                .lockerAmount(0)
                .payMethod(notification.getPayMethod())
                .pgResultCode(notification.getResultCode())
                .pgResultMsg(notification.getResultMsg())
                .createdBy(enroll.getUser().getUuid())
                .updatedBy(enroll.getUser().getUuid())
                .build();
        paymentRepository.save(payment);

        enroll.setPayStatus("PAID");
        enrollRepository.save(enroll);

        // 사물함 할당 로직 등 추가 처리
        if (enroll.isUsesLocker()) {
            // ... 사물함 할당 로직 ...
        }
    }

    /**
     * temp moid로부터 수강신청을 생성합니다.
     */
    private Enroll createEnrollmentFromTempMoid(KispgNotificationRequest notification, Long lessonId,
            String userUuidPrefix) {
        logger.info("[KISPG Webhook] Creating enrollment from temp moid for lessonId: {}, userUuidPrefix: {}", lessonId,
                userUuidPrefix);

        // 1. Lesson 조회
        Lesson lesson = lessonRepository.findById(lessonId)
                .orElseThrow(
                        () -> new ResourceNotFoundException("강습을 찾을 수 없습니다: " + lessonId, ErrorCode.LESSON_NOT_FOUND));

        // 2. User 조회 (userUuidPrefix 사용)
        List<User> users = userRepository.findByUuidStartingWith(userUuidPrefix);

        if (users.isEmpty()) {
            throw new ResourceNotFoundException("사용자를 찾을 수 없습니다. UUID prefix: " + userUuidPrefix,
                    ErrorCode.USER_NOT_FOUND);
        }

        if (users.size() > 1) {
            logger.warn("[KISPG Webhook] Multiple users found with UUID prefix: {}. Using first one.", userUuidPrefix);
        }

        User user = users.get(0);
        logger.info("[KISPG Webhook] Found user: {} with UUID: {}", user.getUsername(), user.getUuid());

        // 3. 결제 금액으로부터 사물함 사용 여부 판단
        boolean usesLocker = false;
        try {
            int paidAmount = Integer.parseInt(notification.getAmt());
            int lessonPrice = lesson.getPrice();
            // 결제 금액이 강습료보다 크면 사물함 사용으로 판단
            if (paidAmount > lessonPrice) {
                usesLocker = true;
            }
        } catch (NumberFormatException e) {
            logger.warn("[KISPG Webhook] Could not parse payment amount: {}. Assuming no locker.",
                    notification.getAmt());
        }

        // 4. 사물함 배정 (사용하는 경우)
        boolean lockerAllocated = false;
        if (usesLocker) {
            if (user.getGender() != null && !user.getGender().trim().isEmpty()) {
                try {
                    lockerService.incrementUsedQuantity(user.getGender().toUpperCase());
                    lockerAllocated = true;
                    logger.info("[KISPG Webhook] Locker allocated for user: {} (gender: {})", user.getUsername(),
                            user.getGender());
                } catch (Exception e) {
                    logger.error("[KISPG Webhook] Failed to allocate locker for user: {}. Error: {}",
                            user.getUsername(), e.getMessage());
                    // 사물함 배정 실패 시에도 수강신청은 생성하되 사물함 없이 진행
                    usesLocker = false;
                }
            } else {
                logger.warn("[KISPG Webhook] User {} has no gender info. Cannot allocate locker.", user.getUsername());
                usesLocker = false;
            }
        }

        // 5. 재수강 여부(renewalFlag) 판단 로직 추가
        boolean isRenewal = false;
        try {
            // 현재 강습의 시작일 기준 지난달
            YearMonth lastMonth = YearMonth.from(lesson.getStartDate()).minusMonths(1);
            LocalDate lastMonthStart = lastMonth.atDay(1);
            LocalDate lastMonthEnd = lastMonth.atEndOfMonth();

            // 지난달의 같은 시간대 강습들을 조회
            List<Lesson> lastMonthLessons = lessonRepository.findByLessonTimeAndStartDateBetween(
                    lesson.getLessonTime(), lastMonthStart, lastMonthEnd);

            if (!lastMonthLessons.isEmpty()) {
                // 지난달 강습 목록 중에 사용자가 수강한 내역(PAID)이 있는지 확인
                isRenewal = enrollRepository.existsByUserAndLessonInAndPayStatus(user, lastMonthLessons, "PAID");
                if (isRenewal) {
                    logger.info("[KISPG Webhook] 재수강으로 확인되었습니다. User: {}, LessonTime: {}", user.getUsername(),
                            lesson.getLessonTime());
                }
            }
        } catch (Exception e) {
            logger.error("[KISPG Webhook] 재수강 여부 확인 중 오류 발생. User: {}, Lesson: {}. Error: {}", user.getUsername(),
                    lesson.getLessonId(), e.getMessage());
            // 오류 발생 시에는 재수강이 아닌 것으로 처리하여 진행
            isRenewal = false;
        }

        // 6. 최종 금액 계산
        int finalAmount = lesson.getPrice();
        if (usesLocker && lockerAllocated) {
            finalAmount += defaultLockerFee;
        }

        // 6. Enroll 엔티티 생성
        Enroll enroll = Enroll.builder()
                .user(user)
                .lesson(lesson)
                .status("APPLIED")
                .payStatus("PAID") // 결제 완료 상태로 바로 생성
                .expireDt(null) // 결제 완료되었으므로 만료시간 불필요
                .usesLocker(usesLocker)
                .lockerAllocated(lockerAllocated)
                .renewalFlag(isRenewal) // 재수강 여부 플래그 설정
                .membershipType(cms.enroll.domain.MembershipType.GENERAL) // 기본값
                .finalAmount(finalAmount)
                .discountAppliedPercentage(0) // 기본값
                .createdBy(user.getUuid())
                .createdIp("KISPG_WEBHOOK") // 웹훅에서 생성됨을 표시
                .build();

        Enroll savedEnroll = enrollRepository.save(enroll);
        logger.info(
                "[KISPG Webhook] Successfully created enrollment: enrollId={}, user={}, lesson={}, usesLocker={}, lockerAllocated={}",
                savedEnroll.getEnrollId(), user.getUsername(), lesson.getLessonId(), usesLocker, lockerAllocated);

        return savedEnroll;
    }
}