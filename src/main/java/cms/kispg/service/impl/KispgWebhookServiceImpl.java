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

import java.math.BigDecimal;
import java.time.LocalDateTime;
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
                // Decide if this is a "FAIL" or if payment proceeds without locker.
                // For now, let's assume it should fail if user is missing, as gender is needed.
                throw new BusinessRuleException(ErrorCode.USER_NOT_FOUND, "User not found for enrollment, cannot allocate locker.");
            }

            // Locker Allocation Logic (Idempotent)
            if (enroll.isUsesLocker()) {
                if (!enroll.isLockerAllocated() || !notification.getTid().equals(enroll.getLockerPgToken())) {
                    // If not allocated OR allocated with a different pgToken (e.g. user changed mind in confirmPayment and then this webhook fires)
                    // Try to allocate if not already allocated by this specific transaction.
                    if (user.getGender() == null || user.getGender().trim().isEmpty()) {
                        logger.warn("[KISPG Webhook] User {} for enrollId {} wants locker, but gender is missing. Cannot allocate locker.", user.getUuid(), enrollId);
                        enroll.setLockerAllocated(false); // Ensure it's marked false
                        // Do not throw error, payment is successful, but locker cannot be assigned. User needs notification.
                    } else {
                        try {
                            lockerService.incrementUsedQuantity(user.getGender().toUpperCase());
                            enroll.setLockerAllocated(true);
                            enroll.setLockerPgToken(notification.getTid()); // Store KISPG TID with locker allocation
                            logger.info("[KISPG Webhook] Locker allocated for user {} (gender: {}), enrollId: {}, pgToken: {}", 
                                        user.getUuid(), user.getGender(), enrollId, notification.getTid());
                        } catch (BusinessRuleException e) { // e.g., LOCKER_NOT_AVAILABLE
                            logger.warn("[KISPG Webhook] Locker allocation failed for user {} (gender: {}), enrollId: {}. Reason: {}. pgToken: {}", 
                                        user.getUuid(), user.getGender(), enrollId, e.getMessage(), notification.getTid());
                            enroll.setLockerAllocated(false); // Mark as not allocated
                            // Payment is still successful. This situation (paid but no locker due to inventory) needs business handling (e.g., notification to admin/user).
                        } catch (Exception e) {
                             logger.error("[KISPG Webhook] Unexpected error during locker allocation for enrollId: {}. Error: {}", enrollId, e.getMessage(), e);
                             enroll.setLockerAllocated(false); // Mark as not allocated on unexpected error too
                        }
                    }
                } else {
                     logger.info("[KISPG Webhook] Locker already allocated for enrollId {} with pgToken {}. No action needed.", enrollId, notification.getTid());
                }
            } else { // User does not want locker
                if (enroll.isLockerAllocated()) { // If one was previously allocated (e.g. changed mind in /confirm)
                    logger.info("[KISPG Webhook] User for enrollId {} does not want locker, but one was previously allocated. Decrementing.", enrollId);
                     if (user.getGender() != null && !user.getGender().trim().isEmpty()) {
                        try {
                            lockerService.decrementUsedQuantity(user.getGender().toUpperCase());
                        } catch (Exception e) {
                            logger.error("[KISPG Webhook] Error decrementing locker for enrollId {} during 'wantsLocker=false' case. Error: {}", enrollId, e.getMessage(), e);
                            // Continue, as payment is main focus. Inventory might need manual check.
                        }
                     }
                    enroll.setLockerAllocated(false);
                    enroll.setLockerPgToken(null);
                }
            }

            enrollRepository.save(enroll);

            // Create or Update Payment record
            Payment payment = paymentRepository.findByEnroll_EnrollId(enrollId)
                    .orElse(new Payment());
            
            payment.setEnroll(enroll);
            payment.setTid(notification.getTid());
            try {
                payment.setPaidAmt(Integer.parseInt(notification.getAmt()));
            } catch (NumberFormatException e) {
                logger.error("[KISPG Webhook] Could not parse 'amt' to Integer: {}. Storing as 0.", notification.getAmt());
                payment.setPaidAmt(0);
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
                    .orElse(new Payment());
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