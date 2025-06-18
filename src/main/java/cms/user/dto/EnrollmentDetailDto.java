package cms.user.dto;

import lombok.Builder;
import lombok.Getter;
import java.time.LocalDateTime;

@Getter
@Builder
public class EnrollmentDetailDto {
    private Long enrollmentId;
    private String lessonTitle;
    private String lessonMonth;
    private String lessonTime;
    private String payStatus;
    private LocalDateTime paymentDate;
}