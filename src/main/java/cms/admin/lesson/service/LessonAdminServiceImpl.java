package cms.admin.lesson.service;

import cms.swimming.dto.LessonDto;
import cms.swimming.domain.Lesson;
import cms.swimming.repository.LessonRepository;
import cms.swimming.repository.specification.LessonSpecification;
// import cms.swimming.service.LessonService; // Not directly used for now
import cms.admin.lesson.dto.CloneLessonRequestDto;
import cms.common.exception.ResourceNotFoundException;
import cms.common.exception.ErrorCode;
import cms.common.exception.BusinessRuleException; // For deleteLesson check
import cms.enroll.repository.EnrollRepository; // For deleteLesson check
import lombok.RequiredArgsConstructor;
// import org.springframework.beans.BeanUtils; // Using builder or specific methods instead
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.time.temporal.ChronoUnit;

@Service
@RequiredArgsConstructor
@Transactional
public class LessonAdminServiceImpl implements LessonAdminService {

    private final LessonRepository lessonRepository;
    private final EnrollRepository enrollRepository; // Injected for deleteLesson

    private LessonDto convertToDto(Lesson lesson) {
        if (lesson == null) return null;
        // Assuming LessonDto has a constructor or builder, or setters for all these fields
        LessonDto dto = new LessonDto();
        dto.setLessonId(lesson.getLessonId());
        dto.setTitle(lesson.getTitle());
        dto.setStartDate(lesson.getStartDate());
        dto.setEndDate(lesson.getEndDate());
        dto.setRegistrationEndDate(lesson.getRegistrationEndDate()); // Added based on Lesson entity
        dto.setCapacity(lesson.getCapacity());
        dto.setPrice(lesson.getPrice());
        dto.setStatus(lesson.getStatus() != null ? lesson.getStatus().name() : null);
        dto.setInstructorName(lesson.getInstructorName()); 
        dto.setLessonTime(lesson.getLessonTime()); 
        return dto;
    }

    // Not directly converting DTO to new Entity for creation in this revised version,
    // will use builder in createLesson for clarity with Lesson entity structure.

    @Override
    @Transactional(readOnly = true)
    public Page<LessonDto> getAllLessons(String status, Integer year, Integer month, Pageable pageable) {
        Specification<Lesson> spec = LessonSpecification.filterBy(status, year, month);
        return lessonRepository.findAll(spec, pageable).map(this::convertToDto);
    }

    @Override
    @Transactional(readOnly = true)
    public LessonDto getLessonById(Long lessonId) {
        Lesson lesson = lessonRepository.findById(lessonId)
                .orElseThrow(() -> new ResourceNotFoundException("Lesson not found with id: " + lessonId, ErrorCode.LESSON_NOT_FOUND));
        return convertToDto(lesson);
    }

    @Override
    public LessonDto createLesson(LessonDto lessonDto) {
        Lesson.LessonStatus statusEnum;
        try {
            statusEnum = Lesson.LessonStatus.valueOf(lessonDto.getStatus().toUpperCase());
        } catch (IllegalArgumentException | NullPointerException e) {
            // Default to OPEN if status is invalid or not provided for new lesson
            statusEnum = Lesson.LessonStatus.OPEN;
            // Or throw: throw new IllegalArgumentException("Invalid or missing lesson status: " + lessonDto.getStatus());
        }

        // TODO: Add instructorName and lessonTime to builder if they are added to LessonDto and Lesson entity
        Lesson lesson = Lesson.builder()
                .title(lessonDto.getTitle())
                .startDate(lessonDto.getStartDate())
                .endDate(lessonDto.getEndDate())
                .registrationEndDate(lessonDto.getRegistrationEndDate()) 
                .capacity(lessonDto.getCapacity())
                .price(lessonDto.getPrice())
                .status(statusEnum)
                .instructorName(lessonDto.getInstructorName()) 
                .lessonTime(lessonDto.getLessonTime()) 
                .build();
        
        Lesson savedLesson = lessonRepository.save(lesson);
        return convertToDto(savedLesson);
    }

