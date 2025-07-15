package cms.external.dto;

import cms.enroll.domain.Enroll;
import lombok.Builder;
import lombok.Getter;

import java.time.LocalDateTime;

@Getter
@Builder
public class EnrollmentDetailDto {
    private Long enrollId;
    private String status;
    private String lessonTitle;
    private LocalDateTime applicationDate;

    public static EnrollmentDetailDto from(Enroll enroll) {
        if (enroll == null) {
            return null;
        }
        return EnrollmentDetailDto.builder()
                .enrollId(enroll.getEnrollId())
                .status(enroll.getPayStatus()) // 결제 상태를 대표 상태로 사용
                .lessonTitle(enroll.getLesson() != null ? enroll.getLesson().getTitle() : null)
                .applicationDate(enroll.getCreatedAt())
                .build();
    }
}