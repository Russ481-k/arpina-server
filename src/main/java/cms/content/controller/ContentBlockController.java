package cms.content.controller;

import cms.content.dto.ContentBlockCreateRequest;
import cms.content.dto.ContentBlockReorderRequest;
import cms.content.dto.ContentBlockResponse;
import cms.content.dto.ContentBlockUpdateRequest;
import cms.content.dto.ContentBlockHistoryResponse;
import cms.content.service.ContentBlockService;
import cms.common.dto.ApiResponseSchema;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import javax.validation.Valid;
import java.util.List;

@RestController
@RequestMapping("/cms")
@RequiredArgsConstructor
public class ContentBlockController {

    private final ContentBlockService contentBlockService;

    @GetMapping("/public/contents/main")
    public ResponseEntity<ApiResponseSchema<List<ContentBlockResponse>>> getPublicMainPageContentBlocks() {
        List<ContentBlockResponse> contentBlocks = contentBlockService.getContentBlocksForMainPage();
        return ResponseEntity.ok(ApiResponseSchema.success(contentBlocks, "메인 페이지 콘텐츠 블록 목록이 성공적으로 조회되었습니다."));
    }

    @GetMapping("/public/menus/{menuId}/contents")
    public ResponseEntity<ApiResponseSchema<List<ContentBlockResponse>>> getPublicContentBlocks(
            @PathVariable Long menuId) {
        List<ContentBlockResponse> contentBlocks = contentBlockService.getContentBlocksByMenu(menuId);
        return ResponseEntity.ok(ApiResponseSchema.success(contentBlocks, "콘텐츠 블록 목록이 성공적으로 조회되었습니다."));
    }

    @GetMapping("/contents/main")
    public ResponseEntity<ApiResponseSchema<List<ContentBlockResponse>>> getMainPageContentBlocks() {
        List<ContentBlockResponse> contentBlocks = contentBlockService.getContentBlocksForMainPage();
        return ResponseEntity.ok(ApiResponseSchema.success(contentBlocks, "메인 페이지 콘텐츠 블록 목록이 성공적으로 조회되었습니다."));
    }

    @PostMapping("/contents/main")
    public ResponseEntity<ApiResponseSchema<ContentBlockResponse>> createMainPageContentBlock(
            @Valid @RequestBody ContentBlockCreateRequest request) {
        ContentBlockResponse createdContentBlock = contentBlockService.createContentBlockForMainPage(request);
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(ApiResponseSchema.success(createdContentBlock, "메인 페이지 콘텐츠 블록이 성공적으로 생성되었습니다."));
    }

    @GetMapping("/menus/{menuId}/contents")
    public ResponseEntity<ApiResponseSchema<List<ContentBlockResponse>>> getContentBlocks(@PathVariable Long menuId) {
        List<ContentBlockResponse> contentBlocks = contentBlockService.getContentBlocksByMenu(menuId);
        return ResponseEntity.ok(ApiResponseSchema.success(contentBlocks, "콘텐츠 블록 목록이 성공적으로 조회되었습니다."));
    }

    @PostMapping("/menus/{menuId}/contents")
    public ResponseEntity<ApiResponseSchema<ContentBlockResponse>> createContentBlock(@PathVariable Long menuId,
            @Valid @RequestBody ContentBlockCreateRequest request) {
        ContentBlockResponse createdContentBlock = contentBlockService.createContentBlock(menuId, request);
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(ApiResponseSchema.success(createdContentBlock, "콘텐츠 블록이 성공적으로 생성되었습니다."));
    }

    @PutMapping("/contents/{contentId}")
    public ResponseEntity<ApiResponseSchema<ContentBlockResponse>> updateContentBlock(@PathVariable Long contentId,
            @Valid @RequestBody ContentBlockUpdateRequest request) {
        ContentBlockResponse updatedContentBlock = contentBlockService.updateContentBlock(contentId, request);
        return ResponseEntity.ok(ApiResponseSchema.success(updatedContentBlock, "콘텐츠 블록이 성공적으로 수정되었습니다."));
    }

    @PutMapping("/contents/reorder")
    public ResponseEntity<ApiResponseSchema<Void>> reorderContentBlocks(
            @Valid @RequestBody ContentBlockReorderRequest request) {
        contentBlockService.reorderContentBlocks(request);
        return ResponseEntity.ok(ApiResponseSchema.success("콘텐츠 블록 순서가 성공적으로 변경되었습니다."));
    }

    @DeleteMapping("/contents/{contentId}")
    public ResponseEntity<ApiResponseSchema<Void>> deleteContentBlock(@PathVariable Long contentId) {
        contentBlockService.deleteContentBlock(contentId);
        return ResponseEntity.ok(ApiResponseSchema.success("콘텐츠 블록이 성공적으로 삭제되었습니다."));
    }

    @GetMapping("/contents/{contentId}/history")
    public ResponseEntity<ApiResponseSchema<List<ContentBlockHistoryResponse>>> getHistory(
            @PathVariable Long contentId) {
        List<ContentBlockHistoryResponse> history = contentBlockService.getHistoryByContentBlockId(contentId);
        return ResponseEntity.ok(ApiResponseSchema.success(history, "콘텐츠 블록 히스토리가 성공적으로 조회되었습니다."));
    }

    @PostMapping("/contents/history/{historyId}/restore")
    public ResponseEntity<ApiResponseSchema<ContentBlockResponse>> restoreFromHistory(@PathVariable Long historyId) {
        ContentBlockResponse restoredContentBlock = contentBlockService.restoreFromHistory(historyId);
        return ResponseEntity.ok(ApiResponseSchema.success(restoredContentBlock, "콘텐츠 블록이 성공적으로 복원되었습니다."));
    }
}