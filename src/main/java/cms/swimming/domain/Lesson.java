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
@Builder
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@AllArgsConstructor
public class Lesson {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "lesson_id")
    private Long lessonId;

    @Column(name = "title", nullable = false)
    private String title;

    @Column(name = "start_date", nullable = false)
    private LocalDate startDate;

    @Column(name = "end_date", nullable = false)
    private LocalDate endDate;

    @Column(name = "registration_end_date", nullable = false)
    private LocalDate registrationEndDate;

    @Column(name = "capacity", nullable = false)
    private Integer capacity;

    @Column(name = "price", nullable = false)
    private Integer price;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false)
    private LessonStatus status;

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

    public enum LessonStatus {
        OPEN, // 접수중
        CLOSED, // 접수마감
        ONGOING, // 수강중
        COMPLETED // 수강종료
        // WAIT, FINISHED // Old values, replaced by ONGOING, COMPLETED and a more explicit OPEN/CLOSED distinction
    }

    // 수업 상태 변경 메소드
    public void updateStatus(LessonStatus status) {
        this.status = status;
    }

    // 수업 정보 업데이트 메소드
    public void updateDetails(
            String title, 
            LocalDate startDate, 
            LocalDate endDate, 
            Integer capacity, 
            Integer price,
            LessonStatus status,
            LocalDate registrationEndDate) {
        this.title = title;
        this.startDate = startDate;
        this.endDate = endDate;
        this.capacity = capacity;
        this.price = price;
        this.status = status;
        this.registrationEndDate = registrationEndDate;
    }
} 