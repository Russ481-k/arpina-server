package cms.admin.lesson.service.impl;

import cms.swimming.domain.Lesson;
import cms.swimming.repository.LessonRepository;
import cms.swimming.repository.specification.LessonSpecification;
import cms.admin.lesson.dto.AdminLessonCreateRequestDto;
import cms.admin.lesson.dto.AdminLessonResponseDto;
import cms.admin.lesson.dto.AdminLessonUpdateRequestDto;
import cms.admin.lesson.dto.CloneLessonRequestDto;
import cms.admin.lesson.service.LessonAdminService;
import cms.common.exception.ResourceNotFoundException;
import cms.common.exception.ErrorCode;
import cms.common.exception.BusinessRuleException;
import cms.common.exception.InvalidInputException;
import cms.enroll.repository.EnrollRepository;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.YearMonth;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
@Transactional
public class LessonAdminServiceImpl implements LessonAdminService {

    private static final Logger logger = LoggerFactory.getLogger(LessonAdminServiceImpl.class);
    private final LessonRepository lessonRepository;
    private final EnrollRepository enrollRepository;

    // Helper method to convert Lesson entity to AdminLessonResponseDto
    private AdminLessonResponseDto convertToAdminLessonResponseDto(Lesson lesson) {
        if (lesson == null) return null;

        Integer remainingSpots = null;
        long paidEnrollments = 0;
        long unpaidActiveEnrollments = 0;

        if (lesson.getCapacity() != null) {
            paidEnrollments = enrollRepository.countByLessonLessonIdAndPayStatus(lesson.getLessonId(), "PAID");
            unpaidActiveEnrollments = enrollRepository.countByLessonLessonIdAndStatusAndPayStatusAndExpireDtAfter(
                    lesson.getLessonId(), "APPLIED", "UNPAID", LocalDateTime.now());
            remainingSpots = lesson.getCapacity() - (int) paidEnrollments - (int) unpaidActiveEnrollments;
            if (remainingSpots < 0) remainingSpots = 0;
        }

        // currentEnrollmentCount can remain as just paid, or also include unpaid active depending on definition for admin view
        long currentEnrollmentCountForDisplay = paidEnrollments; 
        // If you want currentEnrollmentCountForDisplay to also include pending applications:
        // currentEnrollmentCountForDisplay = paidEnrollments + unpaidActiveEnrollments;

        return AdminLessonResponseDto.builder()
                .lessonId(lesson.getLessonId())
                .title(lesson.getTitle())
                .displayName(lesson.getDisplayName())
                .startDate(lesson.getStartDate())
                .endDate(lesson.getEndDate())
                .lessonYear(lesson.getStartDate() != null ? lesson.getStartDate().getYear() : null)
                .lessonMonth(lesson.getStartDate() != null ? lesson.getStartDate().getMonthValue() : null)
                .capacity(lesson.getCapacity())
                .price(lesson.getPrice())
                .instructorName(lesson.getInstructorName())
                .lessonTime(lesson.getLessonTime())
                .locationName(lesson.getLocationName())
                .registrationStartDateTime(lesson.getRegistrationStartDateTime())
                .registrationEndDateTime(lesson.getRegistrationEndDateTime())
                .createdAt(lesson.getCreatedAt())
                .createdBy(lesson.getCreatedBy())
                .createdIp(lesson.getCreatedIp())
                .updatedAt(lesson.getUpdatedAt())
                .updatedBy(lesson.getUpdatedBy())
                .updatedIp(lesson.getUpdatedIp())
                .currentEnrollmentCount((int) currentEnrollmentCountForDisplay) // Using the chosen definition
                .remainingSpots(remainingSpots)
                .build();
    }

    @Override
    @Transactional(readOnly = true)
    public Page<AdminLessonResponseDto> getAllLessonsAdmin(Pageable pageable, Integer year, Integer month) {
        Specification<Lesson> spec = LessonSpecification.filterBy( year, month);
        Page<Lesson> lessonPage = lessonRepository.findAll(spec, pageable);
        List<AdminLessonResponseDto> dtoList = lessonPage.getContent().stream()
                .map(this::convertToAdminLessonResponseDto)
                .collect(Collectors.toList());
        return new PageImpl<>(dtoList, pageable, lessonPage.getTotalElements());
    }

    @Override
    @Transactional(readOnly = true)
    public AdminLessonResponseDto getLessonByIdAdmin(Long lessonId) {
        return lessonRepository.findById(lessonId)
                .map(this::convertToAdminLessonResponseDto)
                .orElseThrow(() -> new ResourceNotFoundException("Lesson not found with id: " + lessonId, ErrorCode.LESSON_NOT_FOUND));
    }

