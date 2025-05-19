package cms.swimming.controller;

import cms.common.dto.ApiResponseSchema;
import cms.swimming.dto.*;
import cms.swimming.service.EnrollService;
import cms.swimming.service.LessonService;
import cms.swimming.service.LockerService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;

import javax.validation.Valid;
import java.util.List;

@RestController
@RequestMapping("/api/v1/admin/swimming")
@RequiredArgsConstructor
@Tag(name = "swimming_admin", description = "수영장 관리자 API")
@SecurityRequirement(name = "bearerAuth")
@Validated
public class SwimmingAdminController {

    private final LessonService lessonService;
    private final LockerService lockerService;
    private final EnrollService enrollService;

    // 1. 강습 관리 API
    @Operation(summary = "모든 강습 목록 조회", description = "모든 강습 목록을 페이징하여 조회합니다.")
    @GetMapping("/lessons")
    @PreAuthorize("hasAnyRole('PROGRAM_ADMIN', 'SUPER_ADMIN')")
    public ResponseEntity<ApiResponseSchema<Page<LessonDto>>> getAllLessons(
            @PageableDefault(size = 10, sort = "startDate", direction = Sort.Direction.DESC) Pageable pageable) {
        Page<LessonDto> lessons = lessonService.getAllLessons(pageable);
        return ResponseEntity.ok(ApiResponseSchema.success(lessons, "강습 목록 조회 성공"));
    }

    @Operation(summary = "특정 상태의 강습 목록 조회", description = "특정 상태의 강습 목록을 페이징하여 조회합니다.")
    @GetMapping("/lessons/status/{status}")
    @PreAuthorize("hasAnyRole('PROGRAM_ADMIN', 'SUPER_ADMIN', 'CS_AGENT')")
    public ResponseEntity<ApiResponseSchema<Page<LessonDto>>> getLessonsByStatus(
            @Parameter(description = "강습 상태 (OPEN, CLOSED, FINISHED)") @PathVariable String status,
            @PageableDefault(size = 10, sort = "startDate", direction = Sort.Direction.DESC) Pageable pageable) {
        Page<LessonDto> lessons = lessonService.getLessonsByStatus(status, pageable);
        return ResponseEntity.ok(ApiResponseSchema.success(lessons, "강습 목록 조회 성공"));
    }

    @Operation(summary = "강습 상세 조회", description = "특정 강습의 상세 정보를 조회합니다.")
    @GetMapping("/lessons/{lessonId}")
    @PreAuthorize("hasAnyRole('PROGRAM_ADMIN', 'SUPER_ADMIN', 'CS_AGENT')")
    public ResponseEntity<ApiResponseSchema<LessonDto>> getLessonById(
            @Parameter(description = "조회할 강습 ID") @PathVariable Long lessonId) {
        LessonDto lesson = lessonService.getLessonById(lessonId);
        return ResponseEntity.ok(ApiResponseSchema.success(lesson, "강습 상세 조회 성공"));
    }

    // 2. 라커 관리 API
    @Operation(summary = "모든 사물함 조회", description = "모든 사물함 목록을 조회합니다.")
    @GetMapping("/lockers")
    @PreAuthorize("hasAnyRole('PROGRAM_ADMIN', 'SUPER_ADMIN')")
    public ResponseEntity<ApiResponseSchema<List<LockerDto>>> getAllLockers() {
        List<LockerDto> lockers = lockerService.getAllLockers();
        return ResponseEntity.ok(ApiResponseSchema.success(lockers, "사물함 목록 조회 성공"));
    }

    @Operation(summary = "특정 구역의 사물함 조회", description = "특정 구역의 사물함 목록을 조회합니다.")
    @GetMapping("/lockers/zone/{zone}")
    @PreAuthorize("hasAnyRole('PROGRAM_ADMIN', 'SUPER_ADMIN')")
    public ResponseEntity<ApiResponseSchema<List<LockerDto>>> getLockersByZone(
            @Parameter(description = "사물함 구역") @PathVariable String zone,
            @Parameter(description = "활성화 상태 필터링") @RequestParam(required = false, defaultValue = "true") Boolean isActive) {
        List<LockerDto> lockers = lockerService.getLockersByZone(zone, isActive);
        return ResponseEntity.ok(ApiResponseSchema.success(lockers, "사물함 목록 조회 성공"));
    }

