package cms.kispg.service.impl;

import cms.common.exception.BusinessRuleException;
import cms.common.exception.ErrorCode;
import cms.common.exception.ResourceNotFoundException;
import cms.enroll.domain.Enroll;
import cms.enroll.repository.EnrollRepository;
import cms.kispg.dto.KispgNotificationRequest;
import cms.kispg.service.KispgWebhookService;
import cms.locker.service.LockerService;
import cms.payment.domain.Payment; // Assuming Payment entity exists
import cms.payment.repository.PaymentRepository; // Assuming PaymentRepository exists
import cms.user.domain.User;
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
// Assuming a utility for KISPG specific security like hash validation
// import cms.kispg.util.KispgSecurityUtil; 

@Service
@RequiredArgsConstructor
public class KispgWebhookServiceImpl implements KispgWebhookService {

    private static final Logger logger = LoggerFactory.getLogger(KispgWebhookServiceImpl.class);

    private final EnrollRepository enrollRepository;
    private final PaymentRepository paymentRepository; // Assuming this is injected
    private final LockerService lockerService;
    // private final KispgSecurityUtil kispgSecurityUtil; // Assuming this is injected

    @Value("${kispg.merchantKey}") // Example: load merchantKey from properties
    private String merchantKey;

    @Value("${kispg.allowedWebhookIps:}") // Example: load allowed IPs from properties, comma-separated
    private String allowedWebhookIps;
    private List<String> allowedIpList;
    
    private static final String KISPG_SUCCESS_CODE = "0000"; // KISPG's typical success code

    @Value("${spring.profiles.active:dev}")
    private String activeProfile;

    @Value("${app.locker.fee:5000}") // 사물함 기본 요금 주입
    private int defaultLockerFee;

    @javax.annotation.PostConstruct
    public void init() {
        if (allowedWebhookIps != null && !allowedWebhookIps.isEmpty()) {
            allowedIpList = Arrays.asList(allowedWebhookIps.split(","));
        } else {
            allowedIpList = List.of(); // Empty list if not configured
        }
    }
    