    @Override
    public LessonDto updateLesson(Long lessonId, LessonDto lessonDto) {
        Lesson existingLesson = lessonRepository.findById(lessonId)
                .orElseThrow(() -> new ResourceNotFoundException("Lesson not found with id: " + lessonId, ErrorCode.LESSON_NOT_FOUND));
        
        Lesson.LessonStatus newStatusEnum = existingLesson.getStatus(); // Default to existing status
        if (lessonDto.getStatus() != null && !lessonDto.getStatus().trim().isEmpty()) {
            try {
                newStatusEnum = Lesson.LessonStatus.valueOf(lessonDto.getStatus().toUpperCase());
            } catch (IllegalArgumentException e) {
                throw new IllegalArgumentException("Invalid lesson status: " + lessonDto.getStatus());
            }
        }

        // Use the entity's updateDetails method
        existingLesson.updateDetails(
            lessonDto.getTitle() != null ? lessonDto.getTitle() : existingLesson.getTitle(),
            lessonDto.getStartDate() != null ? lessonDto.getStartDate() : existingLesson.getStartDate(),
            lessonDto.getEndDate() != null ? lessonDto.getEndDate() : existingLesson.getEndDate(),
            lessonDto.getCapacity() != null ? lessonDto.getCapacity() : existingLesson.getCapacity(),
            lessonDto.getPrice() != null ? lessonDto.getPrice() : existingLesson.getPrice(),
            newStatusEnum,
            lessonDto.getRegistrationEndDate() != null ? lessonDto.getRegistrationEndDate() : existingLesson.getRegistrationEndDate(),
            lessonDto.getInstructorName() != null ? lessonDto.getInstructorName() : existingLesson.getInstructorName(),
            lessonDto.getLessonTime() != null ? lessonDto.getLessonTime() : existingLesson.getLessonTime()
        );
        // TODO: Add instructorName and lessonTime update logic if they are added to Lesson entity
        // e.g., if (lessonDto.getInstructorName() != null) existingLesson.setInstructorName(lessonDto.getInstructorName());

        Lesson updatedLesson = lessonRepository.save(existingLesson);
        return convertToDto(updatedLesson);
    }

    @Override
    public void deleteLesson(Long lessonId) {
        // TODO: Add business logic, e.g., cannot delete if there are active enrollments.
        if (!lessonRepository.existsById(lessonId)) {
            throw new ResourceNotFoundException("Lesson not found with id: " + lessonId, ErrorCode.LESSON_NOT_FOUND);
        }
        // Check if enrollments exist for this lesson that are not CANCELLED or fully REFUNDED
        long activeEnrollmentCount = enrollRepository.countActiveEnrollmentsForLessonDeletion(lessonId);
        if (activeEnrollmentCount > 0) {
            throw new BusinessRuleException(ErrorCode.LESSON_CANNOT_BE_DELETED, 
                "해당 강습에 활성 신청 내역(" + activeEnrollmentCount + "건)이 존재하여 삭제할 수 없습니다.");
        }
        lessonRepository.deleteById(lessonId);
    }

    @Override
    public LessonDto cloneLesson(Long lessonId, CloneLessonRequestDto cloneRequestDto) {
        Lesson originalLesson = lessonRepository.findById(lessonId)
                .orElseThrow(() -> new ResourceNotFoundException("Original lesson not found with id: " + lessonId, ErrorCode.LESSON_NOT_FOUND));

        LocalDate newStartDate = LocalDate.parse(cloneRequestDto.getNewStartDate(), DateTimeFormatter.ISO_LOCAL_DATE);
        long duration = ChronoUnit.DAYS.between(originalLesson.getStartDate(), originalLesson.getEndDate());
        LocalDate newEndDate = newStartDate.plusDays(duration);
        LocalDate newRegistrationEndDate = newStartDate.minusDays(1); 

        // TODO: Add instructorName and lessonTime to builder if they are added to Lesson entity and should be cloned
        Lesson clonedLesson = Lesson.builder()
                                .title(originalLesson.getTitle() + " (복제)") 
                                .startDate(newStartDate)
                                .endDate(newEndDate)
                                .registrationEndDate(newRegistrationEndDate) 
                                .capacity(originalLesson.getCapacity())
                                .price(originalLesson.getPrice())
                                .status(Lesson.LessonStatus.OPEN) 
                                .instructorName(originalLesson.getInstructorName()) 
                                .lessonTime(originalLesson.getLessonTime()) 
                                .build();
        
        Lesson savedClonedLesson = lessonRepository.save(clonedLesson);
        return convertToDto(savedClonedLesson);
    }
} 