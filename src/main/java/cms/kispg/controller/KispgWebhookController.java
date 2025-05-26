package cms.kispg.controller;

import cms.kispg.dto.KispgNotificationRequest;
import cms.kispg.service.KispgWebhookService;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import javax.servlet.http.HttpServletRequest;

@RestController
@RequestMapping("/kispg")
@RequiredArgsConstructor
public class KispgWebhookController {

    private static final Logger logger = LoggerFactory.getLogger(KispgWebhookController.class);
    private final KispgWebhookService kispgWebhookService;

    @PostMapping(value = "/payment-notification", consumes = {MediaType.APPLICATION_FORM_URLENCODED_VALUE, MediaType.APPLICATION_JSON_VALUE})
    public ResponseEntity<String> handlePaymentNotification(KispgNotificationRequest notificationRequest, HttpServletRequest request) {
        String clientIp = getClientIp(request);
        logger.info("[KISPG Webhook] Received notification from IP: {}. Payload: {}", clientIp, notificationRequest.toString());

        // Basic validation: Check for essential fields like tid and moid
        if (notificationRequest.getTid() == null || notificationRequest.getTid().trim().isEmpty() ||
            notificationRequest.getMoid() == null || notificationRequest.getMoid().trim().isEmpty()) {
            logger.warn("[KISPG Webhook] Invalid notification: tid or moid is missing. IP: {}", clientIp);
            // KISPG might expect a specific error format or just a non-200 response
            return ResponseEntity.badRequest().body("ERROR: Missing tid or moid");
        }

        try {
            String responseToKispg = kispgWebhookService.processPaymentNotification(notificationRequest, clientIp);
            logger.info("[KISPG Webhook] Processed notification for moid: {}. Responding to KISPG with: {}", notificationRequest.getMoid(), responseToKispg);
            return ResponseEntity.ok(responseToKispg);
        } catch (Exception e) {
            logger.error("[KISPG Webhook] Error processing notification for moid: {}. IP: {}. Error: {}", 
                         notificationRequest.getMoid(), clientIp, e.getMessage(), e);
            // In case of an unexpected error, respond in a way KISPG might retry or log
            // For now, returning a generic error. KISPG docs should specify expected error responses.
            return ResponseEntity.internalServerError().body("INTERNAL_SERVER_ERROR");
        }
    }

    private String getClientIp(HttpServletRequest request) {
        String xForwardedForHeader = request.getHeader("X-Forwarded-For");
        if (xForwardedForHeader == null || xForwardedForHeader.isEmpty()) {
            return request.getRemoteAddr();
        }
        return xForwardedForHeader.split(",")[0].trim();
    }
} 