    @Override
    public AdminLessonResponseDto createLessonAdmin(AdminLessonCreateRequestDto createRequestDto, String createdBy, String createdIp) {
        if (createRequestDto.getStartDate() == null || createRequestDto.getEndDate() == null) {
            throw new InvalidInputException("Start date and end date are required.", ErrorCode.INVALID_INPUT_VALUE);
        }
        if (createRequestDto.getStartDate().isAfter(createRequestDto.getEndDate())) {
            throw new InvalidInputException("Start date cannot be after end date.", ErrorCode.INVALID_INPUT_VALUE);
        }
        if (createRequestDto.getRegistrationStartDateTime() != null && createRequestDto.getRegistrationEndDateTime() != null &&
            createRequestDto.getRegistrationStartDateTime().isAfter(createRequestDto.getRegistrationEndDateTime())) {
            throw new InvalidInputException("Registration start time cannot be after registration end time.", ErrorCode.INVALID_INPUT_VALUE);
        }
        // Optional: Validate registrationEndDateTime is not wildly after lesson start date

        Lesson lesson = Lesson.builder()
                .title(createRequestDto.getTitle())
                .displayName(createRequestDto.getDisplayName())
                .startDate(createRequestDto.getStartDate())
                .endDate(createRequestDto.getEndDate())
                .capacity(createRequestDto.getCapacity())
                .price(createRequestDto.getPrice())
                .instructorName(createRequestDto.getInstructorName())
                .lessonTime(createRequestDto.getLessonTime())
                .locationName(createRequestDto.getLocationName())
                .registrationStartDateTime(createRequestDto.getRegistrationStartDateTime())
                .registrationEndDateTime(createRequestDto.getRegistrationEndDateTime())
                .createdBy(createdBy)
                .createdIp(createdIp)
                .build();

        Lesson savedLesson = lessonRepository.save(lesson);
        return convertToAdminLessonResponseDto(savedLesson);
    }

    @Override
    public AdminLessonResponseDto updateLessonAdmin(Long lessonId, AdminLessonUpdateRequestDto updateRequestDto, String updatedBy, String updatedIp) {
        Lesson existingLesson = lessonRepository.findById(lessonId)
                .orElseThrow(() -> new ResourceNotFoundException("Lesson not found with id: " + lessonId, ErrorCode.LESSON_NOT_FOUND));

        // Use DTO fields if provided, otherwise keep existing values
        if (updateRequestDto.getTitle() != null) existingLesson.setTitle(updateRequestDto.getTitle());
        if (updateRequestDto.getDisplayName() != null) existingLesson.setDisplayName(updateRequestDto.getDisplayName());
        if (updateRequestDto.getStartDate() != null) existingLesson.setStartDate(updateRequestDto.getStartDate());
        if (updateRequestDto.getEndDate() != null) existingLesson.setEndDate(updateRequestDto.getEndDate());
        if (updateRequestDto.getCapacity() != null) existingLesson.setCapacity(updateRequestDto.getCapacity());
        if (updateRequestDto.getPrice() != null) existingLesson.setPrice(updateRequestDto.getPrice());
        if (updateRequestDto.getInstructorName() != null) existingLesson.setInstructorName(updateRequestDto.getInstructorName());
        if (updateRequestDto.getLessonTime() != null) existingLesson.setLessonTime(updateRequestDto.getLessonTime());
        if (updateRequestDto.getLocationName() != null) existingLesson.setLocationName(updateRequestDto.getLocationName());
        if (updateRequestDto.getRegistrationStartDateTime() != null) existingLesson.setRegistrationStartDateTime(updateRequestDto.getRegistrationStartDateTime());
        if (updateRequestDto.getRegistrationEndDateTime() != null) existingLesson.setRegistrationEndDateTime(updateRequestDto.getRegistrationEndDateTime());

        // Validation after potential updates
        if (existingLesson.getStartDate() != null && existingLesson.getEndDate() != null && 
            existingLesson.getStartDate().isAfter(existingLesson.getEndDate())) {
            throw new InvalidInputException("Start date cannot be after end date.", ErrorCode.INVALID_INPUT_VALUE);
        }
        if (existingLesson.getRegistrationStartDateTime() != null && existingLesson.getRegistrationEndDateTime() != null &&
            existingLesson.getRegistrationStartDateTime().isAfter(existingLesson.getRegistrationEndDateTime())) {
            throw new InvalidInputException("Registration start time cannot be after registration end time.", ErrorCode.INVALID_INPUT_VALUE);
        }

        existingLesson.setUpdatedBy(updatedBy);
        existingLesson.setUpdatedIp(updatedIp);
        // existingLesson.setUpdatedAt(LocalDateTime.now()); // This is handled by @UpdateTimestamp

        Lesson updatedLesson = lessonRepository.save(existingLesson);
        return convertToAdminLessonResponseDto(updatedLesson);
    }

