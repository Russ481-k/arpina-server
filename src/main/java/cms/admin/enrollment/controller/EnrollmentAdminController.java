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
import org.springframework.data.domain.Sort;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import javax.validation.Valid;
import java.util.Map;
import java.util.List;
import java.util.Collections;
import cms.enroll.domain.Enroll;

import cms.admin.enrollment.dto.AdminCancelRequestDto;
import cms.admin.enrollment.dto.DiscountStatusUpdateRequestDto;
import cms.admin.enrollment.dto.CalculatedRefundDetailsDto;
import cms.admin.enrollment.dto.ManualUsedDaysRequestDto;
import cms.admin.enrollment.model.dto.TemporaryEnrollmentRequestDto;
import cms.admin.enrollment.dto.UpdateLockerNoRequestDto;

import org.springframework.http.HttpStatus;

@Tag(name = "CMS - Enrollment Management", description = "수강 신청 및 취소 요청 관리 API (관리자용)")
@RestController
@RequestMapping("/cms/enrollments")
@RequiredArgsConstructor
@PreAuthorize("hasAnyRole('ADMIN', 'SYSTEM_ADMIN')")
public class EnrollmentAdminController {

    private final EnrollmentAdminService enrollmentAdminService;

    @Operation(summary = "관리자 수강 정보 변경", description = "관리자가 특정 수강 등록 정보를 새로운 강습으로 변경합니다.")
    @PatchMapping("/{enrollmentId}/change-lesson")
    public ResponseEntity<ApiResponseSchema<EnrollAdminResponseDto>> changeLesson(
            @Parameter(description = "변경할 대상의 수강 등록 ID") @PathVariable Long enrollmentId,
            @RequestBody Map<String, Long> payload) {
        Long newLessonId = payload.get("newLessonId");
        EnrollAdminResponseDto updatedEnrollment = enrollmentAdminService.changeLesson(enrollmentId, newLessonId);
        return ResponseEntity.ok(ApiResponseSchema.success(updatedEnrollment, "수강 정보가 성공적으로 변경되었습니다."));
    }

    @Operation(summary = "모든 신청 내역 조회", description = "필터(연도, 월, 강습ID, 사용자ID, 결제상태) 및 페이징을 적용하여 신청 내역을 조회합니다.")
    @GetMapping
    public ResponseEntity<ApiResponseSchema<Page<EnrollAdminResponseDto>>> getAllEnrollments(
            @Parameter(description = "조회 연도 (YYYY)") @RequestParam(required = false) Integer year,
            @Parameter(description = "조회 월 (1-12)") @RequestParam(required = false) Integer month,
            @Parameter(description = "강습 ID") @RequestParam(required = false) Long lessonId,
            @Parameter(description = "사용자 UUID") @RequestParam(required = false) String userId,
            @Parameter(description = "결제 상태 (UNPAID, PAID, REFUNDED 등)") @RequestParam(required = false) String payStatus,
            @PageableDefault(size = 10, sort = "createdAt", direction = Sort.Direction.DESC) Pageable pageable) {
        Page<EnrollAdminResponseDto> enrollments = enrollmentAdminService.getAllEnrollments(year, month, lessonId,
                userId, payStatus, pageable);
        return ResponseEntity.ok(ApiResponseSchema.success(enrollments, "신청 내역 조회 성공"));
    }

    @Operation(summary = "특정 신청 상세 조회", description = "신청 ID로 특정 신청의 상세 정보를 조회합니다.")
    @GetMapping("/{enrollId}")
    public ResponseEntity<ApiResponseSchema<EnrollAdminResponseDto>> getEnrollmentById(
            @Parameter(description = "신청 ID") @PathVariable Long enrollId) {
        EnrollAdminResponseDto enrollDto = enrollmentAdminService.getEnrollmentById(enrollId);
        return ResponseEntity.ok(ApiResponseSchema.success(enrollDto, "신청 상세 조회 성공"));
    }

