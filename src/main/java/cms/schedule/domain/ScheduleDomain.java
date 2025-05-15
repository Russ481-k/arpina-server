package cms.schedule.domain;

import com.fasterxml.jackson.databind.JsonNode;
import lombok.*;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.Type;
import org.hibernate.annotations.UpdateTimestamp;

import javax.persistence.*;
import java.time.LocalDateTime;

@Entity
@Table(name = "schedule", 
    uniqueConstraints = @UniqueConstraint(
        name = "uk_schedule_unique",
        columnNames = {"start_time", "end_time", "title"}
    )
)
@Getter
@Builder
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@AllArgsConstructor
public class ScheduleDomain {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "schedule_id")
    private Long scheduleId;

    @Column(name = "title", nullable = false)
    private String title;

    @Column(name = "description_html", columnDefinition = "MEDIUMTEXT")
    private String descriptionHtml;

    @Column(name = "start_time", nullable = false)
    private LocalDateTime startTime;

    @Column(name = "end_time", nullable = false)
    private LocalDateTime endTime;

    @Column(name = "place", length = 100)
    private String place;

    @Column(name = "display_yn", nullable = false, length = 1)
    private String displayYn;

    @Column(name = "color", length = 7)
    private String color;

    @Column(name = "extra", columnDefinition = "JSON")
    @Type(type = "com.vladmihalcea.hibernate.type.json.JsonNodeBinaryType")
    private JsonNode extra;

    @Column(name = "created_by", length = 36)
    private String createdBy;

    @Column(name = "created_ip", length = 45)
    private String createdIp;

    @CreationTimestamp
    @Column(name = "created_at", updatable = false)
    private LocalDateTime createdAt;

    @Column(name = "updated_by", length = 36)
    private String updatedBy;

    @Column(name = "updated_ip", length = 45)
    private String updatedIp;

    @UpdateTimestamp
    @Column(name = "updated_at")
    private LocalDateTime updatedAt;

    public void update(String title, String descriptionHtml, LocalDateTime startTime,
                      LocalDateTime endTime, String place, String displayYn,
                      String color, JsonNode extra, String updatedBy, String updatedIp) {
        this.title = title;
        this.descriptionHtml = descriptionHtml;
        this.startTime = startTime;
        this.endTime = endTime;
        this.place = place;
        this.displayYn = displayYn;
        this.color = color;
        this.extra = extra;
        this.updatedBy = updatedBy;
        this.updatedIp = updatedIp;
    }

    public void setDisplayYn(String displayYn, String updatedBy, String updatedIp) {
        this.displayYn = displayYn;
        this.updatedBy = updatedBy;
        this.updatedIp = updatedIp;
    }
} 