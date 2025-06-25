package cms.swimming.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Getter
@NoArgsConstructor
@AllArgsConstructor
public class CheckEnrollmentEligibilityDto {

    @Schema(description = "신청 가능 여부", example = "true")
    private boolean eligible;

    @Schema(description = "상태 메시지", example = "수강 신청이 가능합니다.")
    private String message;
}