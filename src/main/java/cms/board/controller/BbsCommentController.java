package cms.board.controller;

import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.web.bind.annotation.*;
import cms.common.util.IpUtil;

import javax.servlet.http.HttpServletRequest;
import javax.validation.Valid;
import java.util.List;
import lombok.RequiredArgsConstructor;

import cms.board.service.BbsCommentService;
import cms.board.dto.BbsCommentDto;
import cms.board.dto.BbsCommentRequest;

@RestController
@RequestMapping("/cms/bbs/voice/read/{nttId}/comments") // /cms 경로 추가
@RequiredArgsConstructor
public class BbsCommentController {
    private final BbsCommentService bbsCommentService;

    @PostMapping // "/read/{nttId}"가 상위 경로로 이동했으므로 여기서는 경로가 필요 없음
    public ResponseEntity<BbsCommentDto> createComment(
            @PathVariable Long nttId,
            @Valid @RequestBody BbsCommentRequest request,
            @AuthenticationPrincipal UserDetails userDetails,
            HttpServletRequest httpRequest) {
        String adminId = userDetails.getUsername();
        String ipAddress = IpUtil.getClientIp();
        return ResponseEntity.ok(bbsCommentService.createComment(nttId, request, adminId, ipAddress));
    }

    @GetMapping // 마찬가지로 경로 필요 없음
    public ResponseEntity<List<BbsCommentDto>> getComments(@PathVariable Long nttId) {
        return ResponseEntity.ok(bbsCommentService.getComments(nttId));
    }

    @PutMapping("/{commentId}") // commentId로 특정 댓글을 지정
    public ResponseEntity<Void> updateComment(
            @PathVariable Long nttId,
            @PathVariable Long commentId,
            @Valid @RequestBody BbsCommentRequest request,
            @AuthenticationPrincipal UserDetails userDetails,
            HttpServletRequest httpRequest) {
        String adminId = userDetails.getUsername();
        String ipAddress = IpUtil.getClientIp();
        bbsCommentService.updateComment(commentId, request, adminId, ipAddress);
        return ResponseEntity.ok().build();
    }

    @DeleteMapping("/{commentId}") // commentId로 특정 댓글을 지정
    public ResponseEntity<Void> deleteComment(
            @PathVariable Long nttId,
            @PathVariable Long commentId) {
        bbsCommentService.deleteComment(commentId);
        return ResponseEntity.ok().build();
    }
}