package cms.admin.enrollment.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import java.time.LocalDateTime;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class CancelRequestAdminDto {
    private Long enrollId; // Using enrollId as the primary identifier for the request
    private String userId;
    private String userName;
    private String lessonTitle;
    private PaymentDetailsForCancel paymentInfo;
    private Integer calculatedRefundAmtByNewPolicy; // 시스템 계산 환불 예상액
    private LocalDateTime requestedAt; // 사용자 요청 시각 또는 취소 처리 요청된 시각
    private String userReason;
    private String adminComment;

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class PaymentDetailsForCancel {
        private String tid;
        private Integer paidAmt; // 원 결제액
        private Integer lessonPaidAmt; // 강습료 부분
        private Integer lockerPaidAmt; // 사물함료 부분 (있었다면)
    }
} 