    @Operation(summary = "취소/환불 관리 목록 조회", description = "취소 요청(REQ), 취소 처리 중(PENDING) 상태의 신청 또는 특정 환불 관련 payStatus(예: REFUND_PENDING_ADMIN_CANCEL)를 가진 신청 목록을 조회합니다.")
    @GetMapping("/cancel-requests")
    @PreAuthorize("hasAnyRole('ADMIN', 'SYSTEM_ADMIN')")
    public ResponseEntity<ApiResponseSchema<Page<CancelRequestAdminDto>>> getCancelRequests(
            @Parameter(description = "필터링할 강습 ID (선택 사항)") @RequestParam(required = false) Long lessonId,
            @RequestParam(name = "status", required = false) List<Enroll.CancelStatusType> queryCancelStatuses,
            @RequestParam(name = "payStatus", required = false) List<String> queryPayStatuses,
            @PageableDefault(page = 0, size = 20) Pageable pageable) {

        List<Enroll.CancelStatusType> effectiveCancelStatuses = queryCancelStatuses;
        List<String> effectivePayStatuses = queryPayStatuses;
        boolean useCombinedLogic;

        boolean noCancelStatusFilter = (effectiveCancelStatuses == null || effectiveCancelStatuses.isEmpty());
        boolean noPayStatusFilter = (effectivePayStatuses == null || effectivePayStatuses.isEmpty());

        if (lessonId == null && noCancelStatusFilter && noPayStatusFilter) {
            useCombinedLogic = true;
            effectiveCancelStatuses = Collections.emptyList();
            effectivePayStatuses = Collections.emptyList();
        } else {
            useCombinedLogic = false;
            if (noCancelStatusFilter) {
                effectiveCancelStatuses = Collections.emptyList();
            }
            if (noPayStatusFilter) {
                effectivePayStatuses = Collections.emptyList();
            }
        }

        Page<CancelRequestAdminDto> page = enrollmentAdminService.getCancelRequests(lessonId, effectiveCancelStatuses,
                effectivePayStatuses, useCombinedLogic, pageable);
        return ResponseEntity.ok(ApiResponseSchema.success(page, "취소 요청 목록 조회 성공"));
    }

    @Operation(summary = "관리자 직접 취소 처리", description = "관리자가 특정 신청 건을 직접 취소 처리합니다.")
    @PutMapping("/{enrollId}/admin-cancel")
    public ResponseEntity<ApiResponseSchema<EnrollAdminResponseDto>> adminCancelEnrollment(
            @Parameter(description = "신청 ID") @PathVariable Long enrollId,
            @RequestBody(required = false) AdminCancelRequestDto request) {
        String adminComment = (request != null && request.getAdminComment() != null) ? request.getAdminComment()
                : "관리자에 의한 직접 취소";
        EnrollAdminResponseDto enrollDto = enrollmentAdminService.adminCancelEnrollment(enrollId, adminComment);
        return ResponseEntity.ok(ApiResponseSchema.success(enrollDto, "관리자 직접 취소 처리 성공"));
    }

    @Operation(summary = "신청 건 할인 상태 변경", description = "특정 신청 건의 할인 적용 상태 및 종류를 변경합니다.")
    @PutMapping("/{enrollId}/discount-status")
    public ResponseEntity<ApiResponseSchema<EnrollAdminResponseDto>> updateEnrollmentDiscountStatus(
            @Parameter(description = "신청 ID") @PathVariable Long enrollId,
            @Valid @RequestBody DiscountStatusUpdateRequestDto request) {
        EnrollAdminResponseDto updatedEnrollment = enrollmentAdminService.updateEnrollmentDiscountStatus(enrollId,
                request);
        return ResponseEntity.ok(ApiResponseSchema.success(updatedEnrollment, "신청 건 할인 상태 변경 성공"));
    }

    @Operation(summary = "수강 신청 사물함 번호 수정", description = "특정 수강 신청 정보에 연결된 사물함 번호를 수정하거나 할당 해제합니다.")
    @PutMapping("/{enrollId}/locker-no")
    public ResponseEntity<ApiResponseSchema<EnrollAdminResponseDto>> updateLockerNo(
            @Parameter(description = "수정할 대상 수강 신청의 고유 ID") @PathVariable Long enrollId,
            @RequestBody UpdateLockerNoRequestDto request) {
        EnrollAdminResponseDto updatedEnrollment = enrollmentAdminService.updateLockerNo(enrollId,
                request.getLockerNo());
        return ResponseEntity.ok(ApiResponseSchema.success(updatedEnrollment, "사물함 번호가 성공적으로 업데이트되었습니다."));
    }

