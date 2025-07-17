package cms.board.repository;

import cms.board.domain.BbsArticleCategoryDomain;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

@Repository
public interface BbsArticleCategoryRepository
        extends JpaRepository<BbsArticleCategoryDomain, BbsArticleCategoryDomain.BbsArticleCategoryId> {

    /**
     * 특정 게시글 ID에 해당하는 모든 카테고리 연결 정보를 삭제합니다.
     * 
     * @param nttId 게시글 ID
     */
    @Transactional
    void deleteByArticleNttId(Long nttId);
}