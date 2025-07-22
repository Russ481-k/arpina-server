package cms.swimming.repository;

import cms.swimming.domain.Lesson;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import javax.persistence.LockModeType;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

@Repository
public interface LessonRepository extends JpaRepository<Lesson, Long>, JpaSpecificationExecutor<Lesson> {

    // 기간 내 수업 목록 조회
    @Query("SELECT l FROM Lesson l WHERE l.startDate >= :startDate AND l.endDate <= :endDate")
    List<Lesson> findByDateRange(LocalDate startDate, LocalDate endDate);

    // 특정 수업의 현재 신청 인원 카운트 쿼리
    @Query("SELECT COUNT(e) FROM Enroll e WHERE e.lesson.lessonId = :lessonId AND e.status = 'APPLIED'")
    long countCurrentEnrollments(Long lessonId);

    /**
     * Enrollment capacity control을 위한 비관적 잠금
     * 동시에 여러 사용자가 신청할 때 정원 초과를 방지
     */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT l FROM Lesson l WHERE l.lessonId = :id")
    Optional<Lesson> findByIdWithLock(@Param("id") Long id);

    @Query("SELECT l FROM Lesson l WHERE l.title = :title " +
            "AND l.lessonTime = :lessonTime " +
            "AND l.startDate >= :nextMonthStart AND l.startDate <= :nextMonthEnd")
    Optional<Lesson> findNextMonthLesson(
            @Param("title") String title,
            @Param("lessonTime") String lessonTime,
            @Param("nextMonthStart") LocalDate nextMonthStart,
            @Param("nextMonthEnd") LocalDate nextMonthEnd);

    List<Lesson> findByLessonTimeAndStartDateBetween(String lessonTime, LocalDate startDate, LocalDate endDate);
}