package cms.template.repository;

import cms.template.domain.Template;
import cms.template.domain.TemplateVersion;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface TemplateVersionRepository extends JpaRepository<TemplateVersion, Long> {

    List<TemplateVersion> findByTemplateOrderByVersionNoDesc(Template template);

    Optional<TemplateVersion> findByTemplateAndVersionNo(Template template, Integer versionNo);

    @Query("SELECT MAX(v.versionNo) FROM TemplateVersion v WHERE v.template = :template")
    Optional<Integer> findMaxVersionNoByTemplate(@Param("template") Template template);

    Page<TemplateVersion> findByTemplateOrderByUpdatedAtDesc(Template template, Pageable pageable);

    @Query("SELECT tv FROM TemplateVersion tv WHERE tv.template.templateId = :templateId ORDER BY tv.updatedAt DESC")
    List<TemplateVersion> findByTemplateIdOrderByUpdatedAtDesc(@Param("templateId") Long templateId);
}  