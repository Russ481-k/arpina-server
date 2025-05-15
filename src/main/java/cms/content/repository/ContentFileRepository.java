package cms.content.repository;

import cms.content.domain.Content;
import cms.content.domain.ContentFile;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface ContentFileRepository extends JpaRepository<ContentFile, Long> {
    List<ContentFile> findByContent(Content content);
    
    void deleteByContent(Content content);
} 