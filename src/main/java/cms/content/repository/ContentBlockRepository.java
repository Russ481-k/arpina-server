package cms.content.repository;

import cms.content.domain.ContentBlock;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;

public interface ContentBlockRepository extends JpaRepository<ContentBlock, Long> {

    List<ContentBlock> findAllByMenu_IdOrderBySortOrderAsc(Long menuId);

    List<ContentBlock> findAllByMenuIsNullOrderBySortOrderAsc();

    @Modifying
    @Query("DELETE FROM ContentBlock cb WHERE cb.id IN :ids")
    void deleteAllByIdIn(@Param("ids") List<Long> ids);
}