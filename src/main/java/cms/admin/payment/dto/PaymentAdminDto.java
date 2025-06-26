package cms.admin.payment.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import java.time.LocalDateTime;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class PaymentAdminDto {
    private Long paymentId;
    private Long enrollId;
    private String userId;
    private String userName;
    private String userPhone;
    private String lessonTitle;
    private String tid; // KISPG Transaction ID
    private Integer paidAmt; // 실제 결제된 금액
    private Integer refundedAmt; // 누적 환불액
    private String status; // PAID, FAILED, CANCELED, PARTIAL_REFUNDED
    private String payMethod;
    private String pgResultCode;
    private LocalDateTime paidAt;
    private LocalDateTime lastRefundDt;
    private String pgProvider;
}