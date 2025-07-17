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
import io.swagger.v3.oas.annotations.Operation;
import cms.common.util.IpUtil;

import javax.servlet.http.HttpServletRequest;

@RestController
@RequestMapping("/kispg")
@RequiredArgsConstructor
public class KispgWebhookController {

    private static final Logger logger = LoggerFactory.getLogger(KispgWebhookController.class);
    private final KispgWebhookService kispgWebhookService;

    @PostMapping(value = "/payment-notification", consumes = MediaType.APPLICATION_FORM_URLENCODED_VALUE)
    @Operation(summary = "KISPG 결제 결과 통지 (Webhook)", description = "KISPG로부터 결제 결과(성공, 실패, 취소 등)를 비동기적으로 수신합니다.")
    public ResponseEntity<String> handlePaymentNotification(
            KispgNotificationRequest notificationRequest,
            HttpServletRequest request) {

        String clientIp = IpUtil.getClientIp();
        logger.info("=== KISPG Webhook 수신 시작 (IP: {}) ===", clientIp);

        try {
            // 1. 모든 파라미터 로깅 (KISPG에서 어떤 이름으로 오는지 확인)
            logAllRequestParameters(request);

            // 2. KispgNotificationRequest DTO 로깅
            logger.info("📋 KISPG Webhook DTO 데이터:");
            logger.info("  - MID: {}", notificationRequest.getMid());
            logger.info("  - TID: {}", notificationRequest.getTid());
            logger.info("  - MOID: {}", notificationRequest.getMoid());
            logger.info("  - Amt: {}", notificationRequest.getAmt());
            logger.info("  - ResultCode: {}", notificationRequest.getResultCode());
            logger.info("  - ResultMsg: {}", notificationRequest.getResultMsg());
            logger.info("  - PayMethod: {}", notificationRequest.getPayMethod());
            logger.info("  - ApproveNo: {}", notificationRequest.getApproveNo());
            logger.info("  - CardQuota: {}", notificationRequest.getCardQuota());
            logger.info("  - EncData: {}", notificationRequest.getEncData());
            logger.info("  - BuyerName: {}", notificationRequest.getBuyerName());
            // ... 기타 모든 필드 로깅 ...

            // 3. KispgWebhookService 호출
            String responseToKispg = kispgWebhookService.processPaymentNotification(notificationRequest, clientIp);

            logger.info("✅ KISPG Webhook 처리 완료 - 응답: {}", responseToKispg);
            return ResponseEntity.ok(responseToKispg);

        } catch (Exception e) {
            logger.error("❌ KISPG Webhook 처리 중 심각한 오류 발생:", e);
            return ResponseEntity.internalServerError().body("INTERNAL_SERVER_ERROR");
        }
    }

    private void logAllRequestParameters(HttpServletRequest request) {
        logger.info("📋 KISPG Webhook 실제 수신 파라미터 (Raw):");
        request.getParameterMap().forEach((key, values) -> {
            logger.info("  - {} = {}", key, String.join(", ", values));
        });
    }
}