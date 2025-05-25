package cms.admin.enrollment.dto;

import lombok.Data;
import lombok.NoArgsConstructor;
import javax.validation.constraints.Min;

@Data
@NoArgsConstructor
public class ManualUsedDaysRequestDto {
    @Min(value = 0, message = "사용일수는 0 이상이어야 합니다.")
    private Integer manualUsedDays;
} 