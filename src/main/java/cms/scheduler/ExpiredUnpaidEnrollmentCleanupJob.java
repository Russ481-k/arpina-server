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
     * Periodically checks for UNPAID enrollments that have passed their expiration
     * time.
     * Updates their status to EXPIRED and releases any allocated lockers.
     * Runs every 5 minutes, for example.
     */
    @Scheduled(cron = "0 */5 * * * ?") // Every 5 minutes
    @Transactional
    public void cleanupExpiredUnpaidEnrollments() {
        LocalDateTime now = LocalDateTime.now();
        logger.debug("Running ExpiredUnpaidEnrollmentCleanupJob at {}", now);

        // Find UNPAID enrollments that are APPLIED and whose expireDt has passed
        List<Enroll> expiredEnrollments = enrollRepository.findByPayStatusAndStatusAndExpireDtBefore("UNPAID",
                "APPLIED", now);

        if (expiredEnrollments.isEmpty()) {
            logger.debug("No expired UNPAID enrollments found to clean up.");
            return;
        }

        logger.info("Found {} expired UNPAID enrollments to process.", expiredEnrollments.size());

        int processedCount = 0;

        for (Enroll enroll : expiredEnrollments) {
            logger.info("Processing expired UNPAID enrollment ID: {}, User: {}, Lesson: {}, Expires: {}",
                    enroll.getEnrollId(), enroll.getUser().getUuid(), enroll.getLesson().getLessonId(),
                    enroll.getExpireDt());

            enroll.setStatus("EXPIRED");
            // enroll.setPayStatus("EXPIRED"); // Consider if payStatus should also change,
            // or remain UNPAID with status EXPIRED

            // ❌ 만료 처리는 환불이 아니므로 사물함 재고에 영향을 주지 않음
            // 만료된 미결제 건도 사물함 재고는 변경하지 않음 (향후 실제 환불 시에만 처리)
            logger.info("만료된 UNPAID enrollment ID: {} - 사물함 재고는 변경하지 않음 (환불이 아님)", enroll.getEnrollId());

            enrollRepository.save(enroll);
            processedCount++;
        }

        logger.info("ExpiredUnpaidEnrollmentCleanupJob 완료. 처리된 enrollment: {}", processedCount);
    }
}