    @Operation(summary = "취소 요청 승인 (환불액 직접 지정 가능)", description = "사용자의 취소 요청을 승인하고 환불 절차를 시작합니다. 관리자는 실사용일수, 최종 환불액, 전액 환불 여부를 직접 지정하여 시스템 계산을 무시할 수 있습니다.")
    @PostMapping("/{enrollId}/approve-cancel")
    public ResponseEntity<ApiResponseSchema<Void>> approveCancellation(
            @Parameter(description = "신청 ID") @PathVariable Long enrollId,
            @RequestBody(required = false) AdminCancelRequestDto payload) {

        AdminCancelRequestDto requestDto = (payload != null) ? payload : new AdminCancelRequestDto();

        enrollmentAdminService.approveCancellation(enrollId, requestDto);

        // After approval, the state is changed. The client should refetch to see the
        // updated status.
        // Returning just success status without the full object.
        return ResponseEntity.ok(ApiResponseSchema.success(null, "취소 요청이 성공적으로 승인되었습니다."));
    }

    @Operation(summary = "취소 요청 거부", description = "특정 신청의 취소 요청을 거부합니다.")
    @PostMapping("/{enrollId}/deny-cancel")
    public ResponseEntity<ApiResponseSchema<EnrollAdminResponseDto>> denyCancellation(
            @Parameter(description = "신청 ID") @PathVariable Long enrollId,
            @Valid @RequestBody Map<String, String> payload) {
        String adminComment = payload.get("adminComment");
        if (adminComment == null || adminComment.trim().isEmpty()) {
            return ResponseEntity.badRequest()
                    .body(ApiResponseSchema.error("취소 요청 거부 시 관리자 코멘트는 필수입니다.", "MISSING_ADMIN_COMMENT"));
        }
        EnrollAdminResponseDto enrollDto = enrollmentAdminService.denyCancellation(enrollId, adminComment);
        return ResponseEntity.ok(ApiResponseSchema.success(enrollDto, "취소 요청 거부 처리 성공"));
    }

    @Operation(summary = "환불 요청 거부 (대체 URL)", description = "특정 신청의 환불 요청을 거부합니다. (기존 deny-cancel과 동일한 기능)")
    @PostMapping("/{enrollId}/cancel/deny")
    public ResponseEntity<ApiResponseSchema<EnrollAdminResponseDto>> denyRefundRequest(
            @Parameter(description = "신청 ID") @PathVariable Long enrollId,
            @Valid @RequestBody Map<String, String> payload) {
        String adminComment = payload.get("adminComment");
        if (adminComment == null || adminComment.trim().isEmpty()) {
            return ResponseEntity.badRequest()
                    .body(ApiResponseSchema.error("환불 요청 거부 시 관리자 코멘트는 필수입니다.", "MISSING_ADMIN_COMMENT"));
        }
        EnrollAdminResponseDto enrollDto = enrollmentAdminService.denyCancellation(enrollId, adminComment);
        return ResponseEntity.ok(ApiResponseSchema.success(enrollDto, "환불 요청 거부 처리 성공"));
    }

    @Operation(summary = "취소/환불 처리 시 예상 환불액 미리보기", description = "관리자가 실사용일수를 변경하며 예상 환불액을 미리 계산해봅니다. DB는 변경되지 않습니다.")
    @PostMapping("/{enrollId}/calculate-refund-preview")
    public ResponseEntity<ApiResponseSchema<CalculatedRefundDetailsDto>> calculateRefundPreview(
            @Parameter(description = "신청 ID") @PathVariable Long enrollId,
            @Valid @RequestBody(required = false) ManualUsedDaysRequestDto request) {
        Integer manualUsedDays = (request != null) ? request.getManualUsedDays() : null;
        CalculatedRefundDetailsDto refundDetails = enrollmentAdminService.getRefundPreview(enrollId, manualUsedDays);
        return ResponseEntity.ok(ApiResponseSchema.success(refundDetails, "예상 환불액 계산 성공"));
    }

    @PostMapping("/temporary")
    @Operation(summary = "오프라인 등록자를 위한 임시 등록", description = "관리자가 오프라인으로 신청/결제한 사용자를 시스템에 임시로 등록합니다.")
    @PreAuthorize("hasAnyRole('ADMIN', 'SYSTEM_ADMIN')")
    public ResponseEntity<EnrollAdminResponseDto> createTemporaryEnrollment(
            @Valid @RequestBody TemporaryEnrollmentRequestDto requestDto) {
        EnrollAdminResponseDto newEnrollment = enrollmentAdminService.createTemporaryEnrollment(requestDto);
        return ResponseEntity.status(HttpStatus.CREATED).body(newEnrollment);
    }
}