package cms.board.repository;

import cms.board.domain.BbsMasterDomain;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.lang.NonNull;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface BbsMasterRepository extends JpaRepository<BbsMasterDomain, Long> {
    
    @Query("SELECT b FROM BbsMasterDomain b WHERE b.displayYn = 'Y' ORDER BY b.sortOrder ASC, b.createdAt DESC")
    @NonNull
    Page<BbsMasterDomain> findAll(@NonNull Pageable pageable);

    @Query("SELECT b FROM BbsMasterDomain b WHERE b.displayYn = 'Y' ORDER BY b.sortOrder ASC, b.createdAt DESC")
    List<BbsMasterDomain> findDisplayedBoards();

    @Query("SELECT b FROM BbsMasterDomain b WHERE b.displayYn = 'Y' AND b.bbsName LIKE %:keyword% ORDER BY b.sortOrder ASC, b.createdAt DESC")
    Page<BbsMasterDomain> findByBbsNameContaining(@Param("keyword") String keyword, Pageable pageable);
} 