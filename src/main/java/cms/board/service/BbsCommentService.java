package cms.board.service;

import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import javax.persistence.EntityNotFoundException;
import java.util.List;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import cms.board.domain.BbsCommentDomain;
import cms.board.domain.BbsArticleDomain;
import cms.board.dto.BbsCommentDto;
import cms.board.dto.BbsCommentRequest;
import cms.board.repository.BbsCommentRepository;
import cms.board.repository.BbsArticleRepository;

@Service
@RequiredArgsConstructor
@Transactional
public class BbsCommentService {

    private final BbsCommentRepository BbsCommentRepository;
    private final BbsArticleRepository articleRepository;

    @PreAuthorize("hasRole('ROLE_ADMIN')")
    public BbsCommentDto createComment(Long nttId, BbsCommentRequest request, String adminId, String ipAddress) {
        BbsArticleDomain article = articleRepository.findById(nttId)
                .orElseThrow(() -> new EntityNotFoundException("게시글을 찾을 수 없습니다."));

        BbsCommentDomain comment = new BbsCommentDomain();
        comment.setArticle(article);
        comment.setContent(request.getContent());
        comment.setWriter(adminId);
        comment.setDisplayWriter(request.getDisplayWriter());
        comment.setCreatedBy(adminId);
        comment.setCreatedIp(ipAddress);

        return convertToDto(BbsCommentRepository.save(comment));
    }

    public List<BbsCommentDto> getComments(Long nttId) {
        return BbsCommentRepository.findByArticleNttIdAndIsDeletedOrderByCreatedAtAsc(nttId, "N")
                .stream()
                .map(this::convertToDto)
                .collect(Collectors.toList());
    }

    @PreAuthorize("hasRole('ROLE_ADMIN')")
    public void updateComment(Long commentId, BbsCommentRequest request, String adminId, String ipAddress) {
        BbsCommentDomain comment = BbsCommentRepository.findById(commentId)
                .orElseThrow(() -> new EntityNotFoundException("댓글을 찾을 수 없습니다."));

        comment.setContent(request.getContent());
        comment.setDisplayWriter(request.getDisplayWriter());
        comment.setUpdatedBy(adminId);
        comment.setUpdatedIp(ipAddress);
    }

    @PreAuthorize("hasRole('ROLE_ADMIN')")
    public void deleteComment(Long commentId) {
        BbsCommentDomain comment = BbsCommentRepository.findById(commentId)
                .orElseThrow(() -> new EntityNotFoundException("댓글을 찾을 수 없습니다."));

        comment.setIsDeleted("Y");
    }

    private BbsCommentDto convertToDto(BbsCommentDomain domain) {
        BbsCommentDto dto = new BbsCommentDto();
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