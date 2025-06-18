package cms.swimming.domain;

import lombok.*;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

import javax.persistence.*;
import java.time.LocalDate;
import java.time.LocalDateTime;

@Entity
@Table(name = "lesson")
@Getter
@Setter
@Builder
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@AllArgsConstructor
public class Lesson {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "lesson_id")
    private Long lessonId;

    @Column(name = "title", nullable = false, length = 100)
    private String title;

    @Column(name = "display_name", length = 150)
    private String displayName;

    @Column(name = "start_date", nullable = false)
    private LocalDate startDate;

    @Column(name = "end_date", nullable = false)
    private LocalDate endDate;

    @Column(name = "capacity", nullable = false)
    private Integer capacity;

    @Column(name = "price", nullable = false)
    private Integer price;

    @Column(name = "instructor_name", length = 50)
    private String instructorName;

    @Column(name = "lesson_time", length = 100)
    private String lessonTime;

    @Column(name = "location_name", length = 100)
    private String locationName;

    @Column(name = "registration_start_datetime")
    private LocalDateTime registrationStartDateTime;

    @Column(name = "registration_end_datetime", nullable = false)
    private LocalDateTime registrationEndDateTime;

    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @UpdateTimestamp
    @Column(name = "updated_at", nullable = false)
    private LocalDateTime updatedAt;

    @Column(name = "created_by", length = 50)
    private String createdBy;

    @Column(name = "created_ip", length = 45)
    private String createdIp;

    @Column(name = "updated_by", length = 50)
    private String updatedBy;

    @Column(name = "updated_ip", length = 45)
    private String updatedIp;

    // 수업 정보 업데이트 메소드
    public void updateDetails(
            String title,
            String displayName,
            LocalDate startDate,
            LocalDate endDate,
            Integer capacity,
            Integer price,
            String instructorName,
            String lessonTime,
            String locationName,
            LocalDateTime registrationStartDateTime,
            LocalDateTime registrationEndDateTime) {
        this.title = title;
        this.displayName = displayName;
        this.startDate = startDate;
        this.endDate = endDate;
        this.capacity = capacity;
        this.price = price;
        this.instructorName = instructorName;
        this.lessonTime = lessonTime;
        this.locationName = locationName;
        this.registrationStartDateTime = registrationStartDateTime;
        this.registrationEndDateTime = registrationEndDateTime;
    }
}