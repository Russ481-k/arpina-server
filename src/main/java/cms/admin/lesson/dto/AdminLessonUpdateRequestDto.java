package cms.admin.lesson.dto;

// import cms.swimming.domain.Lesson.LessonStatus;
import com.fasterxml.jackson.annotation.JsonFormat;
import lombok.Getter;
import lombok.Setter;

import javax.validation.constraints.FutureOrPresent;
import javax.validation.constraints.Min;
import java.time.LocalDate;
import java.time.LocalDateTime;

@Getter
@Setter
public class AdminLessonUpdateRequestDto {
    private String title;
    private String displayName;

    @JsonFormat(pattern = "yyyy-MM-dd")
    private LocalDate startDate;

    @JsonFormat(pattern = "yyyy-MM-dd")
    @FutureOrPresent(message = "End date must be in the present or future")
    private LocalDate endDate;

    @Min(value = 1, message = "Capacity must be at least 1")
    private Integer capacity;

    @Min(value = 0, message = "Price cannot be negative")
    private Integer price;

    private String instructorName;
    private String lessonTime;
    private String locationName;

    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    private LocalDateTime registrationStartDateTime;

    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    private LocalDateTime registrationEndDateTime;

    // updatedBy and updatedIp will be set by the system/service layer
} 