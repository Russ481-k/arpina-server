package cms.admin.lesson.dto;

import lombok.Data;
import javax.validation.constraints.NotBlank;

@Data
public class CloneLessonRequestDto {
    @NotBlank(message = "새로운 시작일은 필수입니다.")
    private String newStartDate; // "YYYY-MM-DD"
} 