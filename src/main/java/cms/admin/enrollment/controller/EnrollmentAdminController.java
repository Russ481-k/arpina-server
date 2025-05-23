package cms.admin.enrollment.controller;

import cms.admin.enrollment.dto.CancelRequestAdminDto;
import cms.admin.enrollment.dto.EnrollAdminResponseDto;
import cms.admin.enrollment.service.EnrollmentAdminService;
import cms.common.dto.ApiResponseSchema;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import javax.validation.Valid;
import java.util.Map;

@Tag(name = "CMS - Enrollment Management", description = "수강 신청 및 취소 요청 관리 API (관리자용)")
@RestController
@RequestMapping("/api/v1/cms/enrollments")
@RequiredArgsConstructor
@PreAuthorize("hasAnyRole('ADMIN', 'SYSTEM_ADMIN')")
public class EnrollmentAdminController {

    private final EnrollmentAdminService enrollmentAdminService;

    @Operation(summary = "모든 신청 내역 조회", description = "필터(강습ID, 사용자ID, 결제상태) 및 페이징을 적용하여 신청 내역을 조회합니다.")
    @GetMapping
    public ResponseEntity<ApiResponseSchema<Page<EnrollAdminResponseDto>>> getAllEnrollments(
            @Parameter(description = "강습 ID") @RequestParam(required = false) Long lessonId,
            @Parameter(description = "사용자 UUID") @RequestParam(required = false) String userId,
            @Parameter(description = "결제 상태 (UNPAID, PAID, REFUNDED 등)") @RequestParam(required = false) String payStatus,
            @PageableDefault(size = 10, sort = "createdAt,desc") Pageable pageable) {
        Page<EnrollAdminResponseDto> enrollments = enrollmentAdminService.getAllEnrollments(lessonId, userId, payStatus, pageable);
        return ResponseEntity.ok(ApiResponseSchema.success(enrollments, "신청 내역 조회 성공"));
    }

    @Operation(summary = "특정 신청 상세 조회", description = "신청 ID로 특정 신청의 상세 정보를 조회합니다.")
    @GetMapping("/{enrollId}")
    public ResponseEntity<ApiResponseSchema<EnrollAdminResponseDto>> getEnrollmentById(
            @Parameter(description = "신청 ID") @PathVariable Long enrollId) {
        EnrollAdminResponseDto enrollDto = enrollmentAdminService.getEnrollmentById(enrollId);
        return ResponseEntity.ok(ApiResponseSchema.success(enrollDto, "신청 상세 조회 성공"));
    }

    @Operation(summary = "취소 요청 목록 조회", description = "취소 요청 상태(예: REQ)의 목록을 조회합니다.")
    @GetMapping("/cancel-requests")
    public ResponseEntity<ApiResponseSchema<Page<CancelRequestAdminDto>>> getCancelRequests(
            @Parameter(description = "취소 요청 상태 (기본: REQ)") @RequestParam(defaultValue = "REQ") String status,
            @PageableDefault(size = 10, sort = "updatedAt,desc") Pageable pageable) {
        Page<CancelRequestAdminDto> cancelRequests = enrollmentAdminService.getCancelRequests(status, pageable);
        return ResponseEntity.ok(ApiResponseSchema.success(cancelRequests, "취소 요청 목록 조회 성공"));
    }

    @Operation(summary = "취소 요청 승인", description = "특정 신청의 취소 요청을 승인하고 환불 절차를 진행합니다.")
    @PostMapping("/{enrollId}/approve-cancel")
    public ResponseEntity<ApiResponseSchema<EnrollAdminResponseDto>> approveCancellation(
            @Parameter(description = "신청 ID") @PathVariable Long enrollId,
            @RequestBody(required = false) Map<String, String> payload) {
        String adminComment = (payload != null && payload.containsKey("adminComment")) ? payload.get("adminComment") : "";
        EnrollAdminResponseDto enrollDto = enrollmentAdminService.approveCancellation(enrollId, adminComment);
        return ResponseEntity.ok(ApiResponseSchema.success(enrollDto, "취소 요청 승인 처리 성공"));
    }

    @Operation(summary = "취소 요청 거부", description = "특정 신청의 취소 요청을 거부합니다.")
    @PostMapping("/{enrollId}/deny-cancel")
    public ResponseEntity<ApiResponseSchema<EnrollAdminResponseDto>> denyCancellation(
            @Parameter(description = "신청 ID") @PathVariable Long enrollId,
            @Valid @RequestBody Map<String, String> payload) { // 거부 시 코멘트는 필수일 수 있음
        String adminComment = payload.get("adminComment");
         if (adminComment == null || adminComment.trim().isEmpty()) {
            return ResponseEntity.badRequest().body(ApiResponseSchema.error("취소 요청 거부 시 관리자 코멘트는 필수입니다.", "MISSING_ADMIN_COMMENT"));
        }
        EnrollAdminResponseDto enrollDto = enrollmentAdminService.denyCancellation(enrollId, adminComment);
        return ResponseEntity.ok(ApiResponseSchema.success(enrollDto, "취소 요청 거부 처리 성공"));
    }
} 