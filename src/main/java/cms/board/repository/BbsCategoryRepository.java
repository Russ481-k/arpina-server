package cms.board.repository;

import cms.board.domain.BbsCategoryDomain;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface BbsCategoryRepository extends JpaRepository<BbsCategoryDomain, Long> {
    List<BbsCategoryDomain> findAllByBbsMaster_BbsIdAndDisplayYnOrderBySortOrderAsc(Long bbsId, String displayYn);
}