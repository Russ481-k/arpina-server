package cms.payment.domain;

import cms.enroll.domain.Enroll;
import lombok.Getter;
import lombok.Setter;
import lombok.Builder;
import lombok.NoArgsConstructor;
import lombok.AllArgsConstructor;
import javax.persistence.*;
import java.time.LocalDateTime;

@Entity
@Table(name = "payment")
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class Payment {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "enroll_id", nullable = false)
    private Enroll enroll;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 50)
    private PaymentStatus status;

    @Column(name = "paid_at")
    private LocalDateTime paidAt;

    // KISPG specific fields
    @Column(name = "moid", length = 255)
    private String moid;

    @Column(name = "tid", length = 100, unique = true)
    private String tid;

    @Column(name = "paid_amt") // Primary amount field
    private Integer paidAmt;

    // Payment breakdown
    @Column(name = "lesson_amount")
    private Integer lessonAmount;

    @Column(name = "locker_amount")
    private Integer lockerAmount;

    // Refund related
    @Column(name = "refunded_amt", columnDefinition = "INT DEFAULT 0")
    private Integer refundedAmt = 0;

    @Column(name = "refund_dt")
    private LocalDateTime refundDt;

    // Payment method info
    @Column(name = "pay_method", length = 50)
    private String payMethod;

    @Column(name = "pg_result_code", length = 20)
    private String pgResultCode;

    @Column(name = "pg_result_msg", length = 255)
    private String pgResultMsg;

    // Audit fields
    @Column(name = "created_by", length = 36)
    private String createdBy;

    @Column(name = "created_ip", length = 45)
    private String createdIp;

    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @Column(name = "updated_by", length = 36)
    private String updatedBy;

    @Column(name = "updated_ip", length = 45)
    private String updatedIp;

    @Column(name = "updated_at", nullable = false)
    private LocalDateTime updatedAt;

    @PrePersist
    protected void onCreate() {
        createdAt = LocalDateTime.now();
        updatedAt = LocalDateTime.now();
    }

    @PreUpdate
    protected void onUpdate() {
        updatedAt = LocalDateTime.now();
    }
}