package cms.locker.repository;

import cms.locker.domain.LockerInventory;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public interface LockerInventoryRepository extends JpaRepository<LockerInventory, String> {
    Optional<LockerInventory> findByGender(String gender);
} 