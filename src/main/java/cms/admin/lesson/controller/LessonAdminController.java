package cms.admin.lesson.controller;

import cms.common.dto.ApiResponseSchema;
import cms.swimming.dto.*;
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

@Tag(name = "CMS - Lesson Management", description = "강습 관리 API (관리자용)")
@RestController
@RequestMapping("/cms/lessons")
@RequiredArgsConstructor
@SecurityRequirement(name = "bearerAuth")
@Validated
public class LessonAdminController {

    private final LessonAdminService lessonAdminService;

    @Operation(summary = "모든 강습 목록 조회", description = "모든 강습 목록을 페이징하여 조회합니다.")
    @GetMapping
    @PreAuthorize("hasAnyRole('SYSTEM_ADMIN', 'ADMIN')")
    public ResponseEntity<ApiResponseSchema<Page<LessonDto>>> getAllLessons(
            @PageableDefault(size = 10, sort = "startDate", direction = Sort.Direction.DESC) Pageable pageable) {
        Page<LessonDto> lessons = lessonAdminService.getAllLessonsAdmin(pageable);
        return ResponseEntity.ok(ApiResponseSchema.success(lessons, "강습 목록 조회 성공"));
    }

    @Operation(summary = "특정 상태의 강습 목록 조회", description = "특정 상태의 강습 목록을 페이징하여 조회합니다.")
    @GetMapping("/status/{status}")
    @PreAuthorize("hasAnyRole('SYSTEM_ADMIN', 'CS_AGENT', 'ADMIN')")
    public ResponseEntity<ApiResponseSchema<Page<LessonDto>>> getLessonsByStatus(
            @Parameter(description = "강습 상태 (OPEN, CLOSED, ONGOING, COMPLETED)") @PathVariable String status,
            @PageableDefault(size = 10, sort = "startDate", direction = Sort.Direction.DESC) Pageable pageable) {
        Page<LessonDto> lessons = lessonAdminService.getLessonsByStatusAdmin(status, pageable);
        return ResponseEntity.ok(ApiResponseSchema.success(lessons, "강습 목록 조회 성공"));
    }

    @Operation(summary = "강습 상세 조회", description = "특정 강습의 상세 정보를 조회합니다.")
    @GetMapping("/{lessonId}")
    @PreAuthorize("hasAnyRole('SYSTEM_ADMIN', 'CS_AGENT', 'ADMIN')")
    public ResponseEntity<ApiResponseSchema<LessonDto>> getLessonById(
            @Parameter(description = "조회할 강습 ID") @PathVariable Long lessonId) {
        LessonDto lesson = lessonAdminService.getLessonByIdAdmin(lessonId);
        return ResponseEntity.ok(ApiResponseSchema.success(lesson, "강습 상세 조회 성공"));
    }

    @Operation(summary = "새 강습 생성", description = "새로운 강습을 생성합니다. 스케줄(기간, 시간) 포함.")
    @PostMapping
    @PreAuthorize("hasAnyRole('ADMIN', 'SYSTEM_ADMIN')")
    public ResponseEntity<ApiResponseSchema<LessonDto>> createLesson(@RequestBody LessonDto lessonDto) {
        LessonDto createdLesson = lessonAdminService.createLesson(lessonDto);
        return ResponseEntity.status(HttpStatus.CREATED).body(ApiResponseSchema.success(createdLesson, "강습 생성 성공"));
    }

    @Operation(summary = "강습 정보 수정", description = "기존 강습의 정보 및 스케줄을 수정합니다.")
    @PutMapping("/{lessonId}")
    @PreAuthorize("hasAnyRole('ADMIN', 'SYSTEM_ADMIN')")
    public ResponseEntity<ApiResponseSchema<LessonDto>> updateLesson(
            @Parameter(description = "수정할 강습 ID") @PathVariable Long lessonId,
            @RequestBody LessonDto lessonDto) {
        LessonDto updatedLesson = lessonAdminService.updateLesson(lessonId, lessonDto);
        return ResponseEntity.ok(ApiResponseSchema.success(updatedLesson, "강습 정보 수정 성공"));
    }

    @Operation(summary = "강습 삭제", description = "특정 강습을 삭제합니다 (조건부).")
    @DeleteMapping("/{lessonId}")
    @PreAuthorize("hasAnyRole('ADMIN', 'SYSTEM_ADMIN')")
    public ResponseEntity<ApiResponseSchema<Void>> deleteLesson(
            @Parameter(description = "삭제할 강습 ID") @PathVariable Long lessonId) {
        lessonAdminService.deleteLesson(lessonId);
        return ResponseEntity.noContent().build();
    }

    @Operation(summary = "강습 복제", description = "기존 강습을 복제하여 새 강습을 생성합니다.")
    @PostMapping("/{lessonId}/clone")
    @PreAuthorize("hasAnyRole('ADMIN', 'SYSTEM_ADMIN')")
    public ResponseEntity<ApiResponseSchema<LessonDto>> cloneLesson(
            @Parameter(description = "복제할 강습 ID") @PathVariable Long lessonId,
            @RequestBody CloneLessonRequestDto cloneRequest) {
        LessonDto clonedLesson = lessonAdminService.cloneLesson(lessonId, cloneRequest);
        return ResponseEntity.status(HttpStatus.CREATED).body(ApiResponseSchema.success(clonedLesson, "강습 복제 성공"));
    }
} 