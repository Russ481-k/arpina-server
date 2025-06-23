package cms.payment.service;

import cms.payment.dto.PaymentPageDetailsDto;
import cms.user.domain.User; // User 엔티티 필요 시 (현재는 enrollId로 조회하므로 불필요할 수 있음)
import cms.kispg.dto.KispgCancelResponseDto;
import cms.kispg.dto.KispgNotificationRequest;
import cms.payment.domain.Payment;

public interface PaymentService {
    PaymentPageDetailsDto getPaymentPageDetails(Long enrollId, User currentUser); // 사용자 권한 검증 등을 위해 User 추가

    void confirmPayment(Long enrollId, User currentUser, boolean usesLocker, String pgToken); // pgToken 등 KISPG 결과
                                                                                              // 파라미터 추가 가능

    KispgCancelResponseDto requestCancelPayment(Long paymentId, int cancelAmount, String reason, boolean isPartial);

    Payment createPaymentFromWebhook(KispgNotificationRequest notification);
}