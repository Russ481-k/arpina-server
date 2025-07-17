package cms.admin.lesson.controller;

import cms.admin.lesson.dto.AdminLessonCreateRequestDto;
import cms.admin.lesson.dto.AdminLessonResponseDto;
import cms.admin.lesson.dto.AdminLessonUpdateRequestDto;
import cms.common.dto.ApiResponseSchema;
import cms.admin.lesson.service.LessonAdminService;
import cms.admin.lesson.dto.CloneLessonRequestDto;
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
import cms.common.util.IpUtil;
import javax.servlet.http.HttpServletRequest;
import java.io.IOException;
import javax.validation.Valid;

@Tag(name = "CMS - Lesson Management", description = "강습 관리 API (관리자용)")
@RestController
@RequestMapping("/cms/lessons")
@RequiredArgsConstructor
@SecurityRequirement(name = "bearerAuth")
@Validated
public class LessonAdminController {

    private final LessonAdminService lessonAdminService;

    private String getCurrentUsername() {
        return "admin_user";
    }

    @Operation(summary = "모든 강습 목록 조회 (관리자용)", description = "모든 강습 목록을 페이징하여 조회합니다. 상태, 년도, 월별 필터링 가능.")
    @GetMapping
    @PreAuthorize("hasAnyRole('SYSTEM_ADMIN', 'ADMIN')")
    public ResponseEntity<ApiResponseSchema<Page<AdminLessonResponseDto>>> getAllLessonsAdmin(
            @PageableDefault(size = 10, sort = "startDate", direction = Sort.Direction.DESC) Pageable pageable,
            @Parameter(description = "년도 (YYYY)") @RequestParam(required = false) Integer year,
            @Parameter(description = "월 (1-12)") @RequestParam(required = false) Integer month) {
        Page<AdminLessonResponseDto> lessons = lessonAdminService.getAllLessonsAdmin(pageable, year, month);
        return ResponseEntity.ok(ApiResponseSchema.success(lessons, "관리자용 강습 목록 조회 성공"));
    }

    @Operation(summary = "강습 상세 조회 (관리자용)", description = "특정 강습의 상세 정보를 조회합니다.")
    @GetMapping("/{lessonId}")
    @PreAuthorize("hasAnyRole('SYSTEM_ADMIN', 'CS_AGENT', 'ADMIN')")
    public ResponseEntity<ApiResponseSchema<AdminLessonResponseDto>> getLessonByIdAdmin(
            @Parameter(description = "조회할 강습 ID") @PathVariable Long lessonId) {
        AdminLessonResponseDto lesson = lessonAdminService.getLessonByIdAdmin(lessonId);
        return ResponseEntity.ok(ApiResponseSchema.success(lesson, "강습 상세 조회 성공"));
    }

    @Operation(summary = "새 강습 생성 (관리자용)", description = "새로운 강습을 생성합니다.")
    @PostMapping
    @PreAuthorize("hasAnyRole('ADMIN', 'SYSTEM_ADMIN')")
    public ResponseEntity<ApiResponseSchema<AdminLessonResponseDto>> createLessonAdmin(
            @Valid @RequestBody AdminLessonCreateRequestDto createRequestDto,
            HttpServletRequest request) throws IOException {
        String createdBy = getCurrentUsername();
        String createdIp = IpUtil.getClientIp();
        AdminLessonResponseDto createdLesson = lessonAdminService.createLessonAdmin(createRequestDto, createdBy,
                createdIp);
        return ResponseEntity.status(HttpStatus.CREATED).body(ApiResponseSchema.success(createdLesson, "강습 생성 성공"));
    }

    @Operation(summary = "강습 정보 수정 (관리자용)", description = "기존 강습의 정보를 수정합니다.")
    @PutMapping("/{lessonId}")
    @PreAuthorize("hasAnyRole('ADMIN', 'SYSTEM_ADMIN')")
    public ResponseEntity<ApiResponseSchema<AdminLessonResponseDto>> updateLessonAdmin(
            @Parameter(description = "수정할 강습 ID") @PathVariable Long lessonId,
            @Valid @RequestBody AdminLessonUpdateRequestDto updateRequestDto,
            HttpServletRequest request) throws IOException {
        String updatedBy = getCurrentUsername();
        String updatedIp = IpUtil.getClientIp();
        AdminLessonResponseDto updatedLesson = lessonAdminService.updateLessonAdmin(lessonId, updateRequestDto,
                updatedBy, updatedIp);
        return ResponseEntity.ok(ApiResponseSchema.success(updatedLesson, "강습 정보 수정 성공"));
    }

    @Operation(summary = "강습 삭제 (관리자용)", description = "특정 강습을 삭제합니다 (조건부).")
    @DeleteMapping("/{lessonId}")
    @PreAuthorize("hasAnyRole('ADMIN', 'SYSTEM_ADMIN')")
    public ResponseEntity<ApiResponseSchema<Void>> deleteLessonAdmin(
            @Parameter(description = "삭제할 강습 ID") @PathVariable Long lessonId) {
        lessonAdminService.deleteLessonAdmin(lessonId);
        return ResponseEntity.status(HttpStatus.NO_CONTENT).body(ApiResponseSchema.success(null, "강습 삭제 성공"));
    }

    @Operation(summary = "강습 복제 (관리자용)", description = "기존 강습을 복제하여 새 강습을 생성합니다.")
    @PostMapping("/{lessonId}/clone")
    @PreAuthorize("hasAnyRole('ADMIN', 'SYSTEM_ADMIN')")
    public ResponseEntity<ApiResponseSchema<AdminLessonResponseDto>> cloneLessonAdmin(
            @Parameter(description = "복제할 강습 ID") @PathVariable Long lessonId,
            @Valid @RequestBody CloneLessonRequestDto cloneRequest,
            HttpServletRequest request) throws IOException {
        String createdBy = getCurrentUsername();
        String createdIp = IpUtil.getClientIp();
        AdminLessonResponseDto clonedLesson = lessonAdminService.cloneLessonAdmin(lessonId, cloneRequest, createdBy,
                createdIp);
        return ResponseEntity.status(HttpStatus.CREATED).body(ApiResponseSchema.success(clonedLesson, "강습 복제 성공"));
    }
}