package cms.swimming.dto;

import cms.swimming.domain.Lesson;
import com.fasterxml.jackson.annotation.JsonFormat;
import lombok.*;

import java.time.LocalDate;
import java.time.LocalDateTime;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class LessonDto {
    private Long lessonId;
    private String title;
    private String displayName; // New

    @JsonFormat(pattern = "yyyy-MM-dd")
    private LocalDate startDate;

    @JsonFormat(pattern = "yyyy-MM-dd")
    private LocalDate endDate;

    // registrationEndDate is replaced by registrationEndDateTime
    // @JsonFormat(pattern = "yyyy-MM-dd")
    // private LocalDate registrationEndDate;

    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss") // New
    private LocalDateTime registrationStartDateTime;

    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss") // New, type changed
    private LocalDateTime registrationEndDateTime;

    private Integer capacity;
    private Integer price; // Lesson entity has Integer, matching here.
    private String instructorName;
    private String lessonTime; // Full original lesson time string
    private String locationName; // New

    // Optional: Fields for parsed time components and remaining spots, if this DTO
    // needs to be self-contained for frontend
    // These would typically be populated by the service layer during the mapping
    // from the Lesson entity
    private String days; // e.g., "(월,수,금)"
    private String timePrefix; // e.g., "오전"
    private String timeSlot; // e.g., "09:00 ~ 09:50"
    private Integer remaining; // Calculated remaining spots

    // toEntity() method might not be relevant if this DTO is purely for responses.
    // If it were for requests, it would need to include all new settable fields.
    // For now, commenting out as its current form is incomplete for new Lesson
    // entity.
    /*
     * public Lesson toEntity() {
     * return Lesson.builder()
     * .title(title)
     * // ... other fields ...
     * .build();
     * }
     */

    // Static factory method to convert from Lesson entity to LessonDto
    // This method will be more complex now and might require access to other
    // services/repositories (e.g., for remaining spots)
    // Or, the service layer handles complex mapping and this DTO is simpler.
    // For now, let's assume the service layer will populate all fields, including
    // calculated/parsed ones.
    public static LessonDto fromEntity(Lesson lesson, Integer remainingSpots, String days, String timePrefix,
            String timeSlot) {
        return LessonDto.builder()
                .lessonId(lesson.getLessonId())
                .title(lesson.getTitle())
                .displayName(lesson.getDisplayName()) // From new field in Lesson.java
                .startDate(lesson.getStartDate())
                .endDate(lesson.getEndDate())
                .registrationStartDateTime(lesson.getRegistrationStartDateTime()) // From new field
                .registrationEndDateTime(lesson.getRegistrationEndDateTime()) // From new field (type changed)
                .capacity(lesson.getCapacity())
                .price(lesson.getPrice())
                .instructorName(lesson.getInstructorName())
                .lessonTime(lesson.getLessonTime())
                .locationName(lesson.getLocationName()) // From new field
                .days(days) // Parsed by service
                .timePrefix(timePrefix) // Parsed by service
                .timeSlot(timeSlot) // Parsed by service
                .remaining(remainingSpots) // Calculated by service
                .build();
    }
}