package cms.scheduler;

import cms.enroll.domain.Enroll;
import cms.enroll.repository.EnrollRepository;
import cms.locker.service.LockerService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;

@Service
public class ExpiredUnpaidEnrollmentCleanupJob {

    private static final Logger logger = LoggerFactory.getLogger(ExpiredUnpaidEnrollmentCleanupJob.class);

    private final EnrollRepository enrollRepository;
    private final LockerService lockerService;

    public ExpiredUnpaidEnrollmentCleanupJob(EnrollRepository enrollRepository, LockerService lockerService) {
        this.enrollRepository = enrollRepository;
        this.lockerService = lockerService;
    }

    /**
     * Periodically checks for UNPAID enrollments that have passed their expiration time.
     * Updates their status to EXPIRED and releases any allocated lockers.
     * Runs every 5 minutes, for example.
     */
    @Scheduled(cron = "0 */5 * * * ?") // Every 5 minutes
    @Transactional
    public void cleanupExpiredUnpaidEnrollments() {
        LocalDateTime now = LocalDateTime.now();
        logger.debug("Running ExpiredUnpaidEnrollmentCleanupJob at {}", now);

        // Find UNPAID enrollments that are APPLIED and whose expireDt has passed
        List<Enroll> expiredEnrollments = enrollRepository.findByPayStatusAndStatusAndExpireDtBefore("UNPAID", "APPLIED", now);

        if (expiredEnrollments.isEmpty()) {
            logger.debug("No expired UNPAID enrollments found to clean up.");
            return;
        }

        logger.info("Found {} expired UNPAID enrollments to process.", expiredEnrollments.size());

        for (Enroll enroll : expiredEnrollments) {
            logger.info("Processing expired UNPAID enrollment ID: {}, User: {}, Lesson: {}, Expires: {}", 
                        enroll.getEnrollId(), enroll.getUser().getUuid(), enroll.getLesson().getLessonId(), enroll.getExpireDt());

            enroll.setStatus("EXPIRED"); 
            // enroll.setPayStatus("EXPIRED"); // Consider if payStatus should also change, or remain UNPAID with status EXPIRED

            // FIXME: Temporarily commented out locker release logic until payment module is fully implemented and UNPAID expiry policy is active.
            /*
            if (enroll.isLockerAllocated()) {
                if (enroll.getUser() != null && enroll.getUser().getGender() != null && !enroll.getUser().getGender().trim().isEmpty()) {
                    try {
                        logger.info("Releasing locker for expired UNPAID enrollment ID: {}. Gender: {}", enroll.getEnrollId(), enroll.getUser().getGender());
                        lockerService.decrementUsedQuantity(enroll.getUser().getGender().toUpperCase());
                        enroll.setLockerAllocated(false);
                        logger.info("Locker released for enrollment ID: {}", enroll.getEnrollId());
                    } catch (Exception e) {
                        logger.error("Error decrementing locker quantity for expired UNPAID enrollment ID: {}. Error: {}", enroll.getEnrollId(), e.getMessage(), e);
                        // Decide if the job should fail or continue with other enrollments
                    }
                } else {
                    logger.warn("Expired UNPAID enrollment ID: {} had lockerAllocated=true but user or gender was null. Cannot release locker automatically.", enroll.getEnrollId());
                }
            }
            */
            enrollRepository.save(enroll);
        }
        logger.info("Finished ExpiredUnpaidEnrollmentCleanupJob for this run.");
    }
} 