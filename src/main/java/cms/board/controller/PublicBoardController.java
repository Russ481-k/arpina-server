package cms.board.controller;

import cms.board.dto.BbsMasterDto;
import cms.board.service.BbsMasterService;
import cms.common.dto.ApiResponseSchema;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/bbs")
@RequiredArgsConstructor
@Tag(name = "public_board", description = "공개 게시판 API")
public class PublicBoardController {

    private final BbsMasterService bbsMasterService;

    @Operation(summary = "게시판 정보 조회", description = "일반 사용자가 접근할 수 있는 게시판 정보를 조회합니다.")
    @GetMapping("/{bbsId}/info")
    public ResponseEntity<ApiResponseSchema<BbsMasterDto>> getPublicBoardInfo(
            @Parameter(description = "게시판 ID") @PathVariable Long bbsId) {
        BbsMasterDto boardInfo = bbsMasterService.getBbsMaster(bbsId);
        return ResponseEntity.ok(ApiResponseSchema.success(boardInfo, "게시판 정보를 성공적으로 조회했습니다."));
    }
} 