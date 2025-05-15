package cms.file.repository;

import cms.file.domain.FileMetaDomain;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;
import java.util.List;

@Repository
public interface FileMetaRepository extends JpaRepository<FileMetaDomain, Long> {
    List<FileMetaDomain> findByModuleAndModuleId(String module, Long moduleId);
} 