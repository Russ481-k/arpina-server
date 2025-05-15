package cms.user.repository;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import cms.user.domain.UserActivityLog;

import java.time.LocalDateTime;
import java.util.List;

@Repository
public interface UserActivityLogRepository extends JpaRepository<UserActivityLog, String> {
    List<UserActivityLog> findByUserUuidOrderByCreatedAtDesc(String userUuid);
    List<UserActivityLog> findByUserUuidAndCreatedAtBetweenOrderByCreatedAtDesc(String userUuid, LocalDateTime startDate, LocalDateTime endDate);
    Page<UserActivityLog> findByUserUuid(String userUuid, Pageable pageable);
} 