package cms.scheduler;

import cms.enroll.domain.Enroll;
import cms.enroll.repository.EnrollRepository;
import cms.locker.service.LockerService;
import cms.user.domain.User;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.util.List;

@Service
public class LessonCompletionLockerReleaseSweepJob {

    private static final Logger logger = LoggerFactory.getLogger(LessonCompletionLockerReleaseSweepJob.class);

    private final EnrollRepository enrollRepository;
    private final LockerService lockerService;

    public LessonCompletionLockerReleaseSweepJob(EnrollRepository enrollRepository, LockerService lockerService) {
        this.enrollRepository = enrollRepository;
        this.lockerService = lockerService;
    }

    /**
     * Daily job to find enrollments for past lessons with allocated lockers
     * and release them.
     * Runs at 3 AM server time every day.
     */
    @Scheduled(cron = "0 0 3 * * ?") // Cron expression for 3:00 AM daily
    @Transactional
    public void releaseLockersForCompletedLessons() {
        logger.info("Starting LessonCompletionLockerReleaseSweepJob - releaseLockersForCompletedLessons");
        LocalDate yesterday = LocalDate.now().minusDays(1); // Lessons completed up to yesterday

        // Find enrollments for lessons that ended before today AND have a locker allocated
        // Need a new method in EnrollRepository: findByLesson_EndDateBeforeAndLockerAllocatedTrue
        List<Enroll> enrollmentsToProcess = enrollRepository.findByLesson_EndDateBeforeAndLockerAllocatedIsTrue(yesterday);

        int releasedCount = 0;
        for (Enroll enroll : enrollmentsToProcess) {
            try {
                User user = enroll.getUser();
                if (user != null && user.getGender() != null && !user.getGender().trim().isEmpty()) {
                    logger.info("Processing enrollId: {}. LessonId: {}, Lesson EndDate: {}. Releasing locker for user: {}, gender: {}",
                            enroll.getEnrollId(), enroll.getLesson().getLessonId(), enroll.getLesson().getEndDate(), user.getUuid(), user.getGender());
                    
                    lockerService.decrementUsedQuantity(user.getGender().toUpperCase());
                    enroll.setLockerAllocated(false);
                    enroll.setLockerPgToken(null); // Clear token as well
                    enrollRepository.save(enroll);
                    releasedCount++;
                } else {
                    logger.warn("Skipping locker release for enrollId: {} due to missing user or gender information.", enroll.getEnrollId());
                }
            } catch (Exception e) {
                logger.error("Error releasing locker for enrollId: {}. User: {}. Error: {}", enroll.getEnrollId(), enroll.getUser() != null ? enroll.getUser().getUuid() : "N/A", e.getMessage(), e);
                // Continue to next enrollment
            }
        }
        logger.info("Finished LessonCompletionLockerReleaseSweepJob. Lockers released: {}", releasedCount);
    }
} 