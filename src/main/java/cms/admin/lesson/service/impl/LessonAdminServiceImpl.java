package cms.admin.lesson.service.impl;

import cms.swimming.dto.LessonDto;
import cms.swimming.domain.Lesson;
import cms.swimming.repository.LessonRepository;
import cms.swimming.repository.specification.LessonSpecification;
// import cms.swimming.service.LessonService; // Not directly used for now
import cms.admin.lesson.dto.CloneLessonRequestDto;
import cms.admin.lesson.service.LessonAdminService;
import cms.common.exception.ResourceNotFoundException;
import cms.common.exception.ErrorCode;
import cms.common.exception.BusinessRuleException; // For deleteLesson check
import cms.common.exception.InvalidInputException;
import cms.enroll.repository.EnrollRepository; // For deleteLesson check
import lombok.RequiredArgsConstructor;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
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
        return lessonRepository.findById(lessonId)
                .map(this::convertToDto)
                .orElseThrow(() -> new ResourceNotFoundException("Lesson not found with id: " + lessonId, ErrorCode.LESSON_NOT_FOUND));
    }

    @Override
    public LessonDto createLesson(LessonDto lessonDto) {
        if (lessonDto.getStartDate() == null || lessonDto.getEndDate() == null) {
            throw new InvalidInputException("Start date and end date are required.", ErrorCode.INVALID_INPUT_VALUE);
        }
        if (lessonDto.getRegistrationEndDate() == null) {
            throw new InvalidInputException("Registration end date is required.", ErrorCode.INVALID_INPUT_VALUE);
        }
        if (lessonDto.getStartDate().isAfter(lessonDto.getEndDate())) {
            throw new InvalidInputException("Start date cannot be after end date.", ErrorCode.INVALID_INPUT_VALUE);
        }
        if (lessonDto.getRegistrationEndDate().isAfter(lessonDto.getStartDate())) {
            throw new InvalidInputException("Registration end date cannot be after start date.", ErrorCode.INVALID_INPUT_VALUE);
        }
        Lesson lesson = convertToEntity(lessonDto);
        // createdBy, createdIp 등은 AuditorAware 등으로 자동 설정되거나, SecurityContext에서 가져와 설정
        Lesson savedLesson = lessonRepository.save(lesson);
        return convertToDto(savedLesson);
    }

    @Override
    public LessonDto updateLesson(Long lessonId, LessonDto lessonDto) {
        Lesson existingLesson = lessonRepository.findById(lessonId)
                .orElseThrow(() -> new ResourceNotFoundException("Lesson not found with id: " + lessonId, ErrorCode.LESSON_NOT_FOUND));

        // Determine effective values for update
        String title = lessonDto.getTitle() != null ? lessonDto.getTitle() : existingLesson.getTitle();
        LocalDate startDate = lessonDto.getStartDate() != null ? lessonDto.getStartDate() : existingLesson.getStartDate();
        LocalDate endDate = lessonDto.getEndDate() != null ? lessonDto.getEndDate() : existingLesson.getEndDate();
        Integer capacity = lessonDto.getCapacity() != null ? lessonDto.getCapacity() : existingLesson.getCapacity();
        Integer price = lessonDto.getPrice() != null ? lessonDto.getPrice() : existingLesson.getPrice();
        String instructorName = lessonDto.getInstructorName() != null ? lessonDto.getInstructorName() : existingLesson.getInstructorName();
        String lessonTime = lessonDto.getLessonTime() != null ? lessonDto.getLessonTime() : existingLesson.getLessonTime();

        Lesson.LessonStatus newStatus = existingLesson.getStatus(); // Default to existing status
        if (lessonDto.getStatus() != null && !lessonDto.getStatus().trim().isEmpty()) {
            try {
                newStatus = Lesson.LessonStatus.valueOf(lessonDto.getStatus().toUpperCase());
            } catch (IllegalArgumentException e) {
                throw new InvalidInputException("Invalid lesson status: " + lessonDto.getStatus(), ErrorCode.INVALID_INPUT_VALUE);
            }
        }

        LocalDate registrationEndDate;
        if (lessonDto.getRegistrationEndDate() != null) {
            registrationEndDate = lessonDto.getRegistrationEndDate();
        } else {
            // If DTO doesn't provide it, it means "don't change it" or client assumes server has it.
            // We take the existing one. If existing is null (bad data), subsequent validation will catch it.
            registrationEndDate = existingLesson.getRegistrationEndDate();
        }

        // Validations for resolved values
        if (startDate == null) { // Should ideally not happen if existingLesson is valid and DTO doesn't nullify it
             throw new InvalidInputException("Start date is required.", ErrorCode.INVALID_INPUT_VALUE);
        }
        if (endDate == null) { // Should ideally not happen
             throw new InvalidInputException("End date is required.", ErrorCode.INVALID_INPUT_VALUE);
        }
        if (registrationEndDate == null) {
            throw new InvalidInputException("Registration end date is required and cannot be null.", ErrorCode.INVALID_INPUT_VALUE);
        }

        if (startDate.isAfter(endDate)) {
            throw new InvalidInputException("Start date cannot be after end date.", ErrorCode.INVALID_INPUT_VALUE);
        }
        if (registrationEndDate.isAfter(startDate)) {
            throw new InvalidInputException("Registration end date cannot be after start date.", ErrorCode.INVALID_INPUT_VALUE);
        }
        // Other fields like title, capacity, price usually have @NotNull or similar on DTO or default values.

        // Use the entity's updateDetails method with validated and resolved values
        existingLesson.updateDetails(
                title,
                startDate,
                endDate,
                capacity,
                price,
                newStatus,
                registrationEndDate, // This is now guaranteed non-null and validated
                instructorName,
                lessonTime
        );

        // The check 'existingLesson.getStartDate().isAfter(existingLesson.getEndDate())'
        // that was here previously is now covered by the validation above using resolved 'startDate' and 'endDate'.

        Lesson updatedLesson = lessonRepository.save(existingLesson);
        return convertToDto(updatedLesson);
    }

    @Override
    public void deleteLesson(Long lessonId) {
        Lesson lesson = lessonRepository.findById(lessonId)
                .orElseThrow(() -> new ResourceNotFoundException("Lesson not found with id: " + lessonId, ErrorCode.LESSON_NOT_FOUND));

        // 문서 규칙: "강습 삭제 (조건부)"
        // 예: 활성 신청 내역이 없는 경우에만 삭제 가능
        // EnrollRepository에 정의된 countActiveEnrollmentsForLessonDeletion 메서드 사용
        long activeEnrollments = enrollRepository.countActiveEnrollmentsForLessonDeletion(lesson.getLessonId());
        if (activeEnrollments > 0) {
            throw new BusinessRuleException(ErrorCode.LESSON_CANNOT_BE_DELETED,
                    "Lesson has " + activeEnrollments + " active enrollments and cannot be deleted.");
        }
        lessonRepository.delete(lesson);
    }

    @Override
    @Transactional(readOnly = true)
    public Page<LessonDto> getAllLessonsAdmin(Pageable pageable) {
        // 관리자용 API는 모든 상태의 강습을 조회하거나, 별도의 필터링 조건이 있을 수 있음
        // 여기서는 모든 강습을 페이지네이션하여 반환
        return lessonRepository.findAll(pageable).map(this::convertToDto);
    }

    @Override
    @Transactional(readOnly = true)
    public Page<LessonDto> getLessonsByStatusAdmin(String status, Pageable pageable) {
        if (status == null || status.trim().isEmpty()) {
            throw new InvalidInputException("Status parameter is required for filtering.", ErrorCode.INVALID_INPUT_VALUE);
        }
        try {
            Lesson.LessonStatus lessonStatus = Lesson.LessonStatus.valueOf(status.toUpperCase());
            Specification<Lesson> spec = (root, query, criteriaBuilder) ->
                    criteriaBuilder.equal(root.get("status"), lessonStatus);
            return lessonRepository.findAll(spec, pageable).map(this::convertToDto);
        } catch (IllegalArgumentException e) {
            throw new InvalidInputException("Invalid lesson status: " + status, ErrorCode.INVALID_INPUT_VALUE);
        }
    }

    @Override
    @Transactional(readOnly = true)
    public LessonDto getLessonByIdAdmin(Long lessonId) {
        // getLessonById와 동일한 로직을 사용해도 무방하나, 관리자용으로 별도 권한 체크 등이 추가될 수 있음
        return getLessonById(lessonId);
    }

    @Override
    public LessonDto cloneLesson(Long lessonId, CloneLessonRequestDto cloneRequestDto) {
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
        
        // registrationEndDate도 원본의 시작일-종료일 간격과 유사하게 설정하거나, 특정 규칙(예: 새 시작일 며칠 전)으로 설정
        LocalDate newRegistrationEndDate;
        if (originalLesson.getRegistrationEndDate() != null && originalLesson.getStartDate() != null) {
            long registrationDurationDays = ChronoUnit.DAYS.between(originalLesson.getRegistrationEndDate(), originalLesson.getStartDate());
            newRegistrationEndDate = newStartDate.minusDays(registrationDurationDays);
        } else {
            newRegistrationEndDate = newStartDate.minusDays(1); // Default: 하루 전
        }


        Lesson clonedLesson = Lesson.builder()
                .title(originalLesson.getTitle() + " (복제)") // 기본적으로 복제본임을 명시
                .startDate(newStartDate)
                .endDate(newEndDate)
                .registrationEndDate(newRegistrationEndDate)
                .capacity(originalLesson.getCapacity())
                .price(originalLesson.getPrice())
                .instructorName(originalLesson.getInstructorName())
                .lessonTime(originalLesson.getLessonTime())
                .status(Lesson.LessonStatus.OPEN) // 복제된 강습은 기본적으로 OPEN 상태로 시작
                // createdBy, createdIp 등은 AuditorAware 등으로 자동 설정
                .build();

        Lesson savedClonedLesson = lessonRepository.save(clonedLesson);
        return convertToDto(savedClonedLesson);
    }

    // Helper to convert DTO to Entity for creation/update
    // Assumes LessonDto has all necessary fields that match Lesson entity structure or builder
    private Lesson convertToEntity(LessonDto lessonDto) {
        if (lessonDto == null) return null;
        Lesson.LessonBuilder builder = Lesson.builder()
                .title(lessonDto.getTitle())
                .startDate(lessonDto.getStartDate())
                .endDate(lessonDto.getEndDate())
                .registrationEndDate(lessonDto.getRegistrationEndDate())
                .capacity(lessonDto.getCapacity())
                .price(lessonDto.getPrice())
                .instructorName(lessonDto.getInstructorName())
                .lessonTime(lessonDto.getLessonTime());

        if (lessonDto.getStatus() != null) {
            try {
                builder.status(Lesson.LessonStatus.valueOf(lessonDto.getStatus().toUpperCase()));
            } catch (IllegalArgumentException e) {
                throw new InvalidInputException("Invalid lesson status: " + lessonDto.getStatus(), ErrorCode.INVALID_INPUT_VALUE);
            }
        } else {
            builder.status(Lesson.LessonStatus.OPEN); // Default status for new lessons if not provided
        }
        return builder.build();
    }
} 