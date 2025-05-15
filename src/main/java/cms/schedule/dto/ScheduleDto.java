package cms.schedule.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

import javax.validation.constraints.NotBlank;
import javax.validation.constraints.NotNull;
import java.time.LocalDateTime;

@Data
@Schema(description = "스케줄 정보")
public class ScheduleDto {
    @Schema(description = "스케줄 ID")
    private Long scheduleId;

    @NotBlank(message = "제목은 필수입니다.")
    @Schema(description = "제목", example = "스케줄 제목")
    private String title;

    @Schema(description = "내용", example = "스케줄 내용")
    private String content;

    @NotNull(message = "시작일시는 필수입니다.")
    @Schema(description = "시작일시", example = "2024-03-20T10:00:00")
    private LocalDateTime startDateTime;

    @NotNull(message = "종료일시는 필수입니다.")
    @Schema(description = "종료일시", example = "2024-03-20T12:00:00")
    private LocalDateTime endDateTime;

    @Schema(description = "표시 여부(Y/N)", example = "Y")
    private String displayYn;

    @Schema(description = "상태", example = "UPCOMING")
    private String status;

    @Schema(description = "생성자")
    private String createdBy;

    @Schema(description = "생성일시")
    private LocalDateTime createdDate;

    @Schema(description = "수정자")
    private String updatedBy;

    @Schema(description = "수정일시")
    private LocalDateTime updatedDate;
} 