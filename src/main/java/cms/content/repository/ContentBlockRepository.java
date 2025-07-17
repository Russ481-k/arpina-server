package cms.content.repository;

import cms.content.domain.ContentBlock;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;

public interface ContentBlockRepository extends JpaRepository<ContentBlock, Long> {

    @Query("SELECT DISTINCT cb FROM ContentBlock cb LEFT JOIN FETCH cb.files WHERE cb.menu.id = :menuId ORDER BY cb.sortOrder ASC")
    List<ContentBlock> findAllByMenu_IdOrderBySortOrderAsc(@Param("menuId") Long menuId);

    @Query("SELECT DISTINCT cb FROM ContentBlock cb LEFT JOIN FETCH cb.files WHERE cb.menu IS NULL ORDER BY cb.sortOrder ASC")
    List<ContentBlock> findAllByMenuIsNullOrderBySortOrderAsc();

    @Query("SELECT cb FROM ContentBlock cb LEFT JOIN FETCH cb.files f WHERE cb.id = :id")
    Optional<ContentBlock> findByIdWithFiles(@Param("id") Long id);

    @Modifying
    @Query("DELETE FROM ContentBlock cb WHERE cb.id IN :ids")
    void deleteByIds(@Param("ids") List<Long> ids);

}