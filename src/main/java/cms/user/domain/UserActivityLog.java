package cms.user.domain;

import javax.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.Setter;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

import java.time.LocalDateTime;

@Entity
@Table(name = "user_activity_log")
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class UserActivityLog {
    @Id
    @Column(name = "uuid", nullable = false)
    private String uuid;

    @Column(name = "user_uuid", nullable = false)
    private String userUuid;

    @Column(name = "group_id", nullable = true)
    private String groupId;

    @Column(name = "organization_id", nullable = false)
    private String organizationId;

    @Column(name = "activity_type", nullable = false)
    private String activityType;

    @Column(name = "description", nullable = false)
    private String description;

    @Column(name = "user_agent")
    private String userAgent;

    @Column(name = "created_by")
    private String createdBy;

    @Column(name = "created_ip")
    private String createdIp;

    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @Column(name = "updated_by")
    private String updatedBy;

    @Column(name = "updated_ip")
    private String updatedIp;

    @UpdateTimestamp
    @Column(name = "updated_at", nullable = false)
    private LocalDateTime updatedAt;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_uuid", referencedColumnName = "uuid", insertable = false, updatable = false)
    private User user;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "group_id", referencedColumnName = "uuid", insertable = false, updatable = false, foreignKey = @ForeignKey(value = ConstraintMode.NO_CONSTRAINT))
    private Group group;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "organization_id", referencedColumnName = "uuid", insertable = false, updatable = false)
    private Organization organization;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "created_by", referencedColumnName = "uuid", insertable = false, updatable = false)
    private User creator;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "updated_by", referencedColumnName = "uuid", insertable = false, updatable = false)
    private User updater;

    // 생성 메서드
    public static UserActivityLog createLog(
            String uuid,
            String userUuid,
            String groupId,
            String organizationId,
            String activityType,
            String description,
            String userAgent,
            String createdBy,
            String createdIp) {
        UserActivityLog log = new UserActivityLog();
        log.uuid = uuid;
        log.userUuid = userUuid;
        log.groupId = groupId;
        log.organizationId = organizationId;
        log.activityType = activityType;
        log.description = description;
        log.userAgent = userAgent;
        log.createdBy = createdBy;
        log.createdIp = createdIp;
        return log;
    }
} 