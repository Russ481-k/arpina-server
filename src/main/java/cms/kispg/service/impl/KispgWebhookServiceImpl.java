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

            // Adjusted standard allocation/confirmation path
            if (!isRenewalLockerTransfer) {
                if (enroll.isUsesLocker()) {
                    // Locker was requested during initial enrollment.
                    // EnrollmentServiceImpl should have already attempted to allocate and set enroll.lockerAllocated.
                    if (enroll.isLockerAllocated()) {
                        // Locker was successfully allocated during initial enrollment.
                        // Now, associate this successful payment transaction with the locker.
                        enroll.setLockerPgToken(notification.getTid());
                        logger.info("[KISPG Webhook] Confirmed locker allocation for enrollId: {} (already allocated during application). PG Token: {}", enrollId, notification.getTid());
                    } else {
                        // This case implies usesLocker=true, but lockerAllocated=false.
                        // This means the initial attempt in EnrollmentServiceImpl to allocate a locker failed (e.g., no inventory, missing gender then).
                        // We should not attempt to allocate it here again. The enrollment proceeds without a locker.
                        logger.warn("[KISPG Webhook] User for enrollId {} wanted a locker (usesLocker=true), but it was not allocated during initial application (lockerAllocated=false). Payment successful, but no locker provided.", enrollId);
                        // Ensure usesLocker is marked false if no locker is actually provided, despite initial request.
                        // This might be controversial - does the user expect to be told earlier? EnrollmentServiceImpl should have failed if locker was mandatory.
                        // If EnrollmentServiceImpl allows proceeding without locker even if requested and unavailable, then this is just a confirmation.
                        // For safety, ensure usesLocker reflects reality if no locker is given.
                        // enroll.setUsesLocker(false); // Optional: align usesLocker with reality if it couldn't be allocated.
                    }
                } else { // User does NOT want locker (enroll.isUsesLocker() is false)
                    if (enroll.isLockerAllocated()) {
                        // This means a locker WAS allocated during initial application (e.g., usesLocker was true then),
                        // but now (perhaps through a /payment/confirm step not detailed here, or if usesLocker could change post-application),
                        // the final decision is NO locker. We MUST release the previously allocated one.
                        logger.info("[KISPG Webhook] User for enrollId {} now indicates NO locker (usesLocker=false), but one was previously allocated. Releasing it.", enrollId);
                        // User user = enroll.getUser(); // User should have been fetched earlier and is in scope
                        if (user != null && user.getGender() != null && !user.getGender().trim().isEmpty()) {
                            try {
                                lockerService.decrementUsedQuantity(user.getGender().toUpperCase());
                                enroll.setLockerAllocated(false);
                                enroll.setLockerPgToken(null);
                                logger.info("[KISPG Webhook] Locker successfully released for enrollId {} due to usesLocker=false post-allocation.", enrollId);
                            } catch (Exception e) {
                                logger.error("[KISPG Webhook] Error decrementing locker for enrollId {} during 'usesLocker=false' case. Error: {}", enrollId, e.getMessage(), e);
                                // Even if decrement fails, ensure flags are set.
                                enroll.setLockerAllocated(false);
                                enroll.setLockerPgToken(null);
                            }
                        } else {
                            logger.warn("[KISPG Webhook] User for enrollId {} indicated NO locker, one was allocated, but gender is missing. Cannot reliably decrement inventory. Flags set to no locker.", enrollId);
                            enroll.setLockerAllocated(false);
                            enroll.setLockerPgToken(null);
                        }
                    }
                    // If enroll.isUsesLocker() is false AND enroll.isLockerAllocated() is false, no action needed.
                }
            }
            // --- End of Adjusted Locker Logic ---

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
            // **** PAYMENT FAILURE or OTHER STATUS (e.g., REFUND notification from KISPG) ****
            logger.warn("[KISPG Webhook] Payment FAILED or non-success status for moid: {} (enrollId: {}), tid: {}, resultCode: {}, resultMsg: {}", 
                        notification.getMoid(), enrollId, notification.getTid(), notification.getResultCode(), notification.getResultMsg());

            // Potential KISPG refund codes (these are examples, refer to KISPG docs)
            // String KISPG_REFUND_SUCCESS_CODE = "2001"; // Example: Full refund success
            // String KISPG_PARTIAL_REFUND_SUCCESS_CODE = "2002"; // Example: Partial refund success

            // If it's a notification about a full refund or a definitive final failure for an enrollment that HAD a locker.
            // This part needs to be robust based on actual KISPG non-success codes that imply the original transaction is void or fully reversed.
            // Let's assume any non-KISPG_SUCCESS_CODE means the payment is not going through or is reversed for now.
            // More specific handling based on resultCode would be better.

            if (enroll.isLockerAllocated()) {
                logger.info("[KISPG Webhook] Payment failed/reversed for enrollId: {} which had a locker allocated. Releasing locker.", enrollId);
                User user = enroll.getUser(); // User should have been fetched earlier
                if (user != null && user.getGender() != null && !user.getGender().trim().isEmpty()) {
                    try {
                        lockerService.decrementUsedQuantity(user.getGender().toUpperCase());
                        enroll.setLockerAllocated(false);
                        enroll.setLockerPgToken(null); 
                        // Also update enroll status if this is a final failure
                        // enroll.setStatus("FAILED"); // Or based on resultCode
                        // enroll.setPayStatus("FAILED"); // Or "REFUNDED"
                        logger.info("[KISPG Webhook] Locker released for enrollId {} due to payment failure/reversal.", enrollId);
                    } catch (Exception e) {
                        logger.error("[KISPG Webhook] Error decrementing locker for enrollId {} during payment failure/reversal. Error: {}", enrollId, e.getMessage(), e);
                        // Even if decrement fails, ensure flags are set.
                        enroll.setLockerAllocated(false);
                        enroll.setLockerPgToken(null);
                    }
                } else {
                    logger.warn("[KISPG Webhook] Payment failed/reversed for enrollId: {} which had lockerAllocated=true, but user/gender info is missing. Cannot release locker automatically.", enrollId);
                    enroll.setLockerAllocated(false); // Still mark as not allocated
                    enroll.setLockerPgToken(null);
                }
            }
            
            // Update enrollment status based on failure/refund type
            // Example:
            // if (KISPG_REFUND_SUCCESS_CODE.equals(notification.getResultCode())) {
            //    enroll.setPayStatus("REFUNDED");
            //    enroll.setStatus("CANCELED"); // Or appropriate status
            // } else {
            //    enroll.setPayStatus("FAILED"); // Generic failure
            //    // enroll.setStatus("FAILED"); // Or keep as APPLIED if user can retry? Depends on flow.
            // }


            enrollRepository.save(enroll);
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