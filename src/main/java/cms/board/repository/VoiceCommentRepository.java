package cms.board.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;
import cms.board.domain.VoiceCommentDomain;
import java.util.List;

@Repository
public interface VoiceCommentRepository extends JpaRepository<VoiceCommentDomain, Long> {
    List<VoiceCommentDomain> findByArticleNttIdAndIsDeletedOrderByCreatedAtAsc(Long nttId, String isDeleted);
}