package cms.admin.locker.dto;

import lombok.Data;
import javax.validation.constraints.Min;
import javax.validation.constraints.NotNull;

@Data
public class LockerInventoryUpdateRequestDto {
    @NotNull(message = "총 라커 수량은 필수입니다.")
    @Min(value = 0, message = "총 라커 수량은 0 이상이어야 합니다.")
    private Integer totalQuantity;
} 