package cms.kispg.service;

import cms.kispg.dto.KispgNotificationRequest;

public interface KispgWebhookService {
    /**
     * KISPG 결제 결과 통지(Webhook)를 처리합니다.
     * @param notificationRequest KISPG로부터 받은 알림 데이터
     * @param clientIp 요청 IP 주소 (로깅 및 보안 검증용)
     * @return KISPG에 응답할 문자열 (예: "OK", "SUCCESS")
     */
    String processPaymentNotification(KispgNotificationRequest notificationRequest, String clientIp);
} 