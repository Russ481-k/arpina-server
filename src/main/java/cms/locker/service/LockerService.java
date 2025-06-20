package cms.locker.service;

import cms.locker.dto.LockerAvailabilityDto;

public interface LockerService {
    LockerAvailabilityDto getLockerAvailabilityByGender(String gender);

    void incrementUsedQuantity(String gender);

    void decrementUsedQuantity(String gender);

    void syncUsedQuantity(java.util.Map<String, Long> usageByGender);
}