    @Operation(summary = "성별별 사물함 조회", description = "성별에 따른 사물함 목록을 조회합니다.")
    @GetMapping("/lockers/gender/{gender}")
    @PreAuthorize("hasAnyRole('PROGRAM_ADMIN', 'SUPER_ADMIN')")
    public ResponseEntity<ApiResponseSchema<List<LockerDto>>> getLockersByGender(
            @Parameter(description = "성별 (M, F)") @PathVariable String gender,
            @Parameter(description = "활성화 상태 필터링") @RequestParam(required = false, defaultValue = "true") Boolean isActive) {
        List<LockerDto> lockers = lockerService.getLockersByGenderAndActive(gender, isActive);
        return ResponseEntity.ok(ApiResponseSchema.success(lockers, "사물함 목록 조회 성공"));
    }

    // 3. 신청 관리 API
    @Operation(summary = "모든 신청 내역 조회", description = "모든 신청 내역을 상태별로 페이징하여 조회합니다.")
    @GetMapping("/enrolls")
    @PreAuthorize("hasAnyRole('CS_AGENT', 'SUPER_ADMIN')")
    public ResponseEntity<ApiResponseSchema<Page<EnrollResponseDto>>> getAllEnrolls(
            @Parameter(description = "신청 상태 (APPLIED, CANCELED, PENDING)") @RequestParam(required = false) String status,
            @Parameter(description = "강습 ID로 필터링") @RequestParam(required = false) Long lessonId,
            @PageableDefault(size = 10, sort = "createdAt", direction = Sort.Direction.DESC) Pageable pageable) {
        
        Page<EnrollResponseDto> enrolls;
        if (lessonId != null) {
            enrolls = enrollService.getEnrollsByLessonId(lessonId, pageable);
        } else if (status != null) {
            enrolls = enrollService.getUserEnrollsByStatus(null, status, pageable);
        } else {
            // 전체 조회 로직 필요
            enrolls = Page.empty();
        }
        
        return ResponseEntity.ok(ApiResponseSchema.success(enrolls, "신청 내역 조회 성공"));
    }

    @Operation(summary = "취소 요청 승인", description = "개강 후 취소 요청을 승인하고 환불 처리합니다.")
    @PostMapping("/enrolls/{enrollId}/approve-cancel")
    @PreAuthorize("hasAnyRole('FINANCE_ADMIN', 'SUPER_ADMIN')")
    public ResponseEntity<ApiResponseSchema<Void>> approveCancelRequest(
            @Parameter(description = "취소할 신청 ID") @PathVariable Long enrollId,
            @Parameter(description = "환불 비율 (0-100%)") @RequestParam Integer refundPct) {
        
        // 취소 승인 로직 구현 필요
        // enrollService.approveCancelRequest(enrollId, refundPct);
        
        return ResponseEntity.ok(ApiResponseSchema.success("취소 요청이 승인되었으며, 환불이 처리되었습니다."));
    }

    @Operation(summary = "취소 요청 거부", description = "개강 후 취소 요청을 거부합니다.")
    @PostMapping("/enrolls/{enrollId}/deny-cancel")
    @PreAuthorize("hasAnyRole('CS_AGENT', 'SUPER_ADMIN')")
    public ResponseEntity<ApiResponseSchema<Void>> denyCancelRequest(
            @Parameter(description = "취소할 신청 ID") @PathVariable Long enrollId,
            @Parameter(description = "거부 사유") @RequestParam String comment) {
        
        // 취소 거부 로직 구현 필요
        // enrollService.denyCancelRequest(enrollId, comment);
        
        return ResponseEntity.ok(ApiResponseSchema.success("취소 요청이 거부되었습니다."));
    }

    // 4. 통계 API는 추후 구현
} 