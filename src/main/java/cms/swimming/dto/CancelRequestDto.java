package cms.swimming.dto;

import lombok.*;

import javax.validation.constraints.NotEmpty;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class CancelRequestDto {
    @NotEmpty(message = "취소 사유는 필수입니다")
    private String reason;
} 