package cms.external.dto;

import cms.payment.domain.Payment;
import lombok.Builder;
import lombok.Getter;
import lombok.Setter;

import java.time.LocalDateTime;

@Getter
@Setter
@Builder
public class PaymentDetailDto {
    private Long paymentId;
    private String moid;
    private String tid;
    private String status;
    private Integer paidAmount;
    private LocalDateTime paidAt;
    private String payMethod;
    private Integer exportStatus;
    private EnrollmentDetailDto enrollmentInfo;
    private UserDetailDto userInfo;

    public static PaymentDetailDto from(Payment payment) {
        if (payment == null) {
            return null;
        }

        EnrollmentDetailDto enrollmentDetailDto = null;
        UserDetailDto userDetailDto = null;

        if (payment.getEnroll() != null) {
            enrollmentDetailDto = EnrollmentDetailDto.from(payment.getEnroll());
            if (payment.getEnroll().getUser() != null) {
                userDetailDto = UserDetailDto.from(payment.getEnroll().getUser());
            }
        }

        return PaymentDetailDto.builder()
                .paymentId(payment.getId())
                .moid(payment.getMoid())
                .tid(payment.getTid())
                .status(payment.getStatus() != null ? payment.getStatus().name() : null)
                .paidAmount(payment.getPaidAmt())
                .paidAt(payment.getPaidAt())
                .payMethod(payment.getPayMethod())
                .exportStatus(payment.getExportStatus())
                .enrollmentInfo(enrollmentDetailDto)
                .userInfo(userDetailDto)
                .build();
    }
}