    @Override
    public void deleteLessonAdmin(Long lessonId) {
        Lesson lesson = lessonRepository.findById(lessonId)
                .orElseThrow(() -> new ResourceNotFoundException("Lesson not found with id: " + lessonId, ErrorCode.LESSON_NOT_FOUND));

        long activeEnrollments = enrollRepository.countActiveEnrollmentsForLessonDeletion(lesson.getLessonId());
        if (activeEnrollments > 0) {
            throw new BusinessRuleException(ErrorCode.LESSON_CANNOT_BE_DELETED,
                    "Lesson has " + activeEnrollments + " active enrollments and cannot be deleted.");
        }
        lessonRepository.delete(lesson);
    }

    @Override
    public AdminLessonResponseDto cloneLessonAdmin(Long lessonId, CloneLessonRequestDto cloneRequestDto, String createdBy, String createdIp) {
        Lesson originalLesson = lessonRepository.findById(lessonId)
                .orElseThrow(() -> new ResourceNotFoundException("Original lesson not found with id: " + lessonId, ErrorCode.LESSON_NOT_FOUND));

        LocalDate newStartDate;
        try {
            newStartDate = LocalDate.parse(cloneRequestDto.getNewStartDate(), DateTimeFormatter.ISO_LOCAL_DATE);
        } catch (DateTimeParseException e) {
            throw new InvalidInputException("Invalid newStartDate format. Expected YYYY-MM-DD.", ErrorCode.INVALID_INPUT_VALUE, e);
        }

        long durationDays = ChronoUnit.DAYS.between(originalLesson.getStartDate(), originalLesson.getEndDate());
        LocalDate newEndDate = newStartDate.plusDays(durationDays);
        
        // For cloned lessons, registration dates might need to be explicitly set or follow a default logic.
        // For simplicity, let's clear them, or admin can set them via an update if needed, or clone DTO can be extended.
        // Alternatively, derive them relative to the new start date similar to original logic if applicable.
        LocalDateTime newRegStartDateTime = null; // Or derive based on newStartDate
        LocalDateTime newRegEndDateTime = null;   // Or derive based on newStartDate

        if (originalLesson.getRegistrationStartDateTime() != null && originalLesson.getRegistrationEndDateTime() != null && originalLesson.getStartDate() !=null) {
            long regStartDeltaDays = ChronoUnit.DAYS.between(originalLesson.getRegistrationStartDateTime().toLocalDate(), originalLesson.getStartDate());
            long regEndDeltaDays = ChronoUnit.DAYS.between(originalLesson.getRegistrationEndDateTime().toLocalDate(), originalLesson.getStartDate());
            
            LocalTime originalRegStartTime = originalLesson.getRegistrationStartDateTime().toLocalTime();
            LocalTime originalRegEndTime = originalLesson.getRegistrationEndDateTime().toLocalTime();

            newRegStartDateTime = newStartDate.minusDays(regStartDeltaDays).atTime(originalRegStartTime);
            newRegEndDateTime = newStartDate.minusDays(regEndDeltaDays).atTime(originalRegEndTime);
        }

        Lesson clonedLesson = Lesson.builder()
                .title(originalLesson.getTitle() + " (복제)") // Default title for cloned lesson
                .displayName(originalLesson.getDisplayName() != null ? originalLesson.getDisplayName() + " (복제)" : null)
                .startDate(newStartDate)
                .endDate(newEndDate)
                .capacity(originalLesson.getCapacity())
                .price(originalLesson.getPrice())
                .instructorName(originalLesson.getInstructorName())
                .lessonTime(originalLesson.getLessonTime())
                .locationName(originalLesson.getLocationName())
                .registrationStartDateTime(newRegStartDateTime)
                .registrationEndDateTime(newRegEndDateTime)
                .createdBy(createdBy)
                .createdIp(createdIp)
                .build();

        Lesson savedClonedLesson = lessonRepository.save(clonedLesson);
        return convertToAdminLessonResponseDto(savedClonedLesson);
    }
    
    // The old methods like getAllLessons, getLessonById, createLesson, updateLesson that used LessonDto
    // and the helper convertToDto, convertToEntity have been removed as they are replaced by Admin specific DTOs/methods.
    // If they are still needed for other purposes (e.g. a generic user-facing API also managed by this service), they would need to be re-evaluated.
    // The calculateRegistrationEndDate is also removed as registration dates are now directly set via DTOs.
} 