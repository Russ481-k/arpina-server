package cms.schedule.controller;

import cms.common.dto.ApiResponseSchema;
import cms.schedule.dto.ScheduleDto;
import cms.schedule.dto.ScheduleListResponse;
import cms.schedule.service.ScheduleService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.bind.annotation.*;
import javax.servlet.http.HttpServletRequest;
import java.time.LocalDate;
import java.util.List;
import cms.common.util.IpUtil;

@RestController
@RequestMapping("/cms/schedule")
@Tag(name = "cms_08_Schedule", description = "스케줄 관리 API")
@RequiredArgsConstructor
public class ScheduleController {

    private final ScheduleService scheduleService;

    @GetMapping("/{year}/{month}")
    @PreAuthorize("permitAll()")
    @Operation(summary = "월별 스케줄 조회", description = "지정된 연월의 스케줄 목록을 조회합니다.")
    public ResponseEntity<ApiResponseSchema<ScheduleListResponse>> getSchedulesByMonth(
            @Parameter(description = "연도") @PathVariable int year,
            @Parameter(description = "월") @PathVariable int month) {
        List<ScheduleDto> schedules = scheduleService.getSchedulesByYearMonth(year, month);
        return ResponseEntity.ok(ApiResponseSchema.success(new ScheduleListResponse(schedules)));
    }

    @GetMapping("/range/{dateFrom}/{dateTo}")
    @PreAuthorize("permitAll()")
    @Operation(summary = "기간별 스케줄 조회", description = "지정된 기간의 스케줄 목록을 조회합니다.")
    public ResponseEntity<ApiResponseSchema<ScheduleListResponse>> getSchedulesByRange(
            @Parameter(description = "시작일") @PathVariable @DateTimeFormat(pattern = "yyyy-MM-dd") LocalDate dateFrom,
            @Parameter(description = "종료일") @PathVariable @DateTimeFormat(pattern = "yyyy-MM-dd") LocalDate dateTo) {
        List<ScheduleDto> schedules = scheduleService.getSchedulesByDateRange(dateFrom, dateTo);
        return ResponseEntity.ok(ApiResponseSchema.success(new ScheduleListResponse(schedules)));
    }

    @GetMapping("/{scheduleId}")
    @PreAuthorize("permitAll()")
    @Operation(summary = "스케줄 상세 조회", description = "스케줄 ID에 해당하는 상세 정보를 조회합니다.")
    public ResponseEntity<ApiResponseSchema<ScheduleDto>> getSchedule(
            @Parameter(description = "스케줄 ID") @PathVariable Long scheduleId) {
        ScheduleDto schedule = scheduleService.getSchedule(scheduleId);
        return ResponseEntity.ok(ApiResponseSchema.success(schedule));
    }

    @PostMapping
    @PreAuthorize("hasAnyRole('STAFF', 'ADMIN')")
    @Operation(summary = "스케줄 등록", description = "새로운 스케줄을 등록합니다.")
    public ResponseEntity<ApiResponseSchema<ScheduleDto>> createSchedule(
            @Parameter(description = "스케줄 정보") @RequestBody ScheduleDto scheduleDto,
            HttpServletRequest request) {
        String createdBy = getCurrentUsername();
        String createdIp = IpUtil.getClientIp();
        ScheduleDto created = scheduleService.createSchedule(scheduleDto, createdBy, createdIp);
        return ResponseEntity.ok(ApiResponseSchema.success(created));
    }

    @PutMapping("/{scheduleId}")
    @PreAuthorize("hasAnyRole('STAFF', 'ADMIN')")
    @Operation(summary = "스케줄 수정", description = "기존 스케줄을 수정합니다.")
    public ResponseEntity<ApiResponseSchema<ScheduleDto>> updateSchedule(
            @Parameter(description = "스케줄 ID") @PathVariable Long scheduleId,
            @Parameter(description = "스케줄 정보") @RequestBody ScheduleDto scheduleDto,
            HttpServletRequest request) {
        String updatedBy = getCurrentUsername();
        String updatedIp = IpUtil.getClientIp();
        ScheduleDto updated = scheduleService.updateSchedule(scheduleId, scheduleDto, updatedBy, updatedIp);
        return ResponseEntity.ok(ApiResponseSchema.success(updated));
    }

    @DeleteMapping("/{scheduleId}")
    @PreAuthorize("hasAnyRole('STAFF', 'ADMIN')")
    @Operation(summary = "스케줄 삭제", description = "스케줄을 삭제합니다.")
    public ResponseEntity<ApiResponseSchema<Void>> deleteSchedule(
            @Parameter(description = "스케줄 ID") @PathVariable Long scheduleId) {
        scheduleService.deleteSchedule(scheduleId);
        return ResponseEntity.ok(ApiResponseSchema.success("스케줄이 삭제되었습니다."));
    }

    private String getCurrentUsername() {
        return SecurityContextHolder.getContext().getAuthentication().getName();
    }
}