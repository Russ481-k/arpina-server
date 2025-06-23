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
import java.time.YearMonth;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@Service
public class LockerUsageSyncJob {

    private static final Logger logger = LoggerFactory.getLogger(LockerUsageSyncJob.class);

    private final EnrollRepository enrollRepository;
    private final LockerService lockerService;

    public LockerUsageSyncJob(EnrollRepository enrollRepository, LockerService lockerService) {
        this.enrollRepository = enrollRepository;
        this.lockerService = lockerService;
    }

    /**
     * Hourly job to sync locker usage for the current month.
     * It counts all paid enrollments with an allocated locker for lessons active in
     * the current month
     * and updates the locker inventory usage stats.
     * Runs at the top of every hour.
     */
    @Scheduled(cron = "0 52 * * * ?") // Cron expression for every hour at minute 0
    @Transactional
    public void syncLockerUsage() {
        logger.info("========== Starting LockerUsageSyncJob ==========");

        YearMonth currentMonth = YearMonth.now();
        LocalDate startDate = currentMonth.atDay(1);
        LocalDate endDate = currentMonth.atEndOfMonth();
        logger.info("Target period: {} to {}", startDate, endDate);

        List<Enroll> activeEnrollments = enrollRepository.findActivePaidLockerUsersInDateRange(startDate, endDate);
        logger.info("[STEP 1] Found {} active enrollments from repository.", activeEnrollments.size());

        if (!activeEnrollments.isEmpty()) {
            activeEnrollments.stream().limit(5).forEach(e -> {
                User u = e.getUser();
                String gender = (u != null && u.getGender() != null) ? u.getGender() : "NULL";
                logger.info("  -> Enroll ID: {}, User UUID: {}, User Gender: '{}'", e.getEnrollId(),
                        u != null ? u.getUuid() : "NULL", gender);
            });
        }

        Map<String, Long> usageByGender = activeEnrollments.stream()
                .map(Enroll::getUser)
                .filter(user -> user != null && user.getGender() != null)
                .collect(Collectors.groupingBy(
                        user -> {
                            if ("1".equals(user.getGender())) {
                                return "MALE";
                            } else if ("0".equals(user.getGender())) {
                                return "FEMALE";
                            } else {
                                return "UNKNOWN"; // Should not happen
                            }
                        },
                        Collectors.counting()));

        // Remove UNKNOWN group if it exists
        usageByGender.remove("UNKNOWN");

        logger.info("[STEP 2] Calculated locker usage by gender: MALE={}, FEMALE={}",
                usageByGender.getOrDefault("MALE", 0L),
                usageByGender.getOrDefault("FEMALE", 0L));

        lockerService.syncUsedQuantity(usageByGender);

        logger.info("========== Finished LockerUsageSyncJob ==========");
    }
}