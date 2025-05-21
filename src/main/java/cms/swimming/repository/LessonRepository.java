package cms.swimming.repository;

import cms.swimming.domain.Lesson;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.LocalDate;
import java.util.List;

@Repository
public interface LessonRepository extends JpaRepository<Lesson, Long>, JpaSpecificationExecutor<Lesson> {
    
    // 상태가 OPEN인 수업 목록 조회
    Page<Lesson> findByStatus(Lesson.LessonStatus status, Pageable pageable);
    
    // 기간 내 수업 목록 조회
    @Query("SELECT l FROM Lesson l WHERE l.startDate >= :startDate AND l.endDate <= :endDate AND l.status = :status")
    List<Lesson> findByDateRangeAndStatus(LocalDate startDate, LocalDate endDate, Lesson.LessonStatus status);
    
    // 특정 수업의 현재 신청 인원 카운트 쿼리
    @Query("SELECT COUNT(e) FROM Enroll e WHERE e.lesson.lessonId = :lessonId AND e.status = 'APPLIED'")
    long countCurrentEnrollments(Long lessonId);
} 