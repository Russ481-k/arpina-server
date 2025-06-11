package cms.admin.enrollment.dto;

import lombok.Getter;
import lombok.Setter;
import lombok.Data;

@Getter
@Setter
@Data
public class AdminCancelRequestDto {
    private String adminComment;
    private Integer manualUsedDays;
    private Integer finalRefundAmount;
    private Boolean isFullRefund;
}