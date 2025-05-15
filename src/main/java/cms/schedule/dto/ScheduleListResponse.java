package cms.schedule.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

import java.util.List;

@Data
@Schema(description = "스케줄 목록 응답")
public class ScheduleListResponse {
    @Schema(description = "스케줄 목록")
    private List<ScheduleDto> schedules;

    @Schema(description = "전체 건수")
    private Long totalCount;

    public ScheduleListResponse(List<ScheduleDto> schedules) {
        this.schedules = schedules;
        this.totalCount = (long) schedules.size();
    }

    public ScheduleListResponse(List<ScheduleDto> schedules, Long totalCount) {
        this.schedules = schedules;
        this.totalCount = totalCount;
    }
} 