package cms.locker.service;

import cms.locker.domain.LockerInventory;
import cms.locker.repository.LockerInventoryRepository;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import javax.persistence.LockModeType; // For pessimistic lock
import org.springframework.data.jpa.repository.Lock; // For @Lock annotation

import java.util.NoSuchElementException;
import java.util.Optional;

@Service
@RequiredArgsConstructor
public class LockerServiceImpl implements LockerService {

    private static final Logger logger = LoggerFactory.getLogger(LockerServiceImpl.class);
    private final LockerInventoryRepository lockerInventoryRepository;

    @Override
    @Transactional(readOnly = true)
    public int getAvailableLockerCount(String gender) {
        if (gender == null || gender.trim().isEmpty()) {
            logger.warn("Attempted to get available locker count with null or empty gender.");
            return 0;
        }
        LockerInventory inventory = lockerInventoryRepository.findByGender(gender)
            .orElseGet(() -> {
                logger.warn("Locker inventory not found for gender: {}. Returning 0 available.", gender);
                // Optionally create a default one if business logic requires, but usually this means setup data is missing.
                LockerInventory newInventory = new LockerInventory();
                newInventory.setGender(gender);
                newInventory.setTotalQuantity(0); 
                newInventory.setUsedQuantity(0);
                // It might not be wise to save here as this is a read-only transaction part and indicates missing setup.
                return newInventory;
            });
        return inventory.getAvailableQuantity();
    }

    @Override
    @Transactional // Consider propagation and isolation levels for concurrency
    // @Lock(LockModeType.PESSIMISTIC_WRITE) // On repository method or use EntityManager for finer control
    public boolean assignLocker(String gender) {
        if (gender == null || gender.trim().isEmpty()) {
            logger.error("Cannot assign locker: gender is null or empty.");
            return false; 
        }
        // It's generally better to lock the specific row in the repository method.
        // For example, lockerInventoryRepository.findByGenderForUpdate(gender)
        LockerInventory inventory = lockerInventoryRepository.findByGender(gender)
            .orElseThrow(() -> {
                logger.error("Critical: Locker inventory setup missing for gender: {}", gender);
                return new NoSuchElementException("Locker inventory setup missing for gender: " + gender);
            });

        if (inventory.getAvailableQuantity() > 0) {
            inventory.setUsedQuantity(inventory.getUsedQuantity() + 1);
            lockerInventoryRepository.save(inventory);
            logger.info("Assigned locker for gender: {}. Used: {}, Total: {}", gender, inventory.getUsedQuantity(), inventory.getTotalQuantity());
            return true;
        }
        logger.warn("Failed to assign locker for gender: {}. No available lockers. Used: {}, Total: {}", gender, inventory.getUsedQuantity(), inventory.getTotalQuantity());
        return false; // 라커 부족
    }

    @Override
    @Transactional // Consider propagation and isolation levels for concurrency
    public void releaseLocker(String gender) {
        if (gender == null || gender.trim().isEmpty()) {
            logger.error("Cannot release locker: gender is null or empty.");
            return; 
        }
        // Similar to assignLocker, consider pessimistic lock if high concurrency on this operation
        Optional<LockerInventory> inventoryOpt = lockerInventoryRepository.findByGender(gender);
        
        if (!inventoryOpt.isPresent()) {
            logger.error("Critical: Locker inventory setup missing for gender: {} during release.", gender);
            // This case should ideally not happen if assignLocker requires inventory to exist.
            return;
        }

        LockerInventory inventory = inventoryOpt.get();
        if (inventory.getUsedQuantity() > 0) {
            inventory.setUsedQuantity(inventory.getUsedQuantity() - 1);
            lockerInventoryRepository.save(inventory);
            logger.info("Released locker for gender: {}. Used: {}, Total: {}", gender, inventory.getUsedQuantity(), inventory.getTotalQuantity());
        } else {
            logger.warn("Attempted to release locker for gender: {} but used quantity was already 0.", gender);
        }
    }
} 