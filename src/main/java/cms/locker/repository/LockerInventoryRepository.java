package cms.locker.repository;

import cms.locker.domain.LockerInventory;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.stereotype.Repository;

import javax.persistence.LockModeType;
import java.util.Optional;

@Repository
public interface LockerInventoryRepository extends JpaRepository<LockerInventory, String> {
    // 성별(PK)로 LockerInventory 조회 시 비관적 쓰기 잠금 적용
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    Optional<LockerInventory> findByGender(String gender);
} 