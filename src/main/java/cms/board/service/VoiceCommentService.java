package cms.board.service;

import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import javax.persistence.EntityNotFoundException;
import java.util.List;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import cms.board.domain.VoiceCommentDomain;
import cms.board.domain.BbsArticleDomain;
import cms.board.dto.VoiceCommentDto;
import cms.board.dto.VoiceCommentRequest;
import cms.board.repository.VoiceCommentRepository;
import cms.board.repository.BbsArticleRepository;

@Service
@RequiredArgsConstructor
@Transactional
public class VoiceCommentService {

    private final VoiceCommentRepository commentRepository;
    private final BbsArticleRepository articleRepository;

    @PreAuthorize("hasRole('ROLE_ADMIN')")
    public VoiceCommentDto createComment(Long nttId, VoiceCommentRequest request, String adminId, String ipAddress) {
        BbsArticleDomain article = articleRepository.findById(nttId)
                .orElseThrow(() -> new EntityNotFoundException("게시글을 찾을 수 없습니다."));

        VoiceCommentDomain comment = new VoiceCommentDomain();
        comment.setArticle(article);
        comment.setContent(request.getContent());
        comment.setWriter(adminId);
        comment.setDisplayWriter(request.getDisplayWriter());
        comment.setCreatedBy(adminId);
        comment.setCreatedIp(ipAddress);

        return convertToDto(commentRepository.save(comment));
    }

    public List<VoiceCommentDto> getComments(Long nttId) {
        return commentRepository.findByArticleNttIdAndIsDeletedOrderByCreatedAtAsc(nttId, "N")
                .stream()
                .map(this::convertToDto)
                .collect(Collectors.toList());
    }

    @PreAuthorize("hasRole('ROLE_ADMIN')")
    public void updateComment(Long commentId, VoiceCommentRequest request, String adminId, String ipAddress) {
        VoiceCommentDomain comment = commentRepository.findById(commentId)
                .orElseThrow(() -> new EntityNotFoundException("댓글을 찾을 수 없습니다."));

        comment.setContent(request.getContent());
        comment.setDisplayWriter(request.getDisplayWriter());
        comment.setUpdatedBy(adminId);
        comment.setUpdatedIp(ipAddress);
    }

    @PreAuthorize("hasRole('ROLE_ADMIN')")
    public void deleteComment(Long commentId) {
        VoiceCommentDomain comment = commentRepository.findById(commentId)
                .orElseThrow(() -> new EntityNotFoundException("댓글을 찾을 수 없습니다."));

        comment.setIsDeleted("Y");
    }

    private VoiceCommentDto convertToDto(VoiceCommentDomain domain) {
        VoiceCommentDto dto = new VoiceCommentDto();
        dto.setCommentId(domain.getCommentId());
        dto.setNttId(domain.getArticle().getNttId());
        dto.setContent(domain.getContent());
        dto.setWriter(domain.getWriter());
        dto.setDisplayWriter(domain.getDisplayWriter());
        dto.setCreatedAt(domain.getCreatedAt());
        dto.setUpdatedAt(domain.getUpdatedAt());
        dto.setCreatedBy(domain.getCreatedBy());
        return dto;
    }
}