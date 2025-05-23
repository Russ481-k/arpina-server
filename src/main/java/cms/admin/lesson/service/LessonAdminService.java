package cms.admin.lesson.service;

import cms.swimming.dto.LessonDto;
import cms.admin.lesson.dto.CloneLessonRequestDto;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

public interface LessonAdminService {
    Page<LessonDto> getAllLessons(String status, Integer year, Integer month, Pageable pageable);
    LessonDto getLessonById(Long lessonId);
    LessonDto createLesson(LessonDto lessonDto);
    LessonDto updateLesson(Long lessonId, LessonDto lessonDto);
    void deleteLesson(Long lessonId);
    LessonDto cloneLesson(Long lessonId, CloneLessonRequestDto cloneLessonRequestDto);
} 