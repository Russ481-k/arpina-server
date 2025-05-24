package cms.user.domain;

import lombok.*;
import javax.persistence.*;
import java.time.LocalDateTime;

@Entity
@Table(name = "user_memo")
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class UserMemo {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "memo_id")
    private Long memoId;

    // User 엔티티와 직접적인 관계 대신 user_uuid를 저장하여 유연성 확보
    @Column(name = "user_uuid", nullable = false, length = 36)
    private String userUuid;

    @Lob // 많은 텍스트를 저장할 수 있도록
    @Column(name = "memo_content")
    private String memoContent;

    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @Column(name = "updated_at", nullable = false)
    private LocalDateTime updatedAt;

    @Column(name = "updated_by_admin_id", length = 50) // 관리자 ID 또는 식별자
    private String updatedByAdminId;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_uuid", referencedColumnName = "uuid", insertable = false, updatable = false)
    private User user; // 조회용 User 객체 (선택적)

    @PrePersist
    protected void onCreate() {
        LocalDateTime now = LocalDateTime.now();
        this.createdAt = now;
        this.updatedAt = now;
    }

    @PreUpdate
    protected void onUpdate() {
        this.updatedAt = LocalDateTime.now();
    }
} 