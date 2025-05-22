package cms.locker.dto;

import lombok.Builder;
import lombok.Getter;

@Getter
@Builder
public class LockerAvailabilityDto {
    private String gender; // "MALE" 또는 "FEMALE"
    private int totalQuantity;
    private int usedQuantity;
    private int availableQuantity;

    // LockerInventory 엔티티에서 DTO로 변환하는 정적 팩토리 메소드 (선택 사항이지만 권장)
    public static LockerAvailabilityDto fromEntity(cms.locker.domain.LockerInventory entity) {
        return LockerAvailabilityDto.builder()
                .gender(entity.getGender())
                .totalQuantity(entity.getTotalQuantity())
                .usedQuantity(entity.getUsedQuantity())
                .availableQuantity(entity.getAvailableQuantity()) // 엔티티의 getAvailableQuantity() 사용
                .build();
    }
} 