package cms.locker.service.impl;

import cms.locker.domain.LockerInventory;
import cms.locker.dto.LockerAvailabilityDto;
import cms.locker.repository.LockerInventoryRepository;
import cms.locker.service.LockerService;
import cms.common.exception.BusinessRuleException;
import cms.common.exception.ErrorCode;
import cms.common.exception.ResourceNotFoundException;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Map;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class LockerServiceImpl implements LockerService {

    private static final Logger logger = LoggerFactory.getLogger(LockerServiceImpl.class);
    private final LockerInventoryRepository lockerInventoryRepository;

    @Override
    public LockerAvailabilityDto getLockerAvailabilityByGender(String gender) {
        LockerInventory inventory = lockerInventoryRepository.findByGender(gender.toUpperCase())
                .orElseThrow(() -> new ResourceNotFoundException("해당 성별의 사물함 재고 정보를 찾을 수 없습니다: " + gender,
                        ErrorCode.LOCKER_INVENTORY_NOT_FOUND));

        return LockerAvailabilityDto.builder()
                .gender(inventory.getGender())
                .totalQuantity(inventory.getTotalQuantity())
                .usedQuantity(inventory.getUsedQuantity())
                .availableQuantity(inventory.getAvailableQuantity())
                .build();
    }

    @Override
    @Transactional // 쓰기 트랜잭션
    public void incrementUsedQuantity(String gender) {
        LockerInventory inventory = lockerInventoryRepository.findByGender(gender.toUpperCase())
                .orElseThrow(() -> new ResourceNotFoundException("해당 성별의 사물함 재고 정보를 찾을 수 없습니다: " + gender,
                        ErrorCode.LOCKER_INVENTORY_NOT_FOUND));

        if (inventory.getUsedQuantity() >= inventory.getTotalQuantity()) {
            throw new BusinessRuleException(ErrorCode.LOCKER_NOT_AVAILABLE, "해당 성별의 사용 가능한 사물함이 없습니다.");
        }

        logger.info("Incrementing locker usage for gender: {}. Used: {}/{}",
                gender, inventory.getUsedQuantity() + 1, inventory.getTotalQuantity());

        inventory.setUsedQuantity(inventory.getUsedQuantity() + 1);
        lockerInventoryRepository.save(inventory);
    }

    @Override
    @Transactional // 쓰기 트랜잭션
    public void decrementUsedQuantity(String gender) {
        LockerInventory inventory = lockerInventoryRepository.findByGender(gender.toUpperCase())
                .orElseThrow(() -> new ResourceNotFoundException("해당 성별의 사물함 재고 정보를 찾을 수 없습니다: " + gender,
                        ErrorCode.LOCKER_INVENTORY_NOT_FOUND));

        if (inventory.getUsedQuantity() > 0) {
            logger.info("Decrementing locker usage for gender: {}. Used: {}/{}",
                    gender, inventory.getUsedQuantity() - 1, inventory.getTotalQuantity());

            inventory.setUsedQuantity(inventory.getUsedQuantity() - 1);
            lockerInventoryRepository.save(inventory);
        } else {
            logger.warn("Attempted to decrement locker usage for gender: {} but usedQuantity is already 0", gender);
        }
    }

    @Override
    @Transactional
    public void syncUsedQuantity(Map<String, Long> usageByGender) {
        List<LockerInventory> inventories = lockerInventoryRepository.findAll();
        logger.info("Starting locker usage synchronization. Found {} inventory types.", inventories.size());

        for (LockerInventory inventory : inventories) {
            String gender = inventory.getGender();
            int newUsage = usageByGender.getOrDefault(gender, 0L).intValue();

            if (inventory.getUsedQuantity() != newUsage) {
                logger.info("Syncing locker usage for gender: {}. Old: {}, New: {}. Total: {}",
                        gender, inventory.getUsedQuantity(), newUsage, inventory.getTotalQuantity());
                inventory.setUsedQuantity(newUsage);
                lockerInventoryRepository.save(inventory);
            } else {
                logger.info("Locker usage for gender: {} is already up-to-date. Used: {}, Total: {}",
                        gender, inventory.getUsedQuantity(), inventory.getTotalQuantity());
            }
        }
        logger.info("Finished locker usage synchronization.");
    }
}