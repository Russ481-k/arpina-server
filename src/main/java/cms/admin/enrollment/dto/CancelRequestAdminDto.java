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
    private String userName;
    private String userLoginId; // 추가
    private String userPhone; // 추가
    private String lessonTitle;
    private Long lessonId;
    private String paymentStatus;
    private String cancellationProcessingStatus; // New field for cancel status
    private PaymentDetailsForCancel paymentInfo;

    @Deprecated // 상세 내역 DTO인 calculatedRefundDetails를 사용 권장
    private Integer calculatedRefundAmtByNewPolicy; // 시스템 계산 환불 예상액

    private CalculatedRefundDetailsDto calculatedRefundDetails; // 프론트엔드에서 요청한 상세 환불 내역 필드

    private LocalDateTime requestedAt; // 사용자 요청 시각 또는 취소 처리 요청된 시각
    private String userReason;

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