package cms.enroll.repository;

import cms.enroll.domain.Enroll;
import cms.user.domain.User;
import cms.swimming.domain.Lesson;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

@Repository
public interface EnrollRepository extends JpaRepository<Enroll, Long>, JpaSpecificationExecutor<Enroll> {
       List<Enroll> findByUserOrderByCreatedAtDesc(User user);

       List<Enroll> findByUserAndStatusOrderByCreatedAtDesc(User user, String status);

       List<Enroll> findByUserUuid(String userUuid);

       List<Enroll> findByUserUuidAndPayStatusInOrderByLesson_StartDateDesc(String userUuid, List<String> payStatuses);

       List<Enroll> findByUserUuidAndLessonLessonId(String userUuid, Long lessonId);

       Page<Enroll> findByUserUuidAndStatus(String userUuid, String status, Pageable pageable);

       Page<Enroll> findByLessonLessonId(Long lessonId, Pageable pageable);

       Optional<Enroll> findByUserUuidAndLessonLessonIdAndStatus(String userUuid, Long lessonId, String status);

       Optional<Enroll> findByUserUuidAndLessonLessonIdAndPayStatus(String userUuid, Long lessonId, String payStatus);

       @Query("SELECT COUNT(e) FROM Enroll e " +
                     "WHERE e.lesson.lessonId = :lessonId " +
                     "AND e.user.gender = :gender " +
                     "AND e.usesLocker = true " +
                     "AND e.status = 'APPLIED'")
       long countUsedLockersByLessonAndUserGender(@Param("lessonId") Long lessonId, @Param("gender") String gender);

       // Added methods for capacity checks
       long countByLessonLessonIdAndPayStatus(Long lessonId, String payStatus);

       long countByLessonLessonIdAndStatusAndPayStatus(Long lessonId, String status, String payStatus);

       // Methods for admin view
       Page<Enroll> findByPayStatus(String payStatus, Pageable pageable);

       Page<Enroll> findByStatus(String status, Pageable pageable);

       Page<Enroll> findByLesson(Lesson lesson, Pageable pageable);

       long countByLessonAndPayStatusAndExpireDtAfter(Lesson lesson, String payStatus, LocalDateTime expireDt);

       // Method to count active enrollments for a lesson (PAID or (UNPAID and APPLIED
       // and not expired))
       @Query("SELECT COUNT(e) FROM Enroll e WHERE e.lesson.lessonId = :lessonId AND (e.payStatus = 'PAID' OR (e.payStatus = 'UNPAID' AND e.status = 'APPLIED' AND e.expireDt > :now))")
       long countActiveEnrollmentsForLesson(@Param("lessonId") Long lessonId, @Param("now") LocalDateTime now);

       // Method to count UNPAID, APPLIED, active locker users for a lesson by gender
       @Query("SELECT COUNT(e) FROM Enroll e WHERE e.lesson.lessonId = :lessonId AND e.user.gender = :gender AND e.usesLocker = true AND e.payStatus IN :payStatuses AND e.status = 'APPLIED' AND e.expireDt > :now")
       long countByLessonLessonIdAndUserGenderAndUsesLockerTrueAndPayStatusInAndExpireDtAfter(
                     @Param("lessonId") Long lessonId, @Param("gender") String gender,
                     @Param("payStatuses") List<String> payStatuses, @Param("now") LocalDateTime now);

       // Method to count PAID locker users for a lesson by gender
       @Query("SELECT COUNT(e) FROM Enroll e WHERE e.lesson.lessonId = :lessonId AND e.user.gender = :gender AND e.usesLocker = true AND e.payStatus IN :payStatuses")
       long countByLessonLessonIdAndUserGenderAndUsesLockerTrueAndPayStatusIn(@Param("lessonId") Long lessonId,
                     @Param("gender") String gender, @Param("payStatuses") List<String> payStatuses);

       // For monthly enrollment check (assuming this already exists and is correct)
       // If it doesn't exist or needs specific logic, it would be defined here.
       // Example: @Query("SELECT COUNT(e) FROM Enroll e WHERE e.user.uuid = :userUuid
       // AND YEAR(e.lesson.startDate) = YEAR(:lessonStartDate) AND
       // MONTH(e.lesson.startDate) = MONTH(:lessonStartDate) AND e.payStatus IN
       // ('PAID', 'UNPAID')")
       // long countUserEnrollmentsInMonth(@Param("userUuid") String userUuid,
       // @Param("lessonStartDate") LocalDate lessonStartDate);

       // For renewal locker transfer: find the latest paid enrollment with a locker
       // for the user from the previous month
       @Query("SELECT e FROM Enroll e " +
                     "WHERE e.user.uuid = :userUuid " +
                     "AND e.payStatus = 'PAID' " +
                     "AND e.lockerAllocated = true " +
                     "AND e.lesson.endDate < :currentLessonStartDate " +
                     "AND FUNCTION('YEAR', e.lesson.endDate) = FUNCTION('YEAR', :previousMonthDate) " +
                     "AND FUNCTION('MONTH', e.lesson.endDate) = FUNCTION('MONTH', :previousMonthDate) " +
                     "ORDER BY e.lesson.endDate DESC, e.createdAt DESC")
       List<Enroll> findPreviousPaidLockerEnrollmentsForUser(
                     @Param("userUuid") String userUuid,
                     @Param("currentLessonStartDate") LocalDate currentLessonStartDate,
                     @Param("previousMonthDate") LocalDate previousMonthDate);

       // For LessonCompletionLockerReleaseSweepJob: find enrollments for lessons ended
       // before a certain date with lockers allocated.
       @Query("SELECT e FROM Enroll e " +
                     "WHERE e.lesson.endDate < :date " +
                     "AND e.lockerAllocated = true")
       List<Enroll> findByLesson_EndDateBeforeAndLockerAllocatedIsTrue(@Param("date") LocalDate date);

       // For checking if a lesson can be deleted
       @Query("SELECT COUNT(e) FROM Enroll e WHERE e.lesson.lessonId = :lessonId AND e.status <> 'CANCELED' AND e.payStatus NOT IN ('REFUNDED', 'PARTIAL_REFUNDED', 'CANCELED_UNPAID')")
       long countActiveEnrollmentsForLessonDeletion(@Param("lessonId") Long lessonId);

       Optional<Enroll> findByUserAndLessonAndPayStatusNotIn(User user, Lesson lesson,
                     List<String> excludedPayStatuses);

       // Method that was missing or had an incorrect signature
       long countByLessonLessonIdAndStatusAndPayStatusAndExpireDtAfter(Long lessonId, String status, String payStatus,
                     LocalDateTime expireDt);

       // For checking existing UNPAID enrollment before payment for a specific lesson
       // by a user
       Optional<Enroll> findByUserUuidAndLessonLessonIdAndPayStatusAndExpireDtAfter(
                     String userUuid, Long lessonId, String payStatus, LocalDateTime expireDt);

       // Check if a user has any enrollment (regardless of status) for a specific
       // lesson that was admin-cancelled
       boolean existsByUserUuidAndLessonLessonIdAndCancelStatusAndPayStatusIn(
                     String userUuid,
                     Long lessonId,
                     Enroll.CancelStatusType cancelStatus,
                     List<String> payStatuses);

       // For the temp-enrollment-bypass branch: check for PAID status directly
       // Removed duplicated findByUserUuidAndLessonLessonIdAndPayStatus from here

       // For monthly enrollment limit check (counts any active/paid status for a given
       // month)
       @Query("SELECT count(e) FROM Enroll e WHERE e.user.uuid = :userUuid " +
                     "AND e.lesson.startDate >= :monthStart AND e.lesson.startDate <= :monthEnd " +
                     "AND e.payStatus IN ('PAID', 'UNPAID') AND e.status NOT IN ('CANCELED', 'EXPIRED')") // Consider
                                                                                                          // only active
                                                                                                          // states
       long countUserEnrollmentsInMonthForDateRange(@Param("userUuid") String userUuid,
                     @Param("monthStart") LocalDate monthStart,
                     @Param("monthEnd") LocalDate monthEnd);

       // Simpler version if lesson.getStartDate() is sufficient for month grouping
       // (assumes lessons are monthly)
       @Query("SELECT count(e) FROM Enroll e WHERE e.user.uuid = :userUuid " +
                     "AND FUNCTION('YEAR', e.lesson.startDate) = FUNCTION('YEAR', :lessonMonthDate) " +
                     "AND FUNCTION('MONTH', e.lesson.startDate) = FUNCTION('MONTH', :lessonMonthDate) " +
                     "AND e.payStatus IN ('PAID', 'UNPAID') AND e.status NOT IN ('CANCELED', 'EXPIRED', 'CANCELED_UNPAID')")
       long countUserEnrollmentsInMonth(@Param("userUuid") String userUuid,
                     @Param("lessonMonthDate") LocalDate lessonMonthDate);

       @Query("SELECT count(e) > 0 FROM Enroll e WHERE e.user.uuid = :userUuid " +
                     "AND FUNCTION('YEAR', e.lesson.startDate) = FUNCTION('YEAR', :lessonMonthDate) " +
                     "AND FUNCTION('MONTH', e.lesson.startDate) = FUNCTION('MONTH', :lessonMonthDate) " +
                     "AND e.payStatus = 'PAID'")
       boolean existsPaidEnrollmentInMonth(@Param("userUuid") String userUuid,
                     @Param("lessonMonthDate") LocalDate lessonMonthDate);

       @Query("SELECT COUNT(e) FROM Enroll e " +
                     "WHERE e.user.gender = :gender " +
                     "AND e.payStatus = :payStatus " +
                     "AND e.usesLocker = :usesLocker " +
                     "AND e.lockerAllocated = :lockerAllocated " +
                     "AND e.lesson.startDate <= :periodEnd " +
                     "AND e.lesson.endDate >= :periodStart")
       long countActiveLockerCommitmentsForPeriod(
                     @Param("gender") String gender,
                     @Param("payStatus") String payStatus,
                     @Param("usesLocker") boolean usesLocker,
                     @Param("lockerAllocated") boolean lockerAllocated,
                     @Param("periodStart") LocalDate periodStart,
                     @Param("periodEnd") LocalDate periodEnd);

       // For ExpiredUnpaidEnrollmentCleanupJob
       List<Enroll> findByPayStatusAndStatusAndExpireDtBefore(String payStatus, String status, LocalDateTime expireDt);

       Optional<Enroll> findFirstByUserAndLesson(User user, Lesson lesson);

       @Query("SELECT count(e) > 0 FROM Enroll e WHERE e.user.uuid = :userUuid AND e.lesson.lessonId = :lessonId AND e.payStatus NOT IN ('REFUNDED', 'PARTIAL_REFUNDED', 'CANCELED_UNPAID')")
       boolean existsActiveEnrollment(@Param("userUuid") String userUuid, @Param("lessonId") Long lessonId);

       long countByLesson(Lesson lesson);

       @Query("SELECT e FROM Enroll e JOIN FETCH e.user u JOIN e.lesson l WHERE e.payStatus = 'PAID' AND e.lockerAllocated = true AND l.startDate <= :endDate AND l.endDate >= :startDate")
       List<Enroll> findPaidAndLockerAllocatedInDateRange(@Param("startDate") LocalDate startDate,
                     @Param("endDate") LocalDate endDate);

       @Query("SELECT e FROM Enroll e WHERE e.payStatus = 'UNPAID' AND e.expireDt < :now")
       List<Enroll> findExpiredUnpaidEnrollments(@Param("now") LocalDateTime now);

       @Query("SELECT e FROM Enroll e " +
                     "JOIN FETCH e.user u " +
                     "JOIN e.lesson l " +
                     "WHERE e.payStatus = 'PAID' " +
                     "AND e.usesLocker = true " +
                     "AND (e.cancelStatus IS NULL OR e.cancelStatus = 'NONE') " +
                     "AND l.startDate <= :endDate AND l.endDate >= :startDate")
       List<Enroll> findActivePaidLockerUsersInDateRange(@Param("startDate") LocalDate startDate,
                     @Param("endDate") LocalDate endDate);

       boolean existsByUserUuidAndCancelStatusIn(String userUuid, List<Enroll.CancelStatusType> cancelStatuses);

       boolean existsByUserUuidAndCancelStatusAndPayStatusNotIn(String userUuid, Enroll.CancelStatusType cancelStatus,
                     List<String> payStatuses);

       @Query("SELECT CASE WHEN COUNT(e) > 0 THEN true ELSE false END FROM Enroll e " +
                     "WHERE e.user.uuid = :userUuid " +
                     "AND e.lesson.startDate <= :endDate AND e.lesson.endDate >= :startDate " +
                     "AND e.payStatus NOT IN ('REFUNDED', 'PARTIAL_REFUNDED', 'CANCELED_UNPAID')")
       boolean hasActivePaidEnrollmentInDateRange(@Param("userUuid") String userUuid,
                     @Param("startDate") LocalDate startDate,
                     @Param("endDate") LocalDate endDate);

       @Query("SELECT CASE WHEN COUNT(e) > 0 THEN true ELSE false END " +
                     "FROM Enroll e " +
                     "WHERE e.lesson.lessonId = :lessonId " +
                     "AND e.payStatus NOT IN ('REFUNDED', 'PARTIAL_REFUNDED', 'CANCELED_UNPAID')")
       boolean hasRefundableEnrollmentForLesson(@Param("lessonId") Long lessonId);

       @Query("SELECT e FROM Enroll e " +
                     "JOIN e.lesson l " +
                     "WHERE e.user.uuid = :userUuid AND e.payStatus = :payStatus " +
                     "AND l.title = :title " +
                     "AND l.lessonTime = :lessonTime " +
                     "AND l.startDate >= :startDate AND l.startDate <= :endDate")
       List<Enroll> findPaidByUserForPreviousMonthLesson(
                     @Param("userUuid") String userUuid,
                     @Param("payStatus") String payStatus,
                     @Param("title") String title,
                     @Param("lessonTime") String lessonTime,
                     @Param("startDate") LocalDate startDate,
                     @Param("endDate") LocalDate endDate);

       Integer findMaxWaitingNumberByLesson(Lesson lesson);

       boolean existsByUserAndLessonInAndPayStatus(User user, List<Lesson> lessons, String payStatus);

       @Modifying
       @Query("UPDATE Enroll e SET e.payStatus = :payStatus WHERE e.enrollId = :enrollId")
       void updatePayStatus(@Param("enrollId") Long enrollId, @Param("payStatus") String payStatus);
}