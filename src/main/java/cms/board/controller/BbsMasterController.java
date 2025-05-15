package cms.board.controller;

import cms.board.dto.BbsMasterDto;
import cms.board.service.BbsMasterService;
import cms.common.dto.ApiResponseSchema;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/cms/bbs/master")
@RequiredArgsConstructor
@Tag(name = "cms_03_BbsMaster", description = "게시판 마스터 관리 API")
public class BbsMasterController {

    private final BbsMasterService bbsMasterService;

    @Operation(summary = "게시판 마스터 목록 조회", description = "게시판 마스터 목록을 조회합니다.")
    @GetMapping
    public ResponseEntity<ApiResponseSchema<Page<BbsMasterDto>>> getBbsMasters(Pageable pageable) {
        Page<BbsMasterDto> bbsMasters = bbsMasterService.getBbsMasters(pageable);
        return ResponseEntity.ok(ApiResponseSchema.success(bbsMasters, "게시판 마스터 목록이 성공적으로 조회되었습니다."));
    }

    @Operation(summary = "게시판 마스터 생성", description = "새로운 게시판 마스터를 생성합니다.")
    @PostMapping
    public ResponseEntity<ApiResponseSchema<BbsMasterDto>> createBbsMaster(@RequestBody BbsMasterDto bbsMasterDto) {
        BbsMasterDto createdBbsMaster = bbsMasterService.createBbsMaster(bbsMasterDto);
        return ResponseEntity.ok(ApiResponseSchema.success(createdBbsMaster, "게시판 마스터가 성공적으로 생성되었습니다."));
    }

    @Operation(summary = "게시판 마스터 수정", description = "기존 게시판 마스터를 수정합니다.")
    @PutMapping("/{bbsId}")
    public ResponseEntity<ApiResponseSchema<BbsMasterDto>> updateBbsMaster(
            @Parameter(description = "게시판 ID") @PathVariable Long bbsId,
            @RequestBody BbsMasterDto bbsMasterDto) {
        BbsMasterDto updatedBbsMaster = bbsMasterService.updateBbsMaster(bbsId, bbsMasterDto);
        return ResponseEntity.ok(ApiResponseSchema.success(updatedBbsMaster, "게시판 마스터가 성공적으로 수정되었습니다."));
    }

    @Operation(summary = "게시판 마스터 삭제", description = "기존 게시판 마스터를 삭제합니다.")
    @DeleteMapping("/{bbsId}")
    public ResponseEntity<ApiResponseSchema<Void>> deleteBbsMaster(
            @Parameter(description = "게시판 ID") @PathVariable Long bbsId) {
        bbsMasterService.deleteBbsMaster(bbsId);
        return ResponseEntity.ok(ApiResponseSchema.success("게시판 마스터가 성공적으로 삭제되었습니다."));
    }

    @Operation(summary = "게시판 마스터 조회", description = "특정 게시판 마스터의 정보를 조회합니다.")
    @GetMapping("/{bbsId}")
    public ResponseEntity<ApiResponseSchema<BbsMasterDto>> getBbsMaster(
            @Parameter(description = "게시판 ID") @PathVariable Long bbsId) {
        BbsMasterDto bbsMaster = bbsMasterService.getBbsMaster(bbsId);
        return ResponseEntity.ok(ApiResponseSchema.success(bbsMaster, "게시판 마스터 정보를 성공적으로 조회했습니다."));
    }

    @Operation(summary = "게시판 마스터 검색", description = "키워드로 게시판 마스터를 검색합니다.")
    @GetMapping("/search")
    public ResponseEntity<ApiResponseSchema<Page<BbsMasterDto>>> searchBbsMasters(
            @Parameter(description = "검색 키워드") @RequestParam String keyword,
            Pageable pageable) {
        Page<BbsMasterDto> bbsMasters = bbsMasterService.searchBbsMasters(keyword, pageable);
        return ResponseEntity.ok(ApiResponseSchema.success(bbsMasters, "게시판 마스터 검색이 성공적으로 완료되었습니다."));
    }
} 