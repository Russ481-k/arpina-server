package cms.admin.lesson.dto;

import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class CloneLessonRequestDto {
    private String newStartDate; // yyyy-MM-dd 형식
    // 필요하다면 다른 복제 시 변경 가능한 필드 추가 (예: 강사, 정원 등)
} 