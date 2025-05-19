package cms.swimming.dto;

import cms.swimming.domain.Enroll;
import com.fasterxml.jackson.annotation.JsonFormat;
import lombok.*;

import java.time.LocalDateTime;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class EnrollResponseDto {
    private Long enrollId;
    private Long userId;
    private String userName;
    private String status; // APPLIED, CANCELED, PENDING
    
    @JsonFormat(pattern = "yyyy-MM-dd'T'HH:mm:ss")
    private LocalDateTime createdAt;
    
    private Long lessonId;
    private String lessonTitle;
    private Long lockerId;
    private String lockerNumber;
    private String lockerGender; // M, F

    // 도메인 객체에서 DTO로 변환하는 메소드
    public static EnrollResponseDto fromEntity(Enroll enroll) {
        return EnrollResponseDto.builder()
                .enrollId(enroll.getEnrollId())
                .userId(enroll.getUserId())
                .userName(enroll.getUserName())
                .status(enroll.getStatus().name())
                .createdAt(enroll.getCreatedAt())
                .lessonId(enroll.getLesson().getLessonId())
                .lessonTitle(enroll.getLesson().getTitle())
                .lockerId(enroll.getLocker() != null ? enroll.getLocker().getLockerId() : null)
                .lockerNumber(enroll.getLocker() != null ? enroll.getLocker().getLockerNumber() : null)
                .lockerGender(enroll.getLocker() != null ? enroll.getLocker().getGender().name() : null)
                .build();
    }
} 