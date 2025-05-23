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

    @Column(nullable = false)
    private Integer amount;

    @Column(name = "paid_at") // DTO used OffsetDateTime, using LocalDateTime for DB
    private LocalDateTime paidAt;

    @Column(nullable = false, length = 50)
    private String status; // SUCCESS | CANCELED | PARTIAL | REFUND_REQUESTED (as per PaymentDto and refund flow)
    
    @Column(name = "pg_provider", length = 50)
    private String pgProvider; // From CheckoutDto, good to store with payment

    @Column(name = "pg_token", length = 255) // From /pay request, for verification/refunds
    private String pgToken; 

    @Column(name = "merchant_uid", length = 255) // From CheckoutDto, often used with PG
    private String merchantUid;

    // KISPG specific fields (as per kispg-payment-integration.md and Webhook implementation)
    @Column(name = "tid", length = 100, unique = true) // KISPG Transaction ID, should be unique if it's a primary external ref
    private String tid;

    @Column(name = "paid_amt") // Actual amount confirmed by KISPG
    private Integer paidAmt;

    @Column(name = "pay_method", length = 50) // e.g., CARD, VBANK
    private String payMethod;

    @Column(name = "pg_result_code", length = 20)
    private String pgResultCode;

    @Column(name = "pg_result_msg", length = 255)
    private String pgResultMsg;

    @Column(name = "refunded_amt", columnDefinition = "INT DEFAULT 0")
    private Integer refundedAmt = 0;

    @Column(name = "refund_dt")
    private LocalDateTime refundDt;

    // Timestamps
    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

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