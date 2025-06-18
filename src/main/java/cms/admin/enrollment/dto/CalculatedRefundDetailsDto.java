package cms.admin.enrollment.dto;

import lombok.Builder;
import lombok.Data;
import java.math.BigDecimal;

@Data
@Builder
public class CalculatedRefundDetailsDto {
    private int systemCalculatedUsedDays;
    private Integer manualUsedDays;
    private int effectiveUsedDays;

    private BigDecimal originalLessonPrice;
    private BigDecimal paidLessonAmount;
    private BigDecimal paidLockerAmount;

    private BigDecimal lessonUsageDeduction;
    private BigDecimal lockerDeduction;

    private BigDecimal finalRefundAmount;

    private boolean isFullRefund;

    public static CalculatedRefundDetailsDto createEmpty() {
        return CalculatedRefundDetailsDto.builder()
                .systemCalculatedUsedDays(0)
                .manualUsedDays(null)
                .effectiveUsedDays(0)
                .originalLessonPrice(BigDecimal.ZERO)
                .paidLessonAmount(BigDecimal.ZERO)
                .paidLockerAmount(BigDecimal.ZERO)
                .lessonUsageDeduction(BigDecimal.ZERO)
                .lockerDeduction(BigDecimal.ZERO)
                .finalRefundAmount(BigDecimal.ZERO)
                .isFullRefund(false)
                .build();
    }
}