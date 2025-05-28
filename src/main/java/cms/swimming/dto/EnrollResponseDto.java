package cms.swimming.dto;

import com.fasterxml.jackson.annotation.JsonFormat;
import com.fasterxml.jackson.annotation.JsonInclude;
import lombok.*;

import java.time.LocalDateTime;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
@JsonInclude(JsonInclude.Include.NON_NULL)
public class EnrollResponseDto {
    private Long enrollId;
    private String userId;
    private String userName;
    private String status; // APPLIED, CANCELED, PENDING, COMPLETED, EXPIRED
    private String payStatus; // UNPAID, PAID, REFUNDED, EXPIRED
    
    @JsonFormat(pattern = "yyyy-MM-dd'T'HH:mm:ss")
    private LocalDateTime createdAt;
    
    private Long lessonId;
    private String lessonTitle;
    private Integer lessonPrice; // Base price of the lesson
    private Integer finalAmount; // Calculated final amount after discounts and locker
    private String membershipType; // String value of the membership type used
    private boolean usesLocker; // Added to indicate if a locker was assigned

    @JsonFormat(pattern = "yyyy-MM-dd'T'HH:mm:ss")
    private LocalDateTime expireDt; // Added for initial enrollment response

    private boolean renewalFlag;
    private String cancelStatus;
    private String cancelReason;
} 