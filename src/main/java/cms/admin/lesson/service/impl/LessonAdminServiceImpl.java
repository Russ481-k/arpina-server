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
import java.time.YearMonth;

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
        if (lessonDto.getStartDate().isAfter(lessonDto.getEndDate())) {
            throw new InvalidInputException("Start date cannot be after end date.", ErrorCode.INVALID_INPUT_VALUE);
        }

        LocalDate lessonStartDate = lessonDto.getStartDate();
        LocalDate calculatedRegEndDate = calculateRegistrationEndDate(lessonStartDate);
        lessonDto.setRegistrationEndDate(calculatedRegEndDate); // Set calculated date

        if (lessonDto.getRegistrationEndDate().isAfter(lessonDto.getStartDate())) {
            // This should ideally not happen if calculateRegistrationEndDate is correct
            throw new InvalidInputException("Calculated registration end date cannot be after start date. Logic error.", ErrorCode.INTERNAL_SERVER_ERROR);
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

        String title = lessonDto.getTitle() != null ? lessonDto.getTitle() : existingLesson.getTitle();
        LocalDate startDate = lessonDto.getStartDate() != null ? lessonDto.getStartDate() : existingLesson.getStartDate();
        LocalDate endDate = lessonDto.getEndDate() != null ? lessonDto.getEndDate() : existingLesson.getEndDate();
        Integer capacity = lessonDto.getCapacity() != null ? lessonDto.getCapacity() : existingLesson.getCapacity();
        Integer price = lessonDto.getPrice() != null ? lessonDto.getPrice() : existingLesson.getPrice();
        String instructorName = lessonDto.getInstructorName() != null ? lessonDto.getInstructorName() : existingLesson.getInstructorName();
        String lessonTime = lessonDto.getLessonTime() != null ? lessonDto.getLessonTime() : existingLesson.getLessonTime();

        Lesson.LessonStatus newStatus = existingLesson.getStatus();
        if (lessonDto.getStatus() != null && !lessonDto.getStatus().trim().isEmpty()) {
            try {
                newStatus = Lesson.LessonStatus.valueOf(lessonDto.getStatus().toUpperCase());
            } catch (IllegalArgumentException e) {
                throw new InvalidInputException("Invalid lesson status: " + lessonDto.getStatus(), ErrorCode.INVALID_INPUT_VALUE);
            }
        }

        // Always recalculate registrationEndDate based on the (potentially updated) startDate
        LocalDate registrationEndDate = calculateRegistrationEndDate(startDate);

        if (startDate == null || endDate == null) { // Should be caught by DTO validation or defaults
             throw new InvalidInputException("Start date and end date are required.", ErrorCode.INVALID_INPUT_VALUE);
        }
        if (registrationEndDate == null) { // Should not happen with calculation
            throw new InvalidInputException("Registration end date is required and cannot be null.", ErrorCode.INVALID_INPUT_VALUE);
        }
        if (startDate.isAfter(endDate)) {
            throw new InvalidInputException("Start date cannot be after end date.", ErrorCode.INVALID_INPUT_VALUE);
        }
        if (registrationEndDate.isAfter(startDate)) {
            // This should ideally not happen if calculateRegistrationEndDate is correct
            throw new InvalidInputException("Calculated registration end date cannot be after start date. Logic error.", ErrorCode.INTERNAL_SERVER_ERROR);
        }

        existingLesson.updateDetails(
                title,
                startDate,
                endDate,
                capacity,
                price,
                newStatus,
                registrationEndDate, // Use the calculated date
                instructorName,
                lessonTime
        );
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
        
        LocalDate newRegistrationEndDate = calculateRegistrationEndDate(newStartDate);

        Lesson clonedLesson = Lesson.builder()
                .title(originalLesson.getTitle() + " (복제)")
                .startDate(newStartDate)
                .endDate(newEndDate)
                .registrationEndDate(newRegistrationEndDate) // Use calculated date
                .capacity(originalLesson.getCapacity())
                .price(originalLesson.getPrice())
                .instructorName(originalLesson.getInstructorName())
                .lessonTime(originalLesson.getLessonTime())
                .status(Lesson.LessonStatus.OPEN)
                .build();

        Lesson savedClonedLesson = lessonRepository.save(clonedLesson);
        return convertToDto(savedClonedLesson);
    }

    private LocalDate calculateRegistrationEndDate(LocalDate lessonStartDate) {
        if (lessonStartDate == null) {
            throw new InvalidInputException("Lesson start date is required to calculate registration end date.", ErrorCode.INVALID_INPUT_VALUE);
        }
        YearMonth lessonStartYearMonth = YearMonth.from(lessonStartDate);
        // For any lesson, new registrations (not renewals) close at the end of the month *before* the lesson starts,
        // OR, if the lesson is in the current month, at the end of the current month.
        // The user-facing service (EnrollmentServiceImpl) will handle specific windows (20-25 for renewal, 26-EOM for new for *next* month).
        // Lesson.registrationEndDate should be the absolute final day for any type of new registration.

        // If lesson starts this month, registration is open until end of this month.
        // If lesson starts next month (or later), registration is open until end of *this* current month.
        // This logic reflects the NEW registration part of the policy for users.
        // Renewal period (20-25 of current month for next month lesson) is handled in EnrollmentService.

        // Simplified: the latest a *new* enrollment for a lesson can occur.
        // For a lesson starting in month M:
        // - If M is current month: EOM of M.
        // - If M is a future month: EOM of M-1.
        // This 'registrationEndDate' is the gate for lesson.status=OPEN from an admin perspective.
        
        LocalDate today = LocalDate.now(); // Consider the date of admin operation for context
        YearMonth currentAdminOpMonth = YearMonth.from(today);

        if (lessonStartYearMonth.equals(currentAdminOpMonth)) {
            // Lesson starts in the same month as the admin is operating.
            // New registrations for this lesson should be allowed until EOM of this month.
            return lessonStartYearMonth.atEndOfMonth();
        } else if (lessonStartYearMonth.isAfter(currentAdminOpMonth)) {
            // Lesson starts in a future month.
            // New registrations for this future lesson are allowed until EOM of the month *before* the lesson starts.
            return lessonStartYearMonth.minusMonths(1).atEndOfMonth();
        } else {
            // Lesson starts in a past month (should not typically happen for new/cloned lessons being set to OPEN)
            // Default to a date that's clearly in the past or handle as error.
            // For safety, let's make it the day before the lesson start, or just the lesson start date.
            // Given it's for admin, and they might be fixing old data, let's be slightly lenient.
            // But the primary rule is: registrationEndDate shouldn't be after startDate.
            // If a lesson from past is being made OPEN, its reg end date should also be in past.
            return lessonStartDate.minusDays(1); // Or handle error: "Cannot set open registration for past lesson"
        }
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