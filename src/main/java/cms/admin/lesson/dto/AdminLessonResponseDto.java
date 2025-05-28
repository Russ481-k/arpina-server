package cms.admin.lesson.dto;

// import cms.swimming.domain.Lesson.LessonStatus; // 제거
import com.fasterxml.jackson.annotation.JsonFormat;
import lombok.Builder;
import lombok.Getter;
import lombok.Setter;

import java.time.LocalDate;
import java.time.LocalDateTime;

@Getter
@Setter
@Builder
public class AdminLessonResponseDto {
    private Long lessonId;
    private String title;
    private String displayName;

    @JsonFormat(pattern = "yyyy-MM-dd")
    private LocalDate startDate;

    @JsonFormat(pattern = "yyyy-MM-dd")
    private LocalDate endDate;

    private Integer lessonYear; // Generated
    private Integer lessonMonth; // Generated

    private Integer capacity;
    private Integer price;
    // private LessonStatus status; // Enum type directly // 제거
    private String instructorName;
    private String lessonTime;
    private String locationName;

    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    private LocalDateTime registrationStartDateTime;

    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    private LocalDateTime registrationEndDateTime;

    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    private LocalDateTime createdAt;
    private String createdBy;
    private String createdIp;

    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    private LocalDateTime updatedAt;
    private String updatedBy;
    private String updatedIp;

    // Calculated or related data, if needed by admin view
    private Integer currentEnrollmentCount; // Example: number of users enrolled
    private Integer remainingSpots; // Example: calculated
} 