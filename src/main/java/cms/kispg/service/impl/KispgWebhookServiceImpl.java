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

import java.time.LocalDateTime;
import java.time.LocalDate;
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
        if (KISPG_SUCCESS_CODE.equals(notification.getResultCode())) {
            // **** PAYMENT SUCCESS ****
            logger.info("[KISPG Webhook] Payment success for moid: {} (enrollId: {}), tid: {}", notification.getMoid(),
                    enrollId, notification.getTid());

            // temp moid인 경우 결제 성공 시점에 수강신청 생성
            if (isTempMoid) {
                logger.info(
                        "[KISPG Webhook] Processing temp moid: {}, creating enrollment for lessonId: {}, userUuidPrefix: {}",
                        notification.getMoid(), lessonId, userUuidPrefix);
                try {
                    enroll = createEnrollmentFromTempMoid(notification, lessonId, userUuidPrefix);
                    logger.info("[KISPG Webhook] Successfully created enrollment from temp moid. New enrollId: {}",
                            enroll.getEnrollId());
                } catch (Exception e) {
                    logger.error("[KISPG Webhook] Failed to create enrollment from temp moid: {}. Error: {}",
                            notification.getMoid(), e.getMessage(), e);
                    return "FAIL";
                }
            }

            // If already PAID (e.g., /confirm API ran first and somehow set it, though
            // unlikely for webhook to be primary), just ensure locker consistency.
            if ("PAID".equalsIgnoreCase(enroll.getPayStatus())) {
                logger.info("[KISPG Webhook] EnrollId: {} already marked PAID. Ensuring locker consistency.",
                        enroll.getEnrollId());
                // Fall through to locker logic to ensure it's aligned with usesLocker flag and
                // pgToken
            } else {
                enroll.setPayStatus("PAID");
                logger.info("[KISPG Webhook] Set payStatus to PAID for enrollId: {}", enroll.getEnrollId());
                // Potentially set other fields on enroll like payment_date, etc.
            }

            User user = enroll.getUser();
            if (user == null) {
                logger.error("[KISPG Webhook] User not found for enrollId: {}. Cannot process locker logic.",
                        enroll.getEnrollId());
                throw new BusinessRuleException(ErrorCode.USER_NOT_FOUND,
                        "User not found for enrollment, cannot allocate locker.");
            }

            // --- New Locker Allocation Logic with Renewal Transfer ---
            boolean isRenewalLockerTransfer = false; // Flag to track if this is a transfer

            if (enroll.isRenewalFlag() && enroll.isUsesLocker()) {
                logger.info("[KISPG Webhook] Locker Renewal Logic: enrollId {} is renewal and usesLocker=true.",
                        enroll.getEnrollId());
                // This is a renewal and the user wants a locker for the new period.
                LocalDate currentLessonStartDate = enroll.getLesson().getStartDate();
                // Assumption: previous month is literally one month prior. Adjust if business
                // rule is different (e.g., specific day cutoffs)
                LocalDate previousMonthDate = currentLessonStartDate.minusMonths(1);

                List<Enroll> previousEnrollments = enrollRepository.findPreviousPaidLockerEnrollmentsForUser(
                        user.getUuid(),
                        currentLessonStartDate,
                        previousMonthDate);
                logger.info(
                        "[KISPG Webhook] Found {} previous paid locker enrollments for user {} for month prior to {}",
                        previousEnrollments.size(), user.getUuid(), currentLessonStartDate);

                if (!previousEnrollments.isEmpty()) {
                    Enroll previousEnroll = previousEnrollments.get(0); // Get the latest one from the previous month
                                                                        // that had a locker
                    logger.info(
                            "[KISPG Webhook] Locker Renewal: Attempting transfer from previousEnrollId: {} (lockerAllocated: {})",
                            previousEnroll.getEnrollId(), previousEnroll.isLockerAllocated());

                    isRenewalLockerTransfer = true;
                    enroll.setLockerAllocated(true);
                    enroll.setLockerPgToken(notification.getTid()); // Associate current payment with this locker
                                                                    // allocation

                    // Mark the old enrollment's locker as no longer allocated (it's been
                    // transferred)
                    previousEnroll.setLockerAllocated(false);
                    previousEnroll.setLockerPgToken(null); // Clear its association
                    enrollRepository.save(previousEnroll); // Save changes to the old enrollment

                    logger.info(
                            "[KISPG Webhook] Locker transferred for renewal. User: {}, New EnrollId: {}, Previous EnrollId: {} (now lockerAllocated: {}), PG Token: {}",
                            user.getUuid(), enroll.getEnrollId(), previousEnroll.getEnrollId(),
                            previousEnroll.isLockerAllocated(), notification.getTid());
                } else {
                    logger.info(
                            "[KISPG Webhook] Locker Renewal: No eligible previous locker found for transfer for enrollId {}. Proceeding with standard allocation/confirmation.",
                            enroll.getEnrollId());
                }
            }

            // Adjusted standard allocation/confirmation path
            if (!isRenewalLockerTransfer) {
                logger.info(
                        "[KISPG Webhook] Standard Locker Logic for enrollId {}: isRenewalLockerTransfer=false, usesLocker={}, lockerAllocated={}",
                        enroll.getEnrollId(), enroll.isUsesLocker(), enroll.isLockerAllocated());
                if (enroll.isUsesLocker()) {
                    // Locker was requested during initial enrollment.
                    // EnrollmentServiceImpl should have already attempted to allocate and set
                    // enroll.lockerAllocated.
                    if (enroll.isLockerAllocated()) {
                        // Locker was successfully allocated during initial enrollment.
                        // Now, associate this successful payment transaction with the locker.
                        enroll.setLockerPgToken(notification.getTid());
                        logger.info(
                                "[KISPG Webhook] Confirmed locker allocation for enrollId: {} (already allocated during application). PG Token: {}",
                                enroll.getEnrollId(), notification.getTid());
                    } else {
                        // This case implies usesLocker=true, but lockerAllocated=false.
                        // This means the initial attempt in EnrollmentServiceImpl to allocate a locker
                        // failed (e.g., no inventory, missing gender then).
                        // We should not attempt to allocate it here again. The enrollment proceeds
                        // without a locker.
                        logger.warn(
                                "[KISPG Webhook] User for enrollId {} wanted a locker (usesLocker=true), but it was not allocated during initial application (lockerAllocated=false). Payment successful, but no locker provided.",
                                enroll.getEnrollId());
                        // Ensure usesLocker is marked false if no locker is actually provided, despite
                        // initial request.
                        // This might be controversial - does the user expect to be told earlier?
                        // EnrollmentServiceImpl should have failed if locker was mandatory.
                        // If EnrollmentServiceImpl allows proceeding without locker even if requested
                        // and unavailable, then this is just a confirmation.
                        // For safety, ensure usesLocker reflects reality if no locker is given.
                        // enroll.setUsesLocker(false); // Optional: align usesLocker with reality if it
                        // couldn't be allocated.
                    }
                } else { // User does NOT want locker (enroll.isUsesLocker() is false)
                    if (enroll.isLockerAllocated()) {
                        // This means a locker WAS allocated during initial application (e.g.,
                        // usesLocker was true then),
                        // but now (perhaps through a /payment/confirm step not detailed here, or if
                        // usesLocker could change post-application),
                        // the final decision is NO locker. We MUST release the previously allocated
                        // one.
                        logger.info(
                                "[KISPG Webhook] User for enrollId {} now indicates NO locker (usesLocker=false), but one was previously allocated (lockerAllocated={}). Releasing it.",
                                enroll.getEnrollId(), enroll.isLockerAllocated());
                        // User user = enroll.getUser(); // User should have been fetched earlier and is
                        // in scope
                        if (user != null && user.getGender() != null && !user.getGender().trim().isEmpty()) {
                            try {
                                lockerService.decrementUsedQuantity(user.getGender().toUpperCase());
                                enroll.setLockerAllocated(false);
                                enroll.setLockerPgToken(null);
                                logger.info(
                                        "[KISPG Webhook] Locker successfully released for enrollId {} due to usesLocker=false post-allocation.",
                                        enroll.getEnrollId());
                            } catch (Exception e) {
                                logger.error(
                                        "[KISPG Webhook] Error decrementing locker for enrollId {} during 'usesLocker=false' case. Error: {}",
                                        enroll.getEnrollId(), e.getMessage(), e);
                                // Even if decrement fails, ensure flags are set.
                                enroll.setLockerAllocated(false);
                                enroll.setLockerPgToken(null);
                            }
                        } else {
                            logger.warn(
                                    "[KISPG Webhook] User for enrollId {} indicated NO locker, one was allocated, but gender is missing. Cannot reliably decrement inventory. Flags set to no locker.",
                                    enroll.getEnrollId());
                            enroll.setLockerAllocated(false);
                            enroll.setLockerPgToken(null);
                        }
                    }
                    // If enroll.isUsesLocker() is false AND enroll.isLockerAllocated() is false, no
                    // action needed.
                }
            }
            // --- End of Adjusted Locker Logic ---

            logger.info(
                    "[KISPG Webhook] Attempting to save Enroll (enrollId: {}) with payStatus: {}, lockerAllocated: {}, lockerPgToken: '{}'",
                    enroll.getEnrollId(), enroll.getPayStatus(), enroll.isLockerAllocated(), enroll.getLockerPgToken());
            enrollRepository.save(enroll);
            logger.info("[KISPG Webhook] Successfully saved Enroll (enrollId: {})", enroll.getEnrollId());

            // Create or Update Payment record
            List<Payment> paymentsSuccess = paymentRepository
                    .findByEnroll_EnrollIdOrderByCreatedAtDesc(enroll.getEnrollId());
            Payment payment = paymentsSuccess.isEmpty() ? new Payment() : paymentsSuccess.get(0);

            payment.setEnroll(enroll);
            payment.setTid(notification.getTid());
            int paidAmtFromNotification = 0;
            try {
                paidAmtFromNotification = Integer.parseInt(notification.getAmt());
                payment.setPaidAmt(paidAmtFromNotification);
            } catch (NumberFormatException e) {
                logger.error("[KISPG Webhook] Could not parse 'amt' to Integer: {}. Storing as 0.",
                        notification.getAmt());
                payment.setPaidAmt(0);
                paidAmtFromNotification = 0; // Ensure it's 0 for further calculations
            }
            logger.info("[KISPG Webhook] Payment amount from notification (amt: '{}') parsed as: {}",
                    notification.getAmt(), paidAmtFromNotification);

            // 강습료 및 사물함 요금 분리 저장
            if (enroll.getLesson() != null) {
                int lessonPrice = enroll.getLesson().getPrice();
                if (enroll.isUsesLocker()) {
                    // 사용자가 사물함을 사용하고, 총 결제액이 강습료 + 사물함 기본요금과 일치하는지 확인
                    // 또는 총 결제액에서 강습료를 뺀 나머지를 사물함 요금으로 간주
                    // 여기서는 더 간단한 접근: 총액에서 강습료를 빼고, 그 값이 사물함 요금과 유사하면 할당
                    int calculatedLockerFee = paidAmtFromNotification - lessonPrice;
                    if (calculatedLockerFee > 0 && calculatedLockerFee <= defaultLockerFee + 1000
                            && calculatedLockerFee >= defaultLockerFee - 1000) { // 약간의 오차 허용
                        payment.setLessonAmount(lessonPrice);
                        payment.setLockerAmount(calculatedLockerFee);
                    } else if (paidAmtFromNotification == lessonPrice) { // 사물함 요금 없이 강습료만 결제된 경우
                        payment.setLessonAmount(lessonPrice);
                        payment.setLockerAmount(0);
                    } else { // 금액이 예상과 다를 경우, 총액을 강습료에 넣고 사물함은 0으로 처리 (또는 로깅 강화)
                        logger.warn(
                                "[KISPG Webhook] Paid amount {} does not align with lesson price {} and locker fee for enrollId: {}. Storing full amount as lessonAmount.",
                                paidAmtFromNotification, lessonPrice, enroll.getEnrollId());
                        payment.setLessonAmount(paidAmtFromNotification);
                        payment.setLockerAmount(0);
                    }
                } else {
                    // 사물함 미사용 시, 총 결제액이 강습료와 일치하는지 확인
                    if (paidAmtFromNotification == lessonPrice) {
                        payment.setLessonAmount(lessonPrice);
                        payment.setLockerAmount(0);
                    } else {
                        logger.warn(
                                "[KISPG Webhook] Paid amount {} does not match lesson price {} (no locker) for enrollId: {}. Storing full amount as lessonAmount.",
                                paidAmtFromNotification, lessonPrice, enroll.getEnrollId());
                        payment.setLessonAmount(paidAmtFromNotification);
                        payment.setLockerAmount(0);
                    }
                }
            } else {
                logger.error(
                        "[KISPG Webhook] Lesson not found for enrollId: {} during payment amount splitting. Storing full amount as lessonAmount.",
                        enroll.getEnrollId());
                payment.setLessonAmount(paidAmtFromNotification);
                payment.setLockerAmount(0);
            }

            payment.setStatus(PaymentStatus.PAID);
            payment.setPayMethod(notification.getPayMethod());
            payment.setPgResultCode(notification.getResultCode());
            payment.setPgResultMsg(notification.getResultMsg());
            payment.setPaidAt(LocalDateTime.now());
            payment.setMoid(notification.getMoid());

            logger.info("[KISPG Webhook] Attempting to save Payment record for enrollId: {}, tid: {}, moid: {}",
                    enroll.getEnrollId(), notification.getTid(), notification.getMoid());
            logger.debug("[KISPG Webhook DETAIL] Payment object before save: {}", payment);

            try {
                Payment savedPayment = paymentRepository.save(payment);
                logger.info(
                        "[KISPG Webhook] Successfully saved Payment record (paymentId: {}) for enrollId: {}, tid: {}, moid: {}",
                        savedPayment.getId(), enroll.getEnrollId(), notification.getTid(), notification.getMoid());

                // 저장 후 moid 검증
                if (savedPayment.getMoid() != null && savedPayment.getMoid().equals(notification.getMoid())) {
                    logger.info("[KISPG Webhook] Payment moid correctly saved: {}", savedPayment.getMoid());
                } else {
                    logger.error("[KISPG Webhook] Payment moid save mismatch! Expected: {}, Actual: {}",
                            notification.getMoid(), savedPayment.getMoid());
                }
            } catch (Exception e) {
                logger.error("[KISPG Webhook] Failed to save Payment record for moid: {}. Error: {}",
                        notification.getMoid(), e.getMessage(), e);
                return "FAIL";
            }

            // TODO: Send notifications to user (email/SMS) about successful payment and
            // locker status if applicable.

            return "OK"; // KISPG expects "OK" or "SUCCESS" (check their docs for exact string)

        } else {
            // **** PAYMENT FAILURE or OTHER STATUS (e.g., REFUND notification from KISPG)
            // ****
            logger.warn(
                    "[KISPG Webhook] Payment FAILED or non-success status for moid: {} (enrollId: {}), tid: {}, resultCode: {}, resultMsg: {}",
                    notification.getMoid(), enrollId, notification.getTid(), notification.getResultCode(),
                    notification.getResultMsg());

            // Potential KISPG refund codes (these are examples, refer to KISPG docs)
            // String KISPG_REFUND_SUCCESS_CODE = "2001"; // Example: Full refund success
            // String KISPG_PARTIAL_REFUND_SUCCESS_CODE = "2002"; // Example: Partial refund
            // success

            // If it's a notification about a full refund or a definitive final failure for
            // an enrollment that HAD a locker.
            // This part needs to be robust based on actual KISPG non-success codes that
            // imply the original transaction is void or fully reversed.
            // Let's assume any non-KISPG_SUCCESS_CODE means the payment is not going
            // through or is reversed for now.
            // More specific handling based on resultCode would be better.

            if (enroll.isLockerAllocated()) {
                logger.info(
                        "[KISPG Webhook] Payment failed/reversed for enrollId: {} which had a locker allocated. Releasing locker.",
                        enroll.getEnrollId());
                User user = enroll.getUser(); // User should have been fetched earlier
                if (user != null && user.getGender() != null && !user.getGender().trim().isEmpty()) {
                    try {
                        lockerService.decrementUsedQuantity(user.getGender().toUpperCase());
                        enroll.setLockerAllocated(false);
                        enroll.setLockerPgToken(null);
                        // Also update enroll status if this is a final failure
                        // enroll.setStatus("FAILED"); // Or based on resultCode
                        // enroll.setPayStatus("FAILED"); // Or "REFUNDED"
                        logger.info("[KISPG Webhook] Locker released for enrollId {} due to payment failure/reversal.",
                                enroll.getEnrollId());
                    } catch (Exception e) {
                        logger.error(
                                "[KISPG Webhook] Error decrementing locker for enrollId {} during payment failure/reversal. Error: {}",
                                enroll.getEnrollId(), e.getMessage(), e);
                        // Even if decrement fails, ensure flags are set.
                        enroll.setLockerAllocated(false);
                        enroll.setLockerPgToken(null);
                    }
                } else {
                    logger.warn(
                            "[KISPG Webhook] Payment failed/reversed for enrollId: {} which had lockerAllocated=true, but user/gender info is missing. Cannot release locker automatically.",
                            enroll.getEnrollId());
                    enroll.setLockerAllocated(false); // Still mark as not allocated
                    enroll.setLockerPgToken(null);
                }
            }

            // Update enrollment status based on failure/refund type
            // Example:
            // if (KISPG_REFUND_SUCCESS_CODE.equals(notification.getResultCode())) {
            // enroll.setPayStatus("REFUNDED");
            // enroll.setStatus("CANCELED"); // Or appropriate status
            // } else {
            // enroll.setPayStatus("FAILED"); // Generic failure
            // // enroll.setStatus("FAILED"); // Or keep as APPLIED if user can retry?
            // Depends on flow.
            // }

            enrollRepository.save(enroll);
            // Create/Update Payment record with failure details
            List<Payment> paymentsFailure = paymentRepository
                    .findByEnroll_EnrollIdOrderByCreatedAtDesc(enroll.getEnrollId());
            Payment paymentOnFailure = paymentsFailure.isEmpty() ? new Payment() : paymentsFailure.get(0);

            paymentOnFailure.setEnroll(enroll); // Link to enroll
            paymentOnFailure.setTid(notification.getTid()); // Store TID even for failures for reference
            try {
                paymentOnFailure.setPaidAmt(0); // Failed, so paid amount is 0
                if (notification.getAmt() != null) { // Log intended amount if available
                    logger.info("[KISPG Webhook] Failed payment intended amount for moid {}: {}",
                            notification.getMoid(), notification.getAmt());
                }
            } catch (NumberFormatException e) {
                logger.error("[KISPG Webhook] Could not parse 'amt' during failure processing: {}",
                        notification.getAmt());
            }
            paymentOnFailure.setLessonAmount(0); // 실패 시 강습료/사물함료 0
            paymentOnFailure.setLockerAmount(0);
            paymentOnFailure.setStatus(PaymentStatus.FAILED);
            paymentOnFailure.setPayMethod(notification.getPayMethod());
            paymentOnFailure.setPgResultCode(notification.getResultCode());
            paymentOnFailure.setPgResultMsg(notification.getResultMsg());
            paymentOnFailure.setMoid(notification.getMoid()); // moid 저장 추가
            // paidAt might be null or current time for failure record
            logger.info("[KISPG Webhook] Attempting to save FAILED Payment record for enrollId: {}, tid: {}",
                    enroll.getEnrollId(), notification.getTid());
            logger.debug("[KISPG Webhook DETAIL] Failed Payment object before save: {}", paymentOnFailure);
            paymentRepository.save(paymentOnFailure);
            logger.info(
                    "[KISPG Webhook] Successfully saved FAILED Payment record (paymentId: {}) for enrollId: {}, tid: {}",
                    paymentOnFailure.getId(), enroll.getEnrollId(), notification.getTid());

            // For failures, KISPG docs will specify what to return. Usually "OK" to
            // acknowledge receipt.
            return "OK";
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

        // 5. 최종 금액 계산
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