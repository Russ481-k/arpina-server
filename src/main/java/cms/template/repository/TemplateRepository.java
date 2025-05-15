package cms.template.repository;

import cms.template.domain.Template;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface TemplateRepository extends JpaRepository<Template, Long> {

    @Query("SELECT t FROM Template t WHERE " +
           "(:keyword IS NULL OR t.templateName LIKE CONCAT('%', :keyword, '%') OR t.description LIKE CONCAT('%', :keyword, '%')) " +
           "AND t.deleted = false")
    Page<Template> findByKeyword(@Param("keyword") String keyword, Pageable pageable);

    @Query("SELECT t FROM Template t WHERE t.published = true AND " +
           "(:keyword IS NULL OR t.templateName LIKE CONCAT('%', :keyword, '%') OR t.description LIKE CONCAT('%', :keyword, '%')) " +
           "AND t.deleted = false")
    Page<Template> findByPublishedTrue(@Param("keyword") String keyword, Pageable pageable);

    @Query("SELECT t FROM Template t WHERE t.templateId = :templateId AND t.published = :published AND t.deleted = false")
    Optional<Template> findByTemplateIdAndPublished(@Param("templateId") Long templateId, @Param("published") boolean published);

    @Query("SELECT t FROM Template t WHERE t.templateId = :templateId AND t.deleted = false")
    Optional<Template> findByTemplateId(@Param("templateId") Long templateId);
    
    @Query("SELECT t FROM Template t WHERE t.templateName = :templateName AND t.versionNo < :versionNo AND t.deleted = false")
    List<Template> findOldVersionsByTemplateName(@Param("templateName") String templateName, @Param("versionNo") int versionNo);
    
    @Query("UPDATE Template t SET t.deleted = true WHERE t.templateName = :templateName AND t.versionNo < :versionNo")
    void softDeleteOldVersions(@Param("templateName") String templateName, @Param("versionNo") int versionNo);
} 
 
 
 