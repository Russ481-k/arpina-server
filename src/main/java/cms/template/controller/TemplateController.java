package cms.template.controller;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import cms.template.service.TemplateService;
import cms.template.dto.TemplateDto;
import cms.common.dto.ApiResponseSchema;

@RestController
@RequestMapping("/cms/template")
@RequiredArgsConstructor
@Tag(name = "cms_02_Template", description = "템플릿 관리 API")
public class TemplateController {

    private final TemplateService templateService;

    @Operation(summary = "공개 템플릿 목록 조회", description = "공개된 템플릿 목록을 조회합니다.")
    @GetMapping("/public")
    public ResponseEntity<ApiResponseSchema<Page<TemplateDto>>> getPublicTemplates(
        @Parameter(description = "검색 키워드") @RequestParam(required = false) String keyword,
        Pageable pageable) {
        return ResponseEntity.ok(templateService.getPublicTemplates(keyword, pageable));
    }

    @Operation(summary = "공개 템플릿 상세 조회", description = "공개된 템플릿의 상세 정보를 조회합니다.")
    @GetMapping("/public/{templateId}")
    public ResponseEntity<ApiResponseSchema<TemplateDto>> getPublicTemplate(
        @Parameter(description = "템플릿 ID") @PathVariable Long templateId) {
        return ResponseEntity.ok(templateService.getPublicTemplate(templateId));
    }

    @Operation(summary = "템플릿 목록 조회", description = "템플릿 목록을 조회합니다.")
    @GetMapping
    public ResponseEntity<ApiResponseSchema<Page<TemplateDto>>> getTemplates(
        @Parameter(description = "검색 키워드") @RequestParam(required = false) String keyword,
        Pageable pageable) {
        return ResponseEntity.ok(templateService.getTemplates(keyword, pageable));
    }

    @Operation(summary = "템플릿 생성", description = "새로운 템플릿을 생성합니다.")
    @PostMapping
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<ApiResponseSchema<TemplateDto>> createTemplate(
        @Parameter(description = "템플릿 정보") @RequestBody TemplateDto templateDto) {
        return ResponseEntity.ok(templateService.createTemplate(templateDto));
    }

    @Operation(summary = "템플릿 상세 조회", description = "템플릿의 상세 정보를 조회합니다.")
    @GetMapping("/{templateId}")
    public ResponseEntity<ApiResponseSchema<TemplateDto>> getTemplate(
        @Parameter(description = "템플릿 ID") @PathVariable Long templateId) {
        return ResponseEntity.ok(templateService.getTemplate(templateId));
    }

    @Operation(summary = "템플릿 수정", description = "템플릿 정보를 수정합니다.")
    @PutMapping("/{templateId}")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<ApiResponseSchema<TemplateDto>> updateTemplate(
        @Parameter(description = "템플릿 ID") @PathVariable Long templateId,
        @Parameter(description = "템플릿 정보") @RequestBody TemplateDto templateDto) {
        return ResponseEntity.ok(templateService.updateTemplate(templateId, templateDto));
    }

    @Operation(summary = "템플릿 삭제", description = "템플릿을 삭제합니다.")
    @DeleteMapping("/{templateId}")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<ApiResponseSchema<Void>> deleteTemplate(
        @Parameter(description = "템플릿 ID") @PathVariable Long templateId) {
        return ResponseEntity.ok(templateService.deleteTemplate(templateId));
    }

    @Operation(summary = "템플릿 복제", description = "템플릿을 복제합니다.")
    @PostMapping("/{templateId}/clone")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<ApiResponseSchema<TemplateDto>> cloneTemplate(
        @Parameter(description = "템플릿 ID") @PathVariable Long templateId) {
        return ResponseEntity.ok(templateService.cloneTemplate(templateId));
    }

    @Operation(summary = "템플릿 롤백", description = "템플릿을 특정 버전으로 롤백합니다.")
    @PostMapping("/{templateId}/rollback/{versionId}")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<ApiResponseSchema<TemplateDto>> rollbackTemplate(
        @Parameter(description = "템플릿 ID") @PathVariable Long templateId,
        @Parameter(description = "버전 ID") @PathVariable Long versionId) {
        return ResponseEntity.ok(templateService.rollbackTemplate(templateId, versionId));
    }
} 