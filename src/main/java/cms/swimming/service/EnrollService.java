package cms.swimming.service;

import cms.swimming.dto.CancelRequestDto;
import cms.swimming.dto.EnrollRequestDto;
import cms.swimming.dto.EnrollResponseDto;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

import java.util.List;

public interface EnrollService {
    
    // 사용자 강습 신청
    EnrollResponseDto createEnroll(Long userId, String userName, EnrollRequestDto enrollRequest, String ip);
    
    // 사용자 신청 취소
    EnrollResponseDto cancelEnroll(Long userId, Long enrollId, CancelRequestDto cancelRequest, String ip);
    
    // 사용자 신청 내역 조회
    List<EnrollResponseDto> getUserEnrolls(Long userId);
    
    // 사용자 신청 내역 상태별 조회 (페이징)
    Page<EnrollResponseDto> getUserEnrollsByStatus(Long userId, String status, Pageable pageable);
    
    // 특정 신청 상세 조회
    EnrollResponseDto getEnrollById(Long enrollId);
    
    // 특정 강습의 신청 내역 조회 (페이징)
    Page<EnrollResponseDto> getEnrollsByLessonId(Long lessonId, Pageable pageable);
} 