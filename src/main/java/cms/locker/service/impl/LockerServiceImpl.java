package cms.locker.service.impl;

import cms.locker.domain.LockerInventory;
import cms.locker.dto.LockerAvailabilityDto;
import cms.locker.repository.LockerInventoryRepository;
import cms.locker.service.LockerService;
import cms.common.exception.ResourceNotFoundException; // 일반적인 예외 클래스 사용 가정
import cms.common.exception.ErrorCode; // 일반적인 예외 코드 사용 가정
import cms.common.exception.BusinessRuleException; // 추가
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class LockerServiceImpl implements LockerService {

    private final LockerInventoryRepository lockerInventoryRepository;

    @Override
    @Transactional(readOnly = true)
    public LockerAvailabilityDto getLockerAvailabilityByGender(String gender) {
        LockerInventory inventory = lockerInventoryRepository.findByGender(gender.toUpperCase())
                .orElseThrow(() -> new ResourceNotFoundException("해당 성별의 사물함 재고 정보를 찾을 수 없습니다: " + gender, ErrorCode.LOCKER_INVENTORY_NOT_FOUND));
        return LockerAvailabilityDto.fromEntity(inventory);
    }

    @Override
    @Transactional // 쓰기 트랜잭션
    public void incrementUsedQuantity(String gender) {
        LockerInventory inventory = lockerInventoryRepository.findByGender(gender.toUpperCase())
                .orElseThrow(() -> new ResourceNotFoundException("해당 성별의 사물함 재고 정보를 찾을 수 없습니다: " + gender, ErrorCode.LOCKER_INVENTORY_NOT_FOUND));
        
        if (inventory.getUsedQuantity() >= inventory.getTotalQuantity()) {
            throw new BusinessRuleException(ErrorCode.LOCKER_NOT_AVAILABLE, "해당 성별의 사용 가능한 사물함이 없습니다.");
        }
        inventory.setUsedQuantity(inventory.getUsedQuantity() + 1);
        lockerInventoryRepository.save(inventory);
    }

    @Override
    @Transactional // 쓰기 트랜잭션
    public void decrementUsedQuantity(String gender) {
        LockerInventory inventory = lockerInventoryRepository.findByGender(gender.toUpperCase())
                .orElseThrow(() -> new ResourceNotFoundException("해당 성별의 사물함 재고 정보를 찾을 수 없습니다: " + gender, ErrorCode.LOCKER_INVENTORY_NOT_FOUND));
        
        if (inventory.getUsedQuantity() > 0) {
            inventory.setUsedQuantity(inventory.getUsedQuantity() - 1);
            lockerInventoryRepository.save(inventory);
        }
        // usedQuantity가 0인데 호출될 경우 로깅 또는 별도 처리 가능
    }
} 