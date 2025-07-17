package cms.content.repository;

import cms.content.domain.ContentBlockHistory;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface ContentBlockHistoryRepository extends JpaRepository<ContentBlockHistory, Long> {

    List<ContentBlockHistory> findByContentBlock_IdOrderByVersionDesc(Long contentBlockId);

    Optional<ContentBlockHistory> findFirstByContentBlock_IdOrderByVersionAsc(Long contentBlockId);

    long countByContentBlock_Id(Long contentBlockId);

    Optional<ContentBlockHistory> findTopByContentBlock_IdOrderByVersionDesc(Long contentBlockId);

}