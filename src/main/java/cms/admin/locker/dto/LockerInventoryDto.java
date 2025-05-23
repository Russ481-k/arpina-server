package cms.admin.locker.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class LockerInventoryDto {
    private String gender; // MALE, FEMALE
    private int totalQuantity;
    private int usedQuantity;
    private int availableQuantity;
} 