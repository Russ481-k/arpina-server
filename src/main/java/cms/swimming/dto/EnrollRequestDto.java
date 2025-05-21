package cms.swimming.dto;

import lombok.*;

import javax.validation.constraints.NotNull;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class EnrollRequestDto {
    @NotNull(message = "강습 ID는 필수입니다")
    private Long lessonId;
} 