package cms.enroll.repository;

import cms.enroll.domain.Enroll;
import cms.user.domain.User;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.LocalDate;
import java.time.LocalDateTime;
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

    @Query("SELECT COUNT(e) FROM Enroll e " +
           "WHERE e.lesson.lessonId = :lessonId " +
           "AND e.user.gender = :gender " +
           "AND e.usesLocker = true " +
           "AND e.status = 'APPLIED'")
    long countUsedLockersByLessonAndUserGender(@Param("lessonId") Long lessonId, @Param("gender") String gender);

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

    long countByLessonLessonIdAndStatusAndPayStatusAndExpireDtAfter(Long lessonId, String status, String payStatus, LocalDateTime expireDt);

    // Method to count active enrollments for a lesson (PAID or (UNPAID and APPLIED and not expired))
    @Query("SELECT COUNT(e) FROM Enroll e WHERE e.lesson.lessonId = :lessonId AND (e.payStatus = 'PAID' OR (e.payStatus = 'UNPAID' AND e.status = 'APPLIED' AND e.expireDt > :now))")
    long countActiveEnrollmentsForLesson(@Param("lessonId") Long lessonId, @Param("now") LocalDateTime now);

    // Method to count UNPAID, APPLIED, active locker users for a lesson by gender
    @Query("SELECT COUNT(e) FROM Enroll e WHERE e.lesson.lessonId = :lessonId AND e.user.gender = :gender AND e.usesLocker = true AND e.payStatus IN :payStatuses AND e.status = 'APPLIED' AND e.expireDt > :now")
    long countByLessonLessonIdAndUserGenderAndUsesLockerTrueAndPayStatusInAndExpireDtAfter(@Param("lessonId") Long lessonId, @Param("gender") String gender, @Param("payStatuses") List<String> payStatuses, @Param("now") LocalDateTime now);

    // Method to count PAID locker users for a lesson by gender
    @Query("SELECT COUNT(e) FROM Enroll e WHERE e.lesson.lessonId = :lessonId AND e.user.gender = :gender AND e.usesLocker = true AND e.payStatus IN :payStatuses")
    long countByLessonLessonIdAndUserGenderAndUsesLockerTrueAndPayStatusIn(@Param("lessonId") Long lessonId, @Param("gender") String gender, @Param("payStatuses") List<String> payStatuses);

    // For monthly enrollment check (assuming this already exists and is correct)
    // If it doesn't exist or needs specific logic, it would be defined here.
    // Example: @Query("SELECT COUNT(e) FROM Enroll e WHERE e.user.uuid = :userUuid AND YEAR(e.lesson.startDate) = YEAR(:lessonStartDate) AND MONTH(e.lesson.startDate) = MONTH(:lessonStartDate) AND e.payStatus IN ('PAID', 'UNPAID')")
    // long countUserEnrollmentsInMonth(@Param("userUuid") String userUuid, @Param("lessonStartDate") LocalDate lessonStartDate);
} 