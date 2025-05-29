package cms.scheduler;

import cms.enroll.repository.EnrollRepository;
import cms.locker.domain.LockerInventory;
import cms.locker.repository.LockerInventoryRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.YearMonth;
import java.util.Arrays;
import java.util.List;

@Service
public class MonthlyLockerUsageSyncJob {

    private static final Logger logger = LoggerFactory.getLogger(MonthlyLockerUsageSyncJob.class);

    private final EnrollRepository enrollRepository;
    private final LockerInventoryRepository lockerInventoryRepository;

    public MonthlyLockerUsageSyncJob(EnrollRepository enrollRepository, LockerInventoryRepository lockerInventoryRepository) {
        this.enrollRepository = enrollRepository;
        this.lockerInventoryRepository = lockerInventoryRepository;
    }

    /**
     * Periodically RESETS the used_quantity in locker_inventory to 0.
     * This job is scheduled to run at 00:00:00 on the 20th day of each month.
     * Ensures server timezone is KST or adjust cron expression if UTC is used by scheduler.
     */
    // Cron: At 00:00:00 AM on day-of-month 20.
    @Scheduled(cron = "0 0 0 20 * ?") 
    @Transactional
    public void syncLockerUsageToInventory() {
        logger.info("Starting MonthlyLockerUsageSyncJob to RESET used_quantity to 0 for all genders.");

        List<LockerInventory> allInventories = lockerInventoryRepository.findAll();

        if (allInventories.isEmpty()) {
            logger.warn("No locker inventory records found to reset. This might be an issue if lockers are expected.");
            // Optionally, create default MALE/FEMALE entries if they are guaranteed to exist
            // For now, just logging.
        }

        for (LockerInventory inventory : allInventories) {
            logger.info("Resetting used_quantity for gender {}. Old value: {}. New value: 0.", 
                        inventory.getGender(), inventory.getUsedQuantity());
            inventory.setUsedQuantity(0);
            lockerInventoryRepository.save(inventory);
        }
        logger.info("Finished MonthlyLockerUsageSyncJob. All locker inventory used_quantities reset to 0.");
    }
} 