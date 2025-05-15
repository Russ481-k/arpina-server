package cms.schedule.entity;

import lombok.AccessLevel;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

import javax.persistence.*;
import java.time.LocalDateTime;

@Entity
@Table(name = "schedule")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class Schedule {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "schedule_id")
    private Long scheduleId;

    @Column(name = "title", nullable = false)
    private String title;

    @Column(name = "content", columnDefinition = "TEXT")
    private String content;

    @Column(name = "start_date_time", nullable = false)
    private LocalDateTime startDateTime;

    @Column(name = "end_date_time", nullable = false)
    private LocalDateTime endDateTime;

    @Column(name = "display_yn", length = 1)
    private String displayYn;

    @Column(name = "created_by")
    private String createdBy;

    @Column(name = "created_ip")
    private String createdIp;

    @Column(name = "created_date")
    private LocalDateTime createdDate;

    @Column(name = "updated_by")
    private String updatedBy;

    @Column(name = "updated_ip")
    private String updatedIp;

    @Column(name = "updated_date")
    private LocalDateTime updatedDate;

    @Builder
    public Schedule(String title, String content, LocalDateTime startDateTime, LocalDateTime endDateTime,
                   String displayYn, String createdBy, String createdIp) {
        this.title = title;
        this.content = content;
        this.startDateTime = startDateTime;
        this.endDateTime = endDateTime;
        this.displayYn = displayYn;
        this.createdBy = createdBy;
        this.createdIp = createdIp;
        this.createdDate = LocalDateTime.now();
    }

    public void update(String title, String content, LocalDateTime startDateTime, LocalDateTime endDateTime,
                      String displayYn, String updatedBy, String updatedIp) {
        this.title = title;
        this.content = content;
        this.startDateTime = startDateTime;
        this.endDateTime = endDateTime;
        this.displayYn = displayYn;
        this.updatedBy = updatedBy;
        this.updatedIp = updatedIp;
        this.updatedDate = LocalDateTime.now();
    }
} 