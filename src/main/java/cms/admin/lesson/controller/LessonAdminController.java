package cms.admin.lesson.controller;

import cms.admin.lesson.dto.CloneLessonRequestDto;
import cms.admin.lesson.service.LessonAdminService;
import cms.common.dto.ApiResponseSchema;
import cms.swimming.dto.LessonDto;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import javax.validation.Valid;

@Tag(name = "CMS - Lesson Management", description = "수영 강습 관리 API (관리자용)")
@RestController
@RequestMapping("/api/v1/cms/lessons")
@RequiredArgsConstructor
@PreAuthorize("hasAnyRole('ADMIN', 'SYSTEM_ADMIN')")
public class LessonAdminController {

    private final LessonAdminService lessonAdminService;

    @Operation(summary = "모든 강습 목록 조회", description = "필터(상태, 연도, 월) 및 페이징을 적용하여 강습 목록을 조회합니다.")
    @GetMapping
    public ResponseEntity<ApiResponseSchema<Page<LessonDto>>> getAllLessons(
            @Parameter(description = "강습 상태 (OPEN, CLOSED, ONGOING, COMPLETED)") @RequestParam(required = false) String status,
            @Parameter(description = "연도 (YYYY)") @RequestParam(required = false) Integer year,
            @Parameter(description = "월 (1-12)") @RequestParam(required = false) Integer month,
            @PageableDefault(size = 10, sort = "startDate,desc") Pageable pageable) {
        Page<LessonDto> lessons = lessonAdminService.getAllLessons(status, year, month, pageable);
        return ResponseEntity.ok(ApiResponseSchema.success(lessons, "강습 목록 조회 성공"));
    }

    @Operation(summary = "특정 강습 상세 조회", description = "강습 ID로 특정 강습의 상세 정보를 조회합니다.")
    @GetMapping("/{lessonId}")
    public ResponseEntity<ApiResponseSchema<LessonDto>> getLessonById(
            @Parameter(description = "강습 ID") @PathVariable Long lessonId) {
        LessonDto lessonDto = lessonAdminService.getLessonById(lessonId);
        return ResponseEntity.ok(ApiResponseSchema.success(lessonDto, "강습 상세 조회 성공"));
    }

    @Operation(summary = "새 강습 생성", description = "새로운 수영 강습을 생성합니다.")
    @PostMapping
    @PreAuthorize("hasAnyRole('ADMIN', 'SYSTEM_ADMIN')")
    public ResponseEntity<ApiResponseSchema<LessonDto>> createLesson(
            @Valid @RequestBody LessonDto lessonDto) {
        LessonDto createdLesson = lessonAdminService.createLesson(lessonDto);
        return ResponseEntity.status(HttpStatus.CREATED).body(ApiResponseSchema.success(createdLesson, "강습 생성 성공"));
    }

    @Operation(summary = "강습 정보 수정", description = "기존 수영 강습의 정보를 수정합니다 (일정, 정원, 가격, 상태 등).")
    @PutMapping("/{lessonId}")
    @PreAuthorize("hasAnyRole('ADMIN', 'SYSTEM_ADMIN')")
    public ResponseEntity<ApiResponseSchema<LessonDto>> updateLesson(
            @Parameter(description = "강습 ID") @PathVariable Long lessonId,
            @Valid @RequestBody LessonDto lessonDto) {
        LessonDto updatedLesson = lessonAdminService.updateLesson(lessonId, lessonDto);
        return ResponseEntity.ok(ApiResponseSchema.success(updatedLesson, "강습 수정 성공"));
    }

    @Operation(summary = "강습 삭제", description = "특정 수영 강습을 삭제합니다. (신청자가 없는 등 특정 조건 하에 가능)")
    @DeleteMapping("/{lessonId}")
    @PreAuthorize("hasAnyRole('ADMIN', 'SYSTEM_ADMIN')") 
    public ResponseEntity<ApiResponseSchema<Void>> deleteLesson(
            @Parameter(description = "강습 ID") @PathVariable Long lessonId) {
        lessonAdminService.deleteLesson(lessonId);
        return ResponseEntity.ok(ApiResponseSchema.success("강습 삭제 성공"));
    }

    @Operation(summary = "강습 복제", description = "기존 강습을 기준으로 새 시작일로 강습을 복제합니다.")
    @PostMapping("/{lessonId}/clone")
    @PreAuthorize("hasAnyRole('ADMIN', 'SYSTEM_ADMIN')")
    public ResponseEntity<ApiResponseSchema<LessonDto>> cloneLesson(
            @Parameter(description = "원본 강습 ID") @PathVariable Long lessonId,
            @Valid @RequestBody CloneLessonRequestDto cloneLessonRequestDto) {
        LessonDto clonedLesson = lessonAdminService.cloneLesson(lessonId, cloneLessonRequestDto);
        return ResponseEntity.status(HttpStatus.CREATED).body(ApiResponseSchema.success(clonedLesson, "강습 복제 성공"));
    }
} 