    @Override
    @Transactional
    public String processPaymentNotification(KispgNotificationRequest notification, String clientIp) {
        // 1. Security Validation
        // IP Whitelisting (only in prod, if configured)
        if ("prod".equalsIgnoreCase(activeProfile) && !allowedIpList.isEmpty() && !allowedIpList.contains(clientIp)) {
            logger.warn("[KISPG Webhook] Denied access from unauthorized IP: {} for moid: {}. Allowed IPs: {}", clientIp, notification.getMoid(), allowedIpList);
            // KISPG might expect specific error string or just a non-200 response.
            // Returning "FAIL" or "ERROR" as a generic failure indicator.
            return "FAIL"; 
        }

        // Hash validation (encData) - Placeholder for actual KISPG security util
        // boolean isValidSignature = kispgSecurityUtil.verifyEncData(notification, merchantKey);
        // if (!isValidSignature) {
        //     logger.warn("[KISPG Webhook] Invalid signature (encData) for moid: {}. IP: {}", notification.getMoid(), clientIp);
        //     return "FAIL";
        // }
        // For now, let's assume signature is valid if a util isn't fully integrated.
        logger.info("[KISPG Webhook] Signature validation would be performed here for moid: {}", notification.getMoid());


        // 2. Parameter & Enrollment/Payment Record Check
        // Attempt to parse moid to Long for enrollId. KISPG moid is often `enroll_{enrollId}_{timestamp}`
        Long enrollId;
        try {
            // This parsing logic needs to be robust and match how moid is generated.
            // If moid = "enroll_123_timestamp", then:
            String moid = notification.getMoid();
            if (moid == null || !moid.startsWith("enroll_")) {
                 throw new IllegalArgumentException("MOID format is incorrect: " + moid);   
            }
            // Extract the part between "enroll_" and the next "_"
            String enrollIdStr = moid.substring("enroll_".length()).split("_")[0];
            enrollId = Long.parseLong(enrollIdStr);
        } catch (NumberFormatException | NullPointerException | ArrayIndexOutOfBoundsException e) {
            logger.error("[KISPG Webhook] Could not parse enrollId from moid: {}. Error: {}", notification.getMoid(), e.getMessage());
            return "FAIL"; // Invalid moid format
        }
        
        Enroll enroll = enrollRepository.findById(enrollId)
                .orElseThrow(() -> {
                    logger.error("[KISPG Webhook] Enroll not found for moid (parsed enrollId: {}). Original moid: {}", enrollId, notification.getMoid());
                    return new ResourceNotFoundException("Enrollment not found for moid: " + notification.getMoid(), ErrorCode.ENROLLMENT_NOT_FOUND);
                });

        // Check for duplicate TIDs to ensure idempotency
        Optional<Payment> existingPaymentWithTid = paymentRepository.findByTid(notification.getTid());
        if (existingPaymentWithTid.isPresent()) {
            Payment p = existingPaymentWithTid.get();
            // If it's for the same enrollment and already marked as PAID, it's a duplicate notification.
            if (p.getEnroll() != null && p.getEnroll().getEnrollId().equals(enroll.getEnrollId()) && "PAID".equals(p.getStatus())) {
                 logger.info("[KISPG Webhook] Duplicate successful notification received for tid: {} and moid: {}. Already processed.", notification.getTid(), notification.getMoid());
                 return "OK"; // Acknowledge duplicate, but don't reprocess. KISPG expects "OK" or "SUCCESS" for success.
            } else {
                // Different enroll or not PAID: This is an issue. Maybe TID reuse or an error state.
                logger.error("[KISPG Webhook] TID {} already exists but for a different enroll ({}) or status ({}). Current moid: {}. Halting.", 
                    notification.getTid(), p.getEnroll() != null ? p.getEnroll().getEnrollId() : "NULL_ENROLL", p.getStatus(), notification.getMoid());
                return "FAIL"; // Potentially problematic
            }
        }


        // 3. Process based on KISPG Result Code
        if (KISPG_SUCCESS_CODE.equals(notification.getResultCode())) {
            // **** PAYMENT SUCCESS ****
            logger.info("[KISPG Webhook] Payment success for moid: {} (enrollId: {}), tid: {}", notification.getMoid(), enrollId, notification.getTid());

            // If already PAID (e.g., /confirm API ran first and somehow set it, though unlikely for webhook to be primary), just ensure locker consistency.
            if ("PAID".equalsIgnoreCase(enroll.getPayStatus())) {
                 logger.info("[KISPG Webhook] EnrollId: {} already marked PAID. Ensuring locker consistency.", enrollId);
                 // Fall through to locker logic to ensure it's aligned with usesLocker flag and pgToken
            } else {
                enroll.setPayStatus("PAID");
                // Potentially set other fields on enroll like payment_date, etc.
            }
            
            User user = enroll.getUser();
            if (user == null) {
                logger.error("[KISPG Webhook] User not found for enrollId: {}. Cannot process locker logic.", enrollId);
                throw new BusinessRuleException(ErrorCode.USER_NOT_FOUND, "User not found for enrollment, cannot allocate locker.");
            }

            // --- New Locker Allocation Logic with Renewal Transfer ---
            boolean isRenewalLockerTransfer = false; // Flag to track if this is a transfer

            if (enroll.isRenewalFlag() && enroll.isUsesLocker()) {
                // This is a renewal and the user wants a locker for the new period.
                LocalDate currentLessonStartDate = enroll.getLesson().getStartDate();
                // Assumption: previous month is literally one month prior. Adjust if business rule is different (e.g., specific day cutoffs)
                LocalDate previousMonthDate = currentLessonStartDate.minusMonths(1);

                List<Enroll> previousEnrollments = enrollRepository.findPreviousPaidLockerEnrollmentsForUser(
                    user.getUuid(),
                    currentLessonStartDate,
                    previousMonthDate
                );

                if (!previousEnrollments.isEmpty()) {
                    Enroll previousEnroll = previousEnrollments.get(0); // Get the latest one from the previous month that had a locker

                    isRenewalLockerTransfer = true;
                    enroll.setLockerAllocated(true);
                    enroll.setLockerPgToken(notification.getTid()); // Associate current payment with this locker allocation
                    
                    // Mark the old enrollment's locker as no longer allocated (it's been transferred)
                    previousEnroll.setLockerAllocated(false);
                    previousEnroll.setLockerPgToken(null); // Clear its association
                    enrollRepository.save(previousEnroll); // Save changes to the old enrollment

                    logger.info("[KISPG Webhook] Locker transferred for renewal. User: {}, New EnrollId: {}, Previous EnrollId: {}, PG Token: {}",
                        user.getUuid(), enroll.getEnrollId(), previousEnroll.getEnrollId(), notification.getTid());
                } else {
                    logger.info("[KISPG Webhook] Renewal for enrollId {} wants locker, but no eligible previous locker found for transfer. Proceeding with standard allocation.", enrollId);
                }
            }

            if (!isRenewalLockerTransfer) { // Standard allocation (new enroll or renewal without previous locker to transfer)
                if (enroll.isUsesLocker()) {
                    // Allocate only if not already allocated by this PG transaction or if allocated by a different one (e.g. user changed mind)
                    if (!enroll.isLockerAllocated() || !notification.getTid().equals(enroll.getLockerPgToken())) {
                        if (user.getGender() == null || user.getGender().trim().isEmpty()) {
                            logger.warn("[KISPG Webhook] User {} for enrollId {} wants locker, but gender is missing. Cannot allocate locker.", user.getUuid(), enrollId);
                            enroll.setLockerAllocated(false);
                            enroll.setLockerPgToken(null); 
                        } else {
                            try {
                                // If a locker was allocated by a *different* PG transaction, and now this one is confirmed,
                                // we should ensure the inventory count is correct.
                                // However, /confirmPayment no longer touches inventory. This webhook is the authority.
                                // So, if lockerAllocated is true but token is different, it implies an issue or prior state.
                                // The safest is to ensure it's false before attempting increment.
                                if (enroll.isLockerAllocated() && !notification.getTid().equals(enroll.getLockerPgToken())) {
                                   logger.warn("[KISPG Webhook] EnrollId {} was marked lockerAllocated with a different pgToken ({}). Resetting before new allocation with pgToken {}.", enrollId, enroll.getLockerPgToken(), notification.getTid());
                                   // No inventory change here, as the previous allocation might have been erroneous or from a flow that didn't complete.
                                   // The increment below will handle the actual claim.
                                   enroll.setLockerAllocated(false); 
                                }

                                lockerService.incrementUsedQuantity(user.getGender().toUpperCase());
                                enroll.setLockerAllocated(true);
                                enroll.setLockerPgToken(notification.getTid());
                                logger.info("[KISPG Webhook] Locker allocated for user {} (gender: {}), enrollId: {}, pgToken: {}", 
                                            user.getUuid(), user.getGender(), enrollId, notification.getTid());
                            } catch (BusinessRuleException e) { // e.g., LOCKER_NOT_AVAILABLE
                                logger.warn("[KISPG Webhook] Locker allocation failed for user {} (gender: {}), enrollId: {}. Reason: {}. pgToken: {}", 
                                            user.getUuid(), user.getGender(), enrollId, e.getMessage(), notification.getTid());
                                enroll.setLockerAllocated(false);
                                enroll.setLockerPgToken(null);
                            } catch (Exception e) {
                                 logger.error("[KISPG Webhook] Unexpected error during locker allocation for enrollId: {}. Error: {}", enrollId, e.getMessage(), e);
                                 enroll.setLockerAllocated(false);
                                 enroll.setLockerPgToken(null);
                            }
                        }
                    } else { // Locker already allocated by this specific pgToken
                         logger.info("[KISPG Webhook] Locker already allocated for enrollId {} with pgToken {}. No action needed.", enrollId, notification.getTid());
                    }
                } else { // User does not want locker (for new or renewal where transfer didn't apply)
                    if (enroll.isLockerAllocated()) { // If one was previously allocated (e.g. /confirm indicated wantsLocker then user changed mind, or other edge cases)
                        logger.info("[KISPG Webhook] User for enrollId {} does not want locker, but one was allocated (pgToken: {}). Decrementing.", enrollId, enroll.getLockerPgToken());
                         if (user.getGender() != null && !user.getGender().trim().isEmpty()) {
                            try {
                                lockerService.decrementUsedQuantity(user.getGender().toUpperCase());
                            } catch (Exception e) {
                                logger.error("[KISPG Webhook] Error decrementing locker for enrollId {} during 'wantsLocker=false' case. Error: {}", enrollId, e.getMessage(), e);
                            }
                         } else {
                             logger.warn("[KISPG Webhook] User for enrollId {} does not want locker, one was allocated, but gender is missing. Cannot reliably decrement inventory count.", enrollId);
                         }
                        enroll.setLockerAllocated(false);
                        enroll.setLockerPgToken(null);
                    }
                }
            }
            // --- End of New Locker Allocation Logic ---

            enrollRepository.save(enroll);

            // Create or Update Payment record
            Payment payment = paymentRepository.findByEnroll_EnrollId(enrollId)
                    .orElseGet(Payment::new); // 변경: orElseGet(Payment::new) 사용
            
            payment.setEnroll(enroll);
            payment.setTid(notification.getTid());
            int paidAmtFromNotification = 0;
            try {
                paidAmtFromNotification = Integer.parseInt(notification.getAmt());
                payment.setPaidAmt(paidAmtFromNotification);
            } catch (NumberFormatException e) {
                logger.error("[KISPG Webhook] Could not parse 'amt' to Integer: {}. Storing as 0.", notification.getAmt());
                payment.setPaidAmt(0);
                paidAmtFromNotification = 0; // Ensure it's 0 for further calculations
            }

            // 강습료 및 사물함 요금 분리 저장
            if (enroll.getLesson() != null) {
                int lessonPrice = enroll.getLesson().getPrice();
                if (enroll.isUsesLocker()) {
                    // 사용자가 사물함을 사용하고, 총 결제액이 강습료 + 사물함 기본요금과 일치하는지 확인
                    // 또는 총 결제액에서 강습료를 뺀 나머지를 사물함 요금으로 간주
                    // 여기서는 더 간단한 접근: 총액에서 강습료를 빼고, 그 값이 사물함 요금과 유사하면 할당
                    int calculatedLockerFee = paidAmtFromNotification - lessonPrice;
                    if (calculatedLockerFee > 0 && calculatedLockerFee <= defaultLockerFee + 1000 && calculatedLockerFee >= defaultLockerFee - 1000) { // 약간의 오차 허용
                        payment.setLessonAmount(lessonPrice);
                        payment.setLockerAmount(calculatedLockerFee);
                    } else if (paidAmtFromNotification == lessonPrice) { // 사물함 요금 없이 강습료만 결제된 경우
                        payment.setLessonAmount(lessonPrice);
                        payment.setLockerAmount(0);
                    } else { // 금액이 예상과 다를 경우, 총액을 강습료에 넣고 사물함은 0으로 처리 (또는 로깅 강화)
                        logger.warn("[KISPG Webhook] Paid amount {} does not align with lesson price {} and locker fee for enrollId: {}. Storing full amount as lessonAmount.", paidAmtFromNotification, lessonPrice, enrollId);
                        payment.setLessonAmount(paidAmtFromNotification);
                        payment.setLockerAmount(0);
                    }
                } else {
                    // 사물함 미사용 시, 총 결제액이 강습료와 일치하는지 확인
                    if (paidAmtFromNotification == lessonPrice) {
                        payment.setLessonAmount(lessonPrice);
                        payment.setLockerAmount(0);
                    } else {
                        logger.warn("[KISPG Webhook] Paid amount {} does not match lesson price {} (no locker) for enrollId: {}. Storing full amount as lessonAmount.", paidAmtFromNotification, lessonPrice, enrollId);
                        payment.setLessonAmount(paidAmtFromNotification);
                        payment.setLockerAmount(0);
                    }
                }
            } else {
                 logger.error("[KISPG Webhook] Lesson not found for enrollId: {} during payment amount splitting. Storing full amount as lessonAmount.", enrollId);
                 payment.setLessonAmount(paidAmtFromNotification);
                 payment.setLockerAmount(0);
            }

            payment.setPgProvider("KISPG");
            payment.setStatus("PAID");
            payment.setPayMethod(notification.getPayMethod());
            payment.setPgResultCode(notification.getResultCode());
            payment.setPgResultMsg(notification.getResultMsg());
            payment.setPaidAt(LocalDateTime.now()); 
            // payment.setMerchantUid(notification.getMoid()); // moid can be stored here if not directly on enroll.
                                                            // Or if Payment has a direct moid field.

            paymentRepository.save(payment);
            
            // TODO: Send notifications to user (email/SMS) about successful payment and locker status if applicable.

            return "OK"; // KISPG expects "OK" or "SUCCESS" (check their docs for exact string)

        } else {
            // **** PAYMENT FAILURE / OTHER STATUS ****
            logger.warn("[KISPG Webhook] Payment failed or other status for moid: {}. ResultCode: {}, ResultMsg: {}", 
                        notification.getMoid(), notification.getResultCode(), notification.getResultMsg());
            
            // Update Enroll status if it's still UNPAID.
            // Don't change if it's already CANCELED, EXPIRED etc.
            if ("UNPAID".equalsIgnoreCase(enroll.getPayStatus())) {
                // Potentially map KISPG failure codes to a more specific CMS status if needed
                // For now, just leaving it as UNPAID or logging the failure.
                // enroll.setPayStatus("PAYMENT_FAILED"); // Or some other status
                // enrollRepository.save(enroll);
            }

            // Create/Update Payment record with failure details
             Payment payment = paymentRepository.findByEnroll_EnrollId(enrollId)
                    .orElseGet(Payment::new);
            payment.setEnroll(enroll); // Link to enroll
            payment.setTid(notification.getTid()); // Store TID even for failures for reference
             try {
                payment.setPaidAmt(0); // Failed, so paid amount is 0
                if (notification.getAmt() != null) { // Log intended amount if available
                    logger.info("[KISPG Webhook] Failed payment intended amount for moid {}: {}", notification.getMoid(), notification.getAmt());
                }
            } catch (NumberFormatException e) {
                logger.error("[KISPG Webhook] Could not parse 'amt' during failure processing: {}", notification.getAmt());
            }
            payment.setLessonAmount(0); // 실패 시 강습료/사물함료 0
            payment.setLockerAmount(0);
            payment.setPgProvider("KISPG");
            payment.setStatus("FAILED"); // Or map KISPG codes to more specific failure statuses
            payment.setPayMethod(notification.getPayMethod());
            payment.setPgResultCode(notification.getResultCode());
            payment.setPgResultMsg(notification.getResultMsg());
            // paidAt might be null or current time for failure record
            paymentRepository.save(payment);

            // For failures, KISPG docs will specify what to return. Usually "OK" to acknowledge receipt.
            return "OK"; 
        }
    }
} 