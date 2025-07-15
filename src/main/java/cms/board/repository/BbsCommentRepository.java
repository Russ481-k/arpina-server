package cms.board.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;
import cms.board.domain.BbsCommentDomain;
import java.util.List;

@Repository
public interface BbsCommentRepository extends JpaRepository<BbsCommentDomain, Long> {
    List<BbsCommentDomain> findByArticleNttIdAndIsDeletedOrderByCreatedAtAsc(Long nttId, String isDeleted);
}