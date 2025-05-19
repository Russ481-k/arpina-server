package cms.swimming.service.impl;

import cms.swimming.domain.Locker;
import cms.swimming.dto.LockerDto;
import cms.swimming.repository.LockerRepository;
import cms.swimming.service.LockerService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import javax.persistence.EntityNotFoundException;
import java.util.List;
import java.util.stream.Collectors;

@Service("swimmingLockerServiceImpl")
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class LockerServiceImpl implements LockerService {

    private final LockerRepository lockerRepository;

    @Override
    public List<LockerDto> getAllLockers() {
        return lockerRepository.findAll().stream()
                .map(LockerDto::fromEntity)
                .collect(Collectors.toList());
    }

    @Override
    public List<LockerDto> getLockersByGenderAndActive(String gender, Boolean isActive) {
        Locker.LockerGender lockerGender = Locker.LockerGender.valueOf(gender);
        return lockerRepository.findByGenderAndIsActive(lockerGender, isActive).stream()
                .map(LockerDto::fromEntity)
                .collect(Collectors.toList());
    }

    @Override
    public List<LockerDto> getLockersByZone(String zone, Boolean isActive) {
        return lockerRepository.findByZoneAndIsActive(zone, isActive).stream()
                .map(LockerDto::fromEntity)
                .collect(Collectors.toList());
    }

    @Override
    public List<LockerDto> getAvailableLockers(String gender) {
        Locker.LockerGender lockerGender = Locker.LockerGender.valueOf(gender);
        return lockerRepository.findAvailableLockers(lockerGender).stream()
                .map(LockerDto::fromEntity)
                .collect(Collectors.toList());
    }

    @Override
    public LockerDto getLockerById(Long lockerId) {
        Locker locker = lockerRepository.findById(lockerId)
                .orElseThrow(() -> new EntityNotFoundException("사물함을 찾을 수 없습니다. ID: " + lockerId));
        return LockerDto.fromEntity(locker);
    }
} 