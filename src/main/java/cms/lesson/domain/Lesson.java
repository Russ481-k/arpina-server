package cms.lesson.domain;

import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.AllArgsConstructor;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@Getter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class Lesson {
    private Long id;
    private String name;
    private String description;
    private BigDecimal price;
    private String period;
    private String time;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
    // Add other relevant fields as necessary for a lesson
} 