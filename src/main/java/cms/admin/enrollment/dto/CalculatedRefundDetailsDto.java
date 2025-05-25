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
    private BigDecimal lockerUsageDeduction; 

    private BigDecimal lessonPenalty;        
    private BigDecimal lockerPenalty;        

    private BigDecimal finalRefundAmount;    
} 