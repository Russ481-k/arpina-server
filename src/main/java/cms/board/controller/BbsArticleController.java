package cms.board.controller;

import cms.board.dto.BbsArticleDto;
import cms.board.service.BbsArticleService;
import cms.common.dto.ApiResponseSchema;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.http.MediaType;

import javax.validation.Valid;
import java.util.List;

@RestController
@RequestMapping("/cms/bbs/article")
@RequiredArgsConstructor
@Tag(name = "cms_04_BbsArticle", description = "게시글 관리 API")
public class BbsArticleController {

    private final BbsArticleService bbsArticleService;

    @Operation(summary = "게시글 목록 조회", description = "특정 게시판의 게시글 목록을 조회합니다.")
    @GetMapping
    public ResponseEntity<ApiResponseSchema<Page<BbsArticleDto>>> getArticles(
            @RequestParam Long bbsId,
            @RequestParam Long menuId,
            @RequestParam(required = false) Long categoryId,
            @RequestParam(required = false) String keyword,
            Pageable pageable,
            Authentication authentication) {

        boolean isAdmin = authentication != null &&
                authentication.getAuthorities().stream()
                        .anyMatch(grantedAuthority -> grantedAuthority.getAuthority().equals("ROLE_ADMIN"));

        Page<BbsArticleDto> articles;
        if (keyword != null && !keyword.trim().isEmpty()) {
            articles = bbsArticleService.searchArticles(bbsId, menuId, keyword, pageable, isAdmin);
        } else if (categoryId != null) {
            articles = bbsArticleService.getArticles(bbsId, menuId, categoryId, pageable, isAdmin);
        } else {
            articles = bbsArticleService.getArticles(bbsId, menuId, pageable, isAdmin);
        }

        return ResponseEntity.ok(ApiResponseSchema.success(articles));
    }

    @Operation(summary = "게시글 상세 조회", description = "특정 게시글의 상세 정보를 조회합니다.")
    @GetMapping("/{nttId}")
    public ResponseEntity<ApiResponseSchema<BbsArticleDto>> getArticle(
            @Parameter(description = "게시글 ID") @PathVariable Long nttId) {
        return ResponseEntity.ok(ApiResponseSchema.success(bbsArticleService.getArticle(nttId), "게시글을 성공적으로 조회했습니다."));
    }

    @Operation(summary = "게시글 생성", description = "새로운 게시글을 생성합니다.")
    @PostMapping(consumes = { MediaType.MULTIPART_FORM_DATA_VALUE })
    public ResponseEntity<ApiResponseSchema<Long>> createArticle(
            @RequestPart("articleData") @Valid BbsArticleDto articleDto,
            @RequestPart(value = "editorContentJson", required = false) String editorContentJson,
            @RequestPart(value = "mediaFiles", required = false) List<MultipartFile> mediaFiles,
            @RequestPart(value = "mediaLocalIds", required = false) String mediaLocalIds,
            @RequestPart(value = "attachments", required = false) List<MultipartFile> attachments) {
        BbsArticleDto createdArticle = bbsArticleService.createArticle(articleDto, editorContentJson, mediaFiles,
                mediaLocalIds, attachments);
        return ResponseEntity.ok(ApiResponseSchema.success(createdArticle.getNttId(), "게시글이 성공적으로 생성되었습니다."));
    }

    @Operation(summary = "게시글 수정", description = "기존 게시글을 수정합니다.")
    @PutMapping(value = "/{nttId}", consumes = { MediaType.MULTIPART_FORM_DATA_VALUE })
    public ResponseEntity<ApiResponseSchema<Void>> updateArticle(
            @PathVariable Long nttId,
            @RequestPart("articleData") @Valid BbsArticleDto articleDto,
            @RequestPart(value = "editorContentJson", required = false) String editorContentJson,
            @RequestPart(value = "mediaFiles", required = false) List<MultipartFile> mediaFiles,
            @RequestPart(value = "mediaLocalIds", required = false) String mediaLocalIds,
            @RequestPart(value = "attachments", required = false) List<MultipartFile> attachments) {
        bbsArticleService.updateArticle(nttId, articleDto, editorContentJson, mediaFiles, mediaLocalIds, attachments);
        return ResponseEntity.ok(ApiResponseSchema.success(null, "게시글이 성공적으로 수정되었습니다."));
    }

    @Operation(summary = "게시글 삭제", description = "게시글을 삭제합니다.")
    @DeleteMapping("/{nttId}")
    public ResponseEntity<ApiResponseSchema<Void>> deleteArticle(
            @Parameter(description = "게시글 ID") @PathVariable Long nttId) {
        bbsArticleService.deleteArticle(nttId);
        return ResponseEntity.ok(ApiResponseSchema.success("게시글이 성공적으로 삭제되었습니다."));
    }
}