package cms.enroll.repository;

import cms.enroll.domain.Enroll;
import cms.swimming.domain.Locker;
import cms.user.domain.User;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

@Repository
public interface EnrollRepository extends JpaRepository<Enroll, Long> {
    List<Enroll> findByUserOrderByCreatedAtDesc(User user);
    List<Enroll> findByUserAndStatusOrderByCreatedAtDesc(User user, String status);

    List<Enroll> findByUserUuid(String userUuid);

    Page<Enroll> findByUserUuidAndStatus(String userUuid, String status, Pageable pageable);

    Page<Enroll> findByLessonLessonId(Long lessonId, Pageable pageable);

    Optional<Enroll> findByUserUuidAndLessonLessonIdAndStatus(String userUuid, Long lessonId, String status);

    @Query("SELECT COUNT(e) FROM Enroll e JOIN e.locker l WHERE e.lesson.lessonId = :lessonId AND l.gender = :gender AND e.status = 'APPLIED'")
    long countLockersByGender(@Param("lessonId") Long lessonId, @Param("gender") Locker.LockerGender gender);

    @Query("SELECT COUNT(e) FROM Enroll e WHERE e.user.uuid = :userUuid AND e.status = 'APPLIED' " +
           "AND FUNCTION('YEAR', e.lesson.startDate) = FUNCTION('YEAR', :date) " +
           "AND FUNCTION('MONTH', e.lesson.startDate) = FUNCTION('MONTH', :date)")
    long countUserEnrollmentsInMonth(@Param("userUuid") String userUuid, @Param("date") LocalDate date);

    // Added methods for capacity checks
    long countByLessonLessonIdAndPayStatus(Long lessonId, String payStatus);

    long countByLessonLessonIdAndStatusAndPayStatus(Long lessonId, String status, String payStatus);

    // Methods for admin view
    Page<Enroll> findByPayStatus(String payStatus, Pageable pageable);
    Page<Enroll> findByStatus(String status, Pageable pageable);
    Page<Enroll> findByLesson(cms.swimming.domain.Lesson lesson, Pageable pageable);
} 