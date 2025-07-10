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
public class EnrollAdminResponseDto {
    private Long enrollId;
    private String userId; // 사용자 UUID
    private String userLoginId; // 사용자 로그인 ID (username)
    private String userName;
    private String userPhone;
    private String status; // Enrollment status (APPLIED, CANCELED, EXPIRED)
    private String payStatus; // Payment status (UNPAID, PAID, PARTIAL_REFUNDED, PAYMENT_TIMEOUT)
    private boolean usesLocker;
    private boolean lockerAllocated;
    private String lockerNo;
    private String userGender;
    private LocalDateTime createdAt;
    private LocalDateTime expireDt;
    private Long lessonId;
    private String lessonTitle;
    private String lessonTime;
    private PaymentInfoForEnrollAdmin payment; // Nested Payment Info
    private String membershipType; // Added to display membership/discount type
    private String cancelStatus; // NONE, REQ, APPROVED, DENIED
    private String cancelReason;
    private Integer finalAmount;
    private boolean renewalFlag; // 재수강 여부
    // private Integer remain_days_at_cancel; // 의미 재검토 필요, 일단 제외

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class PaymentInfoForEnrollAdmin {
        private String tid;
        private Integer paidAmt;
        private Integer refundedAmt;
        private String payMethod;
        private LocalDateTime paidAt;
        private boolean isFullRefund;
    }
}