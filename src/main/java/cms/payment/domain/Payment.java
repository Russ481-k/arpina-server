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