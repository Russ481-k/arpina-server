package cms.admin.locker.service;

import cms.admin.locker.dto.LockerInventoryDto;
import cms.admin.locker.dto.LockerInventoryUpdateRequestDto;
import java.util.List;

public interface LockerAdminService {
    List<LockerInventoryDto> getAllLockerInventories();
    LockerInventoryDto getLockerInventoryByGender(String gender);
    LockerInventoryDto updateTotalLockerQuantity(String gender, LockerInventoryUpdateRequestDto updateRequestDto);
} 