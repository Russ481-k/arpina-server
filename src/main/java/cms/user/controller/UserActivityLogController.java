package cms.user.controller;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import cms.user.dto.UserActivityLogDto;
import cms.user.service.UserActivityLogService;
import cms.common.dto.ApiResponseSchema;

@Tag(name = "cms_05_User", description = "사용자 활동 로그 API")
@RestController
@RequestMapping("/cms/user-activity-logs")
@RequiredArgsConstructor
public class UserActivityLogController {

    private final UserActivityLogService userActivityLogService;

    @Operation(summary = "Get all activity logs")
    @GetMapping
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<ApiResponseSchema<Page<UserActivityLogDto>>> getActivityLogs(Pageable pageable) {
        Page<UserActivityLogDto> logs = userActivityLogService.getActivityLogs(pageable);
        return ResponseEntity.ok(ApiResponseSchema.success(logs));
    }

    @Operation(summary = "Get activity logs by user ID")
    @GetMapping("/user/{userId}")
    @PreAuthorize("hasRole('ADMIN') or #userId == authentication.principal.userId")
    public ResponseEntity<ApiResponseSchema<Page<UserActivityLogDto>>> getActivityLogsByUser(
            @Parameter(description = "User ID") @PathVariable String userId,
            Pageable pageable) {
        Page<UserActivityLogDto> logs = userActivityLogService.getActivityLogsByUser(userId, pageable);
        return ResponseEntity.ok(ApiResponseSchema.success(logs));
    }

    @Operation(summary = "Get activity log by ID")
    @GetMapping("/{logId}")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<ApiResponseSchema<UserActivityLogDto>> getActivityLog(
            @Parameter(description = "Log ID") @PathVariable String logId) {
        UserActivityLogDto log = userActivityLogService.getActivityLog(logId);
        return ResponseEntity.ok(ApiResponseSchema.success(log));
    }

    @Operation(summary = "Delete activity log")
    @DeleteMapping("/{logId}")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<ApiResponseSchema<Void>> deleteActivityLog(
            @Parameter(description = "Log ID") @PathVariable String logId) {
        userActivityLogService.deleteActivityLog(logId);
        return ResponseEntity.ok(ApiResponseSchema.success("활동 로그가 성공적으로 삭제되었습니다."));
    }
} 