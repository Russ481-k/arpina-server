package cms.user.repository;

import cms.user.domain.UserMemo;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public interface UserMemoRepository extends JpaRepository<UserMemo, Long> {
    Optional<UserMemo> findByUserUuid(String userUuid);
} 