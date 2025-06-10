package cms.admin.enrollment.dto;

import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class ApproveCancelRequestDto {
    private String adminComment;
    private Integer manualUsedDays; // Nullable, if null, system calculates
}