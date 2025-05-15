package cms.content.repository;

import cms.content.domain.Content;
import cms.content.domain.ContentStatus;
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
public interface ContentRepository extends JpaRepository<Content, Long> {
    @Query("SELECT c FROM Content c WHERE " +
           "c.title LIKE CONCAT('%', :keyword, '%') OR " +
           "c.content LIKE CONCAT('%', :keyword, '%') OR " +
           "c.description LIKE CONCAT('%', :keyword, '%')")
    Page<Content> searchContents(@Param("keyword") String keyword, Pageable pageable);

    @Query("SELECT c FROM Content c WHERE c.publishedAt IS NOT NULL AND " +
           "(c.expiredAt IS NULL OR c.expiredAt > CURRENT_TIMESTAMP)")
    Page<Content> findPublishedContents(Pageable pageable);

    Optional<Content> findByIdAndStatus(Long id, ContentStatus status);

    List<Content> findByTemplateAndStatusOrderByCreatedAtDesc(Template template, ContentStatus status);

    Page<Content> findByStatus(ContentStatus status, Pageable pageable);

    @Query("SELECT c FROM Content c WHERE c.status = :status AND " +
           "(c.title LIKE CONCAT('%', :keyword, '%') OR c.description LIKE CONCAT('%', :keyword, '%'))")
    Page<Content> findByStatusAndKeyword(@Param("status") ContentStatus status,
                                       @Param("keyword") String keyword,
                                       Pageable pageable);

    @Query("SELECT c FROM Content c WHERE c.template = :template AND c.status = :status AND " +
           "(c.title LIKE CONCAT('%', :keyword, '%') OR c.description LIKE CONCAT('%', :keyword, '%'))")
    Page<Content> findByTemplateAndStatusAndKeyword(@Param("template") Template template,
                                                  @Param("status") ContentStatus status,
                                                  @Param("keyword") String keyword,
                                                  Pageable pageable);

    @Query("SELECT c FROM Content c WHERE c.template = :template AND c.status = :status AND " +
           "c.creator.username LIKE CONCAT('%', :creator, '%')")
    Page<Content> findByTemplateAndStatusAndCreator(@Param("template") Template template,
                                                  @Param("status") ContentStatus status,
                                                  @Param("creator") String creator,
                                                  Pageable pageable);
} 