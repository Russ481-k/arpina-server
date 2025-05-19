package cms.swimming.service;

import cms.swimming.dto.LessonDto;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

import java.time.LocalDate;
import java.util.List;

public interface LessonService {
    
    // 모든 강습 조회 (페이징)
    Page<LessonDto> getAllLessons(Pageable pageable);
    
    // 상태별 강습 조회 (페이징)
    Page<LessonDto> getLessonsByStatus(String status, Pageable pageable);
    
    // 특정 기간 내 강습 조회
    List<LessonDto> getLessonsByDateRange(LocalDate startDate, LocalDate endDate, String status);
    
    // 특정 강습 상세 조회
    LessonDto getLessonById(Long lessonId);
    
    // 특정 강습의 현재 신청 인원 조회
    long countCurrentEnrollments(Long lessonId);
    
    // 특정 강습의 현재 사물함 사용 현황 조회
    long countLockersByGender(Long lessonId, String gender);
} 