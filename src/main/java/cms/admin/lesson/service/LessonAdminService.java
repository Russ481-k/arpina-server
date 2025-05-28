package cms.admin.lesson.service;

// import cms.swimming.dto.LessonDto; // No longer using this for admin operations directly
import cms.admin.lesson.dto.AdminLessonCreateRequestDto;
import cms.admin.lesson.dto.AdminLessonResponseDto;
import cms.admin.lesson.dto.AdminLessonUpdateRequestDto;
import cms.admin.lesson.dto.CloneLessonRequestDto;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

public interface LessonAdminService {
    // Method for general user-facing lesson list if this service handles it, otherwise remove or keep as is if it serves a different purpose.
    // Page<LessonDto> getAllLessons(String status, Integer year, Integer month, Pageable pageable);
    
    // Admin specific methods using new DTOs
    Page<AdminLessonResponseDto> getAllLessonsAdmin(Pageable pageable, Integer year, Integer month); // status 파라미터 제거
    AdminLessonResponseDto getLessonByIdAdmin(Long lessonId);
    AdminLessonResponseDto createLessonAdmin(AdminLessonCreateRequestDto createRequestDto, String createdBy, String createdIp);
    AdminLessonResponseDto updateLessonAdmin(Long lessonId, AdminLessonUpdateRequestDto updateRequestDto, String updatedBy, String updatedIp);
    void deleteLessonAdmin(Long lessonId);
    AdminLessonResponseDto cloneLessonAdmin(Long lessonId, CloneLessonRequestDto cloneLessonRequestDto, String createdBy, String createdIp);

    // These seem redundant if getAllLessonsAdmin and getLessonByIdAdmin are comprehensive
    // Page<LessonDto> getLessonsByStatusAdmin(String status, Pageable pageable); 
    // LessonDto getLessonById(Long lessonId); // old one, superseded by getLessonByIdAdmin
} 