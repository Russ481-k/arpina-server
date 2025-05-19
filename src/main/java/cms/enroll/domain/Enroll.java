package cms.enroll.domain;

import cms.user.domain.User;
import cms.swimming.domain.Lesson;
import cms.swimming.domain.Locker;
import lombok.Getter;
import lombok.Setter;
import lombok.Builder;
import lombok.NoArgsConstructor;
import lombok.AllArgsConstructor;
import javax.persistence.*;
import java.time.LocalDateTime;
import java.math.BigDecimal;
import org.hibernate.annotations.ColumnDefault;

@Entity
@Table(name = "enroll")
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class Enroll {

    public static enum CancelStatusType {
        NONE, REQ, PENDING, APPROVED, DENIED
    }

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "enroll_id")
    private Long enrollId;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_uuid", referencedColumnName = "uuid", nullable = false)
    private User user;

    @Column(name = "user_name", nullable = false, length = 50)
    private String userName;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "lesson_id", nullable = false)
    private Lesson lesson;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "locker_id")
    private Locker locker;

    @Column(name = "status", nullable = false, length = 20)
    private String status;

    @Column(name = "pay_status", nullable = false, length = 20)
    @ColumnDefault("'UNPAID'")
    private String payStatus;

    @Column(name = "expire_dt", nullable = false)
    private LocalDateTime expireDt;

    @Column(name = "renewal_flag", nullable = false, columnDefinition = "TINYINT(1) DEFAULT 0")
    @ColumnDefault("0")
    private boolean renewalFlag;

    @Enumerated(EnumType.STRING)
    @Column(name = "cancel_status", length = 20)
    @ColumnDefault("'NONE'")
    private CancelStatusType cancelStatus = CancelStatusType.NONE;

    @Column(name = "cancel_reason", length = 100)
    private String cancelReason;

    @Column(name = "cancel_approved_at")
    private LocalDateTime cancelApprovedAt;

    @Column(name = "original_pay_status_before_cancel", length = 20)
    private String originalPayStatusBeforeCancel;

    @Column(name = "refund_amount", precision = 10, scale = 2)
    private BigDecimal refundAmount;

    @Column(name = "locker_zone", length = 50)
    private String lockerZone;

    @Column(name = "locker_carry_over", columnDefinition = "TINYINT(1) DEFAULT 0")
    @ColumnDefault("0")
    private boolean lockerCarryOver;

    @Column(name = "created_at", nullable = false, updatable = false)
    @ColumnDefault("CURRENT_TIMESTAMP")
    private LocalDateTime createdAt;

    @Column(name = "updated_at", nullable = false)
    @ColumnDefault("CURRENT_TIMESTAMP")
    private LocalDateTime updatedAt;

    @Column(name = "created_by", length = 50)
    private String createdBy;

    @Column(name = "created_ip", length = 45)
    private String createdIp;

    @Column(name = "updated_by", length = 50)
    private String updatedBy;

    @Column(name = "updated_ip", length = 45)
    private String updatedIp;

    @PrePersist
    protected void onCreate() {
        LocalDateTime now = LocalDateTime.now();
        this.createdAt = now;
        this.updatedAt = now;
        if (this.payStatus == null) {
            this.payStatus = "UNPAID";
        }
        if (this.cancelStatus == null) {
            this.cancelStatus = CancelStatusType.NONE;
        }
        if (this.renewalFlag == false && this.status == null) {
            this.status = "APPLIED";
        }
    }

    @PreUpdate
    protected void onUpdate() {
        this.updatedAt = LocalDateTime.now();
    }
} 