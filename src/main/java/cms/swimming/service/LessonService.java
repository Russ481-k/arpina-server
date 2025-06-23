package cms.swimming.service;

import cms.swimming.dto.LessonDto;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

import java.time.LocalDate;
import java.util.List;
// List import might be unused if getLessonsByDateRange is fully removed
// import java.util.List; 

public interface LessonService {

    // Old methods commented out as they are replaced by the consolidated
    // getLessons:
    // Page<LessonDto> getAllLessons(Pageable pageable);
    // Page<LessonDto> getLessonsByStatus(String status, Pageable pageable);
    // List<LessonDto> getLessonsByDateRange(LocalDate startDate, LocalDate endDate,
    // String status);

    // Consolidated method for fetching lessons with various filters
    Page<LessonDto> getLessons(List<Integer> months, LocalDate startDate, LocalDate endDate, Pageable pageable);

    // 특정 강습 상세 조회
    LessonDto getLessonById(Long lessonId);

    // 특정 강습의 현재 신청 인원 조회
    long countCurrentEnrollments(Long lessonId);

    // 특정 강습의 현재 사물함 사용 현황 조회
    long countLockersByGender(Long lessonId, String gender);
}