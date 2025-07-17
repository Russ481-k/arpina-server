package cms.board.controller;

import cms.board.dto.BbsCategoryDto;
import cms.board.service.BbsCategoryService;
import cms.common.dto.ApiResponseSchema;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/cms/bbs")
@RequiredArgsConstructor
@Tag(name = "cms_05_BbsCategory", description = "게시판 카테고리 관리 API")
public class BbsCategoryController {

    private final BbsCategoryService bbsCategoryService;

    @Operation(summary = "게시판별 카테고리 목록 조회", description = "특정 게시판에 속한 카테고리 목록을 조회합니다.")
    @GetMapping("/{bbsId}/categories")
    public ResponseEntity<ApiResponseSchema<List<BbsCategoryDto>>> getCategoriesByBbsId(
            @Parameter(description = "게시판 ID") @PathVariable Long bbsId) {
        List<BbsCategoryDto> categories = bbsCategoryService.getCategoriesByBbsId(bbsId);
        return ResponseEntity.ok(ApiResponseSchema.success(categories, "카테고리 목록을 성공적으로 조회했습니다."));
    }
}