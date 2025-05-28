package cms.admin.lesson.dto;

import com.fasterxml.jackson.annotation.JsonFormat;
import lombok.Getter;
import lombok.Setter;

import javax.validation.constraints.FutureOrPresent;
import javax.validation.constraints.Min;
import javax.validation.constraints.NotBlank;
import javax.validation.constraints.NotNull;
import java.time.LocalDate;
import java.time.LocalDateTime;

@Getter
@Setter
public class AdminLessonCreateRequestDto {
    @NotBlank(message = "Title is mandatory")
    private String title;

    private String displayName;

    @NotNull(message = "Start date is mandatory")
    @JsonFormat(pattern = "yyyy-MM-dd")
    private LocalDate startDate;

    @NotNull(message = "End date is mandatory")
    @JsonFormat(pattern = "yyyy-MM-dd")
    @FutureOrPresent(message = "End date must be in the present or future")
    private LocalDate endDate;

    @NotNull(message = "Capacity is mandatory")
    @Min(value = 1, message = "Capacity must be at least 1")
    private Integer capacity;

    @NotNull(message = "Price is mandatory")
    @Min(value = 0, message = "Price cannot be negative")
    private Integer price;

    private String instructorName;
    private String lessonTime;
    private String locationName;

    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    private LocalDateTime registrationStartDateTime;

    @NotNull(message = "Registration end date/time is mandatory")
    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    private LocalDateTime registrationEndDateTime;

    // createdBy and createdIp will be set by the system/service layer
} 