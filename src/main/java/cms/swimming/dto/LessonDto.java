package cms.swimming.dto;

import cms.swimming.domain.Lesson;
import com.fasterxml.jackson.annotation.JsonFormat;
import lombok.*;

import java.time.LocalDate;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class LessonDto {
    private Long lessonId;
    private String title;
    
    @JsonFormat(pattern = "yyyy-MM-dd")
    private LocalDate startDate;
    
    @JsonFormat(pattern = "yyyy-MM-dd")
    private LocalDate endDate;
    
    private Integer capacity;
    private Integer maleLockerCap;
    private Integer femaleLockerCap;
    private Integer price;
    private String status; // OPEN, CLOSED, FINISHED

    // 도메인 객체로 변환하는 메소드
    public Lesson toEntity() {
        return Lesson.builder()
                .title(title)
                .startDate(startDate)
                .endDate(endDate)
                .capacity(capacity)
                .maleLockerCap(maleLockerCap)
                .femaleLockerCap(femaleLockerCap)
                .price(price)
                .status(Lesson.LessonStatus.valueOf(status))
                .build();
    }

    // 도메인 객체에서 DTO로 변환하는 메소드
    public static LessonDto fromEntity(Lesson lesson) {
        return LessonDto.builder()
                .lessonId(lesson.getLessonId())
                .title(lesson.getTitle())
                .startDate(lesson.getStartDate())
                .endDate(lesson.getEndDate())
                .capacity(lesson.getCapacity())
                .maleLockerCap(lesson.getMaleLockerCap())
                .femaleLockerCap(lesson.getFemaleLockerCap())
                .price(lesson.getPrice())
                .status(lesson.getStatus().name())
                .build();
    }
} 