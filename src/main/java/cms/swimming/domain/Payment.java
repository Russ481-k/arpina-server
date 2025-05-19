package cms.swimming.domain;

import lombok.*;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

import javax.persistence.*;
import java.time.LocalDateTime;

@Entity
@Table(name = "payment")
@Getter
@Builder
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@AllArgsConstructor
public class Payment {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "payment_id")
    private Long paymentId;

    @OneToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "enroll_id", nullable = false)
    private Enroll enroll;

    @Column(name = "tid", nullable = false)
    private String tid; // PG사 거래 ID

    @Column(name = "amount", nullable = false)
    private Integer amount;

    @Column(name = "refund_amount")
    private Integer refundAmount;

    @Column(name = "refund_dt")
    private LocalDateTime refundDt;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false)
    private PaymentStatus status;

    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @UpdateTimestamp
    @Column(name = "updated_at", nullable = false)
    private LocalDateTime updatedAt;

    @Column(name = "created_by", length = 50)
    private String createdBy;

    @Column(name = "created_ip", length = 45)
    private String createdIp;

    @Column(name = "updated_by", length = 50)
    private String updatedBy;

    @Column(name = "updated_ip", length = 45)
    private String updatedIp;

    public enum PaymentStatus {
        PAID, CANCELED, PARTIAL_REFUNDED
    }

    // 전액 환불 처리
    public void cancelPayment(String updatedBy, String updatedIp) {
        this.status = PaymentStatus.CANCELED;
        this.refundAmount = this.amount;
        this.refundDt = LocalDateTime.now();
        this.updatedBy = updatedBy;
        this.updatedIp = updatedIp;
    }

    // 부분 환불 처리
    public void partialRefund(Integer refundAmount, String updatedBy, String updatedIp) {
        if (refundAmount > 0 && refundAmount < this.amount) {
            this.status = PaymentStatus.PARTIAL_REFUNDED;
            this.refundAmount = refundAmount;
            this.refundDt = LocalDateTime.now();
            this.updatedBy = updatedBy;
            this.updatedIp = updatedIp;
        }
    }
} 