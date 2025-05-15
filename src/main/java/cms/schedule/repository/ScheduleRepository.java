package cms.schedule.repository;

import cms.schedule.entity.Schedule;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.List;

@Repository
public interface ScheduleRepository extends JpaRepository<Schedule, Long> {
    
    List<Schedule> findByStartDateTimeBetween(LocalDateTime startDateTime, LocalDateTime endDateTime);

    boolean existsByTitleAndStartDateTime(String title, LocalDateTime startDateTime);

    boolean existsByTitleAndStartDateTimeAndScheduleIdNot(String title, LocalDateTime startDateTime, Long scheduleId);
    
    @Query("SELECT s FROM Schedule s WHERE " +
           "YEAR(s.startDateTime) = :year AND MONTH(s.startDateTime) = :month " +
           "ORDER BY s.startDateTime ASC")
    List<Schedule> findByYearAndMonth(@Param("year") int year, @Param("month") int month);

    @Query("SELECT s FROM Schedule s WHERE " +
           "s.startDateTime >= :dateFrom AND s.startDateTime <= :dateTo " +
           "ORDER BY s.startDateTime ASC")
    List<Schedule> findByDateRange(
        @Param("dateFrom") LocalDateTime dateFrom,
        @Param("dateTo") LocalDateTime dateTo
    );

    @Query("SELECT s FROM Schedule s WHERE " +
           "(:title IS NULL OR s.title LIKE %:title%) AND " +
           "(:displayYn IS NULL OR s.displayYn = :displayYn)")
    Page<Schedule> search(@Param("title") String title, @Param("displayYn") String displayYn, Pageable pageable);
} 