package cms.swimming.service;

import cms.swimming.dto.LockerDto;

import java.util.List;

public interface LockerService {
    
    // 모든 사물함 조회
    List<LockerDto> getAllLockers();
    
    // 성별과 활성화 상태로 사물함 조회
    List<LockerDto> getLockersByGenderAndActive(String gender, Boolean isActive);
    
    // 특정 구역의 사물함 조회
    List<LockerDto> getLockersByZone(String zone, Boolean isActive);
    
    // 사용 가능한 (할당되지 않은) 사물함 조회
//    List<LockerDto> getAvailableLockers(String gender);
    
    // 특정 사물함 상세 조회
    LockerDto getLockerById(Long lockerId);
} 