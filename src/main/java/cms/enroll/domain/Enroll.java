package cms.enroll.domain;

import cms.user.domain.User;
// Assuming a Lesson entity might exist or will be created, e.g., in cms.lesson.domain
// import cms.lesson.domain.Lesson;
import lombok.Getter;
import lombok.Setter;
import lombok.Builder;
import lombok.NoArgsConstructor;
import lombok.AllArgsConstructor;
import javax.persistence.*;
import java.time.LocalDateTime;
import java.time.OffsetDateTime;
import org.hibernate.annotations.ColumnDefault;

@Entity
@Table(name = "enroll")
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class Enroll {

    // CancelStatusType enum을 클래스 내부 static public으로 이동
    public static enum CancelStatusType {
        NONE, REQ, PENDING, APPROVED, DENIED
    }

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_uuid", nullable = false)
    private User user;

    // @ManyToOne(fetch = FetchType.LAZY)
    // @JoinColumn(name = "lesson_id", nullable = false)
    // private Lesson lesson; // Placeholder for lesson relationship
    // For now, using lessonId directly as per RenewalRequestDto and to avoid immediate dependency
    @Column(name = "lesson_id", nullable = false)
    private Long lessonId;

    @Column(nullable = false, length = 50)
    private String status; // e.g., UNPAID, PAID, CANCELED, CANCELED_UNPAID

    @Column(name = "expire_dt")
    private LocalDateTime expireDt; // As per DDL in user.md (DATETIME)

    @Column(name = "renewal_flag", columnDefinition = "TINYINT(1) DEFAULT 0")
    @ColumnDefault("0")
    private boolean renewalFlag;

    @Column(name = "uses_locker", nullable = false, columnDefinition = "TINYINT(1) DEFAULT 0")
    private boolean usesLocker = false;

    @Enumerated(EnumType.STRING)
    @Column(name = "cancel_status", length = 20)
    @ColumnDefault("'NONE'")
    private CancelStatusType cancelStatus = CancelStatusType.NONE;

    @Column(name = "cancel_reason", length = 150)
    private String cancelReason;

    @Column(name = "refund_amount")
    private Integer refundAmount;
    
    // Placeholder for locker relationship or direct fields if simple
    // For EnrollDto's locker: { "id": 12, "zone": "여성A", "carryOver": true }
    @Column(name = "locker_id")
    private Integer lockerId;

    @Column(name = "locker_zone", length = 50)
    private String lockerZone;

    @Column(name = "locker_carry_over")
    @ColumnDefault("0")
    private boolean lockerCarryOver;

    // Timestamps
    @Column(name = "created_at", nullable = false, updatable = false)
    @ColumnDefault("CURRENT_TIMESTAMP")
    private LocalDateTime createdAt;

    @Column(name = "updated_at", nullable = false)
    @ColumnDefault("CURRENT_TIMESTAMP")
    private LocalDateTime updatedAt;

    @PrePersist
    protected void onCreate() {
        createdAt = LocalDateTime.now();
        updatedAt = LocalDateTime.now();
        if (cancelStatus == null) {
            cancelStatus = CancelStatusType.NONE; 
        }
    }

    @PreUpdate
    protected void onUpdate() {
        updatedAt = LocalDateTime.now();
    }
} 