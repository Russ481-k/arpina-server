package cms.swimming.repository;

import cms.swimming.domain.Enroll;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.stereotype.Repository;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

@Repository
public interface EnrollRepository extends JpaRepository<Enroll, Long> {
    
    // 사용자ID로 신청 내역 조회
    List<Enroll> findByUserId(Long userId);
    
    // 사용자ID와 상태로 신청 내역 조회
    Page<Enroll> findByUserIdAndStatus(Long userId, Enroll.EnrollStatus status, Pageable pageable);
    
    // 특정 강습의 신청 내역 조회
    Page<Enroll> findByLessonLessonId(Long lessonId, Pageable pageable);
    
    // 사용자ID와 강습ID로 신청 내역 조회 (중복 신청 방지용)
    Optional<Enroll> findByUserIdAndLessonLessonIdAndStatus(Long userId, Long lessonId, Enroll.EnrollStatus status);
    
    // 특정 강습의 성별별 사물함 신청 수 조회
    @Query("SELECT COUNT(e) FROM Enroll e JOIN e.locker l WHERE e.lesson.lessonId = :lessonId AND l.gender = :gender AND e.status = 'APPLIED'")
    long countLockersByGender(Long lessonId, String gender);
    
    // 같은 달에 사용자의 신청 내역 확인 (같은 달 중복 신청 방지용)
    @Query("SELECT COUNT(e) FROM Enroll e WHERE e.userId = :userId AND e.status = 'APPLIED' " +
           "AND FUNCTION('YEAR', e.lesson.startDate) = FUNCTION('YEAR', :date) " +
           "AND FUNCTION('MONTH', e.lesson.startDate) = FUNCTION('MONTH', :date)")
    long countUserEnrollmentsByMonth(Long userId, LocalDate date);
} 