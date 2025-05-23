package cms.admin.locker.service;

import cms.admin.locker.dto.LockerInventoryDto;
import cms.admin.locker.dto.LockerInventoryUpdateRequestDto;
import cms.locker.domain.LockerInventory;
import cms.locker.repository.LockerInventoryRepository;
import cms.common.exception.ResourceNotFoundException;
import cms.common.exception.ErrorCode;
import cms.common.exception.BusinessRuleException;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Arrays;
import java.util.List;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
@Transactional
public class LockerAdminServiceImpl implements LockerAdminService {

    private final LockerInventoryRepository lockerInventoryRepository;
    private final List<String> VALID_GENDERS = Arrays.asList("MALE", "FEMALE");

    private LockerInventoryDto convertToDto(LockerInventory entity) {
        if (entity == null) return null;
        return LockerInventoryDto.builder()
                .gender(entity.getGender())
                .totalQuantity(entity.getTotalQuantity())
                .usedQuantity(entity.getUsedQuantity())
                .availableQuantity(entity.getTotalQuantity() - entity.getUsedQuantity())
                .build();
    }

    private String normalizeGender(String gender) {
        if (gender == null || gender.trim().isEmpty()) {
            throw new BusinessRuleException(ErrorCode.INVALID_INPUT_VALUE, "성별을 입력해주세요.");
        }
        String upperGender = gender.trim().toUpperCase();
        if (!VALID_GENDERS.contains(upperGender)) {
            throw new BusinessRuleException(ErrorCode.INVALID_USER_GENDER, "유효하지 않은 성별 값입니다: " + gender);
        }
        return upperGender;
    }

    @Override
    @Transactional(readOnly = true)
    public List<LockerInventoryDto> getAllLockerInventories() {
        return lockerInventoryRepository.findAll().stream()
                .map(this::convertToDto)
                .collect(Collectors.toList());
    }

    @Override
    @Transactional(readOnly = true)
    public LockerInventoryDto getLockerInventoryByGender(String gender) {
        String normalizedGender = normalizeGender(gender);
        LockerInventory inventory = lockerInventoryRepository.findByGender(normalizedGender)
                .orElseThrow(() -> new ResourceNotFoundException(
                        "해당 성별의 사물함 재고 정보를 찾을 수 없습니다: " + normalizedGender,
                        ErrorCode.LOCKER_INVENTORY_NOT_FOUND));
        return convertToDto(inventory);
    }

    @Override
    public LockerInventoryDto updateTotalLockerQuantity(String gender, LockerInventoryUpdateRequestDto updateRequestDto) {
        String normalizedGender = normalizeGender(gender);
        LockerInventory inventory = lockerInventoryRepository.findByGender(normalizedGender)
                .orElseGet(() -> {
                    // If not found, create a new one. This might be desired or not depending on policy.
                    // For now, let's assume we only update existing ones, or it has to be pre-populated.
                    // Alternative: Create if not exists
                    // LockerInventory newInventory = new LockerInventory();
                    // newInventory.setGender(normalizedGender);
                    // newInventory.setUsedQuantity(0); // Default used to 0
                    // return newInventory;
                     throw new ResourceNotFoundException(
                        "해당 성별의 사물함 재고 정보를 찾을 수 없습니다: " + normalizedGender + ". 먼저 재고 정보를 생성해주세요.",
                        ErrorCode.LOCKER_INVENTORY_NOT_FOUND);
                });

        if (updateRequestDto.getTotalQuantity() < inventory.getUsedQuantity()) {
            throw new BusinessRuleException(ErrorCode.INVALID_INPUT_VALUE,
                    "총 라커 수량은 현재 사용 중인 수량(" + inventory.getUsedQuantity() + "개)보다 적을 수 없습니다.");
        }
        inventory.setTotalQuantity(updateRequestDto.getTotalQuantity());
        LockerInventory updatedInventory = lockerInventoryRepository.save(inventory);
        return convertToDto(updatedInventory);
    }
} 