package cms.swimming.repository;

import cms.swimming.domain.Locker;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface LockerRepository extends JpaRepository<Locker, Long> {
    
    // 성별과 활성화 상태로 사물함 조회
    List<Locker> findByGenderAndIsActive(Locker.LockerGender gender, Boolean isActive);
    
    // 특정 구역의 사물함 조회
    List<Locker> findByZoneAndIsActive(String zone, Boolean isActive);
    
    // 사용 가능한 (신청되지 않은) 사물함 조회
//    @Query("SELECT l FROM Locker l WHERE l.isActive = true AND l.gender = :gender AND " +
//           "l.lockerId NOT IN (SELECT e.locker.lockerId FROM Enroll e WHERE e.status = 'APPLIED' AND e.locker IS NOT NULL)")
//    List<Locker> findAvailableLockers(Locker.LockerGender gender);
} 