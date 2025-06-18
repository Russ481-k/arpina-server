package cms.user.dto;

import lombok.Builder;
import lombok.Getter;
import java.util.List;

@Getter
@Builder
public class UserEnrollmentHistoryDto {
    private Long index;
    private String uuid;
    private String username;
    private String name;
    private String phone;
    private String status;
    private EnrollmentDetailDto lastEnrollment;
    private List<EnrollmentDetailDto> enrollmentHistory;
}