package cms.enroll.domain;

import cms.user.domain.User;
import cms.swimming.domain.Lesson;
import lombok.Getter;
import lombok.Setter;
import lombok.Builder;
import lombok.NoArgsConstructor;
import lombok.AllArgsConstructor;
import javax.persistence.*;
import java.time.LocalDateTime;
import org.hibernate.annotations.ColumnDefault;

@Entity
@Table(name = "enroll", indexes = {
        @Index(name = "idx_user_lesson_status", columnList = "user_uuid, lesson_id, status"),
        @Index(name = "idx_lesson_paystatus", columnList = "lesson_id, pay_status"),
        @Index(name = "idx_expire_dt", columnList = "expire_dt")
}, uniqueConstraints = {
        @UniqueConstraint(name = "uk_user_lesson_active", columnNames = { "user_uuid", "lesson_id" }) // DDL에서 이미 존재하는
                                                                                                      // 제약조건
})
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class Enroll {

    @Getter
    public enum CancelStatusType {
        NONE, // 취소 절차 없음
        REQ, // 사용자가 취소 요청
        PENDING, // 시스템 처리중 (사용되지 않을 수 있음)
        APPROVED, // 관리자가 사용자 요청을 승인 (환불 처리 필요)
        DENIED, // 관리자가 취소 거부
        ADMIN_CANCELED // 관리자가 직접 취소 (환불 처리 필요)
    }

    public static enum DiscountStatusType {
        PENDING, APPROVED, DENIED
    }

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id")
    private Long enrollId;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_uuid", referencedColumnName = "uuid", nullable = false)
    private User user;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "lesson_id", nullable = false)
    private Lesson lesson;

    @Column(name = "status", nullable = false, length = 20)
    private String status;

    @Column(name = "pay_status", nullable = false, length = 50)
    @ColumnDefault("'UNPAID'")
    private String payStatus;

    @Column(name = "expire_dt", nullable = true)
    private LocalDateTime expireDt;

    @Column(name = "renewal_flag", nullable = false, columnDefinition = "TINYINT(1) DEFAULT 0")
    @ColumnDefault("0")
    private boolean renewalFlag;

    @Enumerated(EnumType.STRING)
    @Column(name = "cancel_status", length = 20)
    @ColumnDefault("'NONE'")
    private CancelStatusType cancelStatus = CancelStatusType.NONE;

    @Column(name = "cancel_reason", length = 255)
    private String cancelReason;

    @Column(name = "cancel_approved_at")
    private LocalDateTime cancelApprovedAt;

    @Column(name = "original_pay_status_before_cancel", length = 20)
    private String originalPayStatusBeforeCancel;

    @Column(name = "refund_amount")
    private Integer refundAmount;

    @Column(name = "uses_locker", nullable = false, columnDefinition = "TINYINT(1) DEFAULT 0")
    @ColumnDefault("0")
    private boolean usesLocker;

    @Column(name = "locker_allocated", nullable = false, columnDefinition = "TINYINT(1) DEFAULT 0")
    @ColumnDefault("0")
    private boolean lockerAllocated;

    @Column(name = "locker_no", length = 255)
    private String lockerNo;

    @Column(name = "locker_pg_token", length = 100)
    private String lockerPgToken;

    @Column(name = "remain_days")
    private Integer remainDays;

    @Column(name = "discount_type", length = 50)
    private String discountType;

    @Enumerated(EnumType.STRING)
    @Column(name = "discount_status", length = 20)
    @ColumnDefault("'PENDING'")
    private DiscountStatusType discountStatus = DiscountStatusType.PENDING;

    @Column(name = "discount_approved_at")
    private LocalDateTime discountApprovedAt;

    @Column(name = "discount_admin_comment", length = 255)
    private String discountAdminComment;

    @Column(name = "cancel_requested_at")
    private LocalDateTime cancelRequestedAt;

    @Column(name = "days_used_for_refund")
    private Integer daysUsedForRefund;

    @Column(name = "CREATED_AT", nullable = false, updatable = false)
    @ColumnDefault("CURRENT_TIMESTAMP")
    private LocalDateTime createdAt;

    @Column(name = "UPDATED_AT", nullable = false)
    @ColumnDefault("CURRENT_TIMESTAMP")
    private LocalDateTime updatedAt;

    @Column(name = "CREATED_BY", length = 36)
    private String createdBy;

    @Column(name = "CREATED_IP", length = 45)
    private String createdIp;

    @Column(name = "UPDATED_BY", length = 36)
    private String updatedBy;

    @Column(name = "UPDATED_IP", length = 45)
    private String updatedIp;

    @Column(name = "final_amount")
    private Integer finalAmount;

    @Column(name = "discount_applied_percentage")
    private Integer discountAppliedPercentage;

    @Enumerated(EnumType.STRING)
    @Column(name = "membership_type", length = 50, nullable = false)
    @ColumnDefault("'GENERAL'")
    private cms.enroll.domain.MembershipType membershipType = cms.enroll.domain.MembershipType.GENERAL;

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