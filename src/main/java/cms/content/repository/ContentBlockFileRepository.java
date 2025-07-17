package cms.content.repository;

import cms.content.domain.ContentBlockFile;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface ContentBlockFileRepository extends JpaRepository<ContentBlockFile, Long> {

    @Modifying
    @Query("DELETE FROM ContentBlockFile cbf WHERE cbf.contentBlock.id = :contentBlockId")
    void deleteAllByContentBlockId(@Param("contentBlockId") Long contentBlockId);
}