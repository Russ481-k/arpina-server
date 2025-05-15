package cms.enterprise.controller;

import cms.common.dto.ApiResponseSchema;
import cms.enterprise.dto.CreateEnterpriseRequest;
import cms.enterprise.dto.EnterpriseDto;
import cms.enterprise.dto.UpdateEnterpriseRequest;
import cms.enterprise.service.EnterpriseService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import javax.servlet.http.HttpServletRequest;
import javax.validation.Valid;

@RestController
@RequestMapping("/cms/enterprises") // URL 수정: API 버전과 경로 수정
@RequiredArgsConstructor
@Tag(name = "cms_enterprise", description = "입주 기업 관리 API")
@Validated
public class EnterpriseController {

    private final EnterpriseService enterpriseService;

    @Operation(summary = "입주 기업 목록 조회", description = "등록된 입주 기업 목록을 조회합니다. 연도별 필터링 및 페이지네이션을 지원합니다.")
    @GetMapping
    public ResponseEntity<ApiResponseSchema<Page<EnterpriseDto>>> getAllEnterprises(
            @Parameter(description = "검색할 연도 (예: 2024)") @RequestParam(required = false) Integer year,
            @Parameter(description = "검색할 기업명 (부분 일치)") @RequestParam(required = false) String name,
            @Parameter(description = "검색할 대표자명 (부분 일치)") @RequestParam(required = false) String representative,
            @Parameter(description = "검색할 업종 (부분 일치)") @RequestParam(required = false) String businessType,
            @PageableDefault(size = 10, sort = "createdAt", direction = Sort.Direction.DESC) Pageable pageable) {
        
        Page<EnterpriseDto> enterprisesPage = enterpriseService.getAllEnterprises(year, name, representative, businessType, pageable);
        return ResponseEntity.ok(ApiResponseSchema.success(enterprisesPage, "성공적으로 기업 목록을 조회했습니다."));
    }

    @Operation(summary = "입주 기업 상세 조회", description = "특정 ID를 가진 입주 기업의 상세 정보를 조회합니다.")
    @GetMapping("/{id}")
    public ResponseEntity<ApiResponseSchema<EnterpriseDto>> getEnterpriseById(
            @Parameter(description = "조회할 기업의 ID") @PathVariable Long id) {
        EnterpriseDto enterpriseDto = enterpriseService.getEnterpriseById(id);
        return ResponseEntity.ok(ApiResponseSchema.success(enterpriseDto, "기업 상세 정보를 성공적으로 조회했습니다."));
    }

    @Operation(summary = "입주 기업 생성", description = "새로운 입주 기업 정보와 이미지를 등록합니다.")
    @PostMapping(consumes = {MediaType.MULTIPART_FORM_DATA_VALUE})
    public ResponseEntity<ApiResponseSchema<EnterpriseDto>> createEnterprise(
            @RequestPart("data") @Valid CreateEnterpriseRequest createRequest,
            @RequestPart(value = "image", required = false) MultipartFile imageFile,
            HttpServletRequest request, Authentication authentication) {
        
        String username = (authentication != null && authentication.isAuthenticated()) ? authentication.getName() : "anonymousUser";
        String clientIp = request.getRemoteAddr();

        EnterpriseDto createdEnterprise = enterpriseService.createEnterprise(createRequest, imageFile, username, clientIp);
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(ApiResponseSchema.success(createdEnterprise, "새로운 기업 정보가 성공적으로 등록되었습니다."));
    }

    @Operation(summary = "입주 기업 정보 수정", description = "기존 입주 기업의 정보와 이미지를 수정합니다.")
    @PutMapping(value = "/{id}", consumes = {MediaType.MULTIPART_FORM_DATA_VALUE})
    public ResponseEntity<ApiResponseSchema<EnterpriseDto>> updateEnterprise(
            @Parameter(description = "수정할 기업의 ID") @PathVariable Long id,
            @RequestPart("data") @Valid UpdateEnterpriseRequest updateRequest,
            @RequestPart(value = "image", required = false) MultipartFile imageFile,
            HttpServletRequest request, Authentication authentication) {

        String username = (authentication != null && authentication.isAuthenticated()) ? authentication.getName() : "anonymousUser";
        String clientIp = request.getRemoteAddr();

        EnterpriseDto updatedEnterprise = enterpriseService.updateEnterprise(id, updateRequest, imageFile, username, clientIp);
        return ResponseEntity.ok(ApiResponseSchema.success(updatedEnterprise, "기업 정보가 성공적으로 수정되었습니다."));
    }

    @Operation(summary = "입주 기업 정보 삭제", description = "특정 ID를 가진 입주 기업 정보를 시스템에서 삭제합니다.")
    @DeleteMapping("/{id}")
    public ResponseEntity<ApiResponseSchema<Void>> deleteEnterprise(
            @Parameter(description = "삭제할 기업의 ID") @PathVariable Long id) {
        enterpriseService.deleteEnterprise(id);
        return ResponseEntity.ok(ApiResponseSchema.success(null, "기업 정보가 성공적으로 삭제되었습니다."));
    }
} 