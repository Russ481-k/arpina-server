package cms.swimming.service.impl;

import cms.swimming.domain.Lesson;
import cms.swimming.dto.LessonDto;
import cms.swimming.repository.LessonRepository;
import cms.swimming.service.LessonService;
import cms.enroll.repository.EnrollRepository;
import cms.common.exception.InvalidInputException;
import cms.common.exception.ErrorCode;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import javax.persistence.EntityNotFoundException;
import java.time.LocalDate;
import java.time.LocalDateTime; // For remaining spots calculation
import java.util.List;
import javax.persistence.criteria.Predicate;
import java.util.ArrayList;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;
import org.springframework.data.jpa.domain.Specification;

@Service("swimmingLessonServiceImpl")
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class LessonServiceImpl implements LessonService {

    private static final Logger logger = LoggerFactory.getLogger(LessonServiceImpl.class);
    private final LessonRepository lessonRepository;
    private final EnrollRepository enrollRepository;

    private LessonDto convertToLessonDto(Lesson lesson) {
        if (lesson == null) return null;

        Integer remainingSpots = null;
        if (lesson.getCapacity() != null) {
            long paidEnrollments = enrollRepository.countByLessonLessonIdAndPayStatus(lesson.getLessonId(), "PAID");
            long unpaidActiveEnrollments = enrollRepository.countByLessonLessonIdAndStatusAndPayStatusAndExpireDtAfter(
                    lesson.getLessonId(), "APPLIED", "UNPAID", LocalDateTime.now());
            remainingSpots = lesson.getCapacity() - (int) paidEnrollments - (int) unpaidActiveEnrollments;
            if (remainingSpots < 0) remainingSpots = 0;
        }

        String days = null;
        String timePrefix = null;
        String timeSlot = null;
        if (lesson.getLessonTime() != null && !lesson.getLessonTime().isEmpty()) {
            String lessonTimeString = lesson.getLessonTime();
            Pattern pattern = Pattern.compile("^(?:(\\(.*?\\))\\s*)?(?:(오전|오후)\\s*)?(\\d{1,2}:\\d{2}\\s*[~-]\\s*\\d{1,2}:\\d{2})$");
            Matcher matcher = pattern.matcher(lessonTimeString.trim());
            if (matcher.find()) {
                days = matcher.group(1);
                timePrefix = matcher.group(2);
                timeSlot = matcher.group(3);
            } else {
                 if (lessonTimeString.matches("^\\d{1,2}:\\d{2}\\s*[~-]\\s*\\d{1,2}:\\d{2}$")) {
                    timeSlot = lessonTimeString.trim();
                } else {
                    logger.warn("LessonTime '{}' did not match expected patterns in LessonServiceImpl. Full string used in lessonTime field.", lessonTimeString);
                }
            }
        }

        String lessonStatusDisplay = null;
        if (lesson.getStatus() != null) {
            switch (lesson.getStatus()) {
                case OPEN: lessonStatusDisplay = "접수중"; break;
                case CLOSED: lessonStatusDisplay = "접수마감"; break;
                case ONGOING: lessonStatusDisplay = "수강중"; break;
                case COMPLETED: lessonStatusDisplay = "강습종료"; break; // Corrected typo from EnrollDto "강습종료" to "수강종료" to match other logic
                default: lessonStatusDisplay = lesson.getStatus().name();
            }
        }

        return LessonDto.fromEntity(lesson, remainingSpots, days, timePrefix, timeSlot, lessonStatusDisplay);
    }

    @Override
    public Page<LessonDto> getLessons(String status, List<Integer> months, LocalDate startDate, LocalDate endDate, Pageable pageable) {
        Specification<Lesson> spec = (root, query, criteriaBuilder) -> {
            List<Predicate> predicates = new ArrayList<>();

            if (status != null && !status.trim().isEmpty()) {
                try {
                    Lesson.LessonStatus lessonStatus = Lesson.LessonStatus.valueOf(status.toUpperCase());
                    predicates.add(criteriaBuilder.equal(root.get("status"), lessonStatus));
                } catch (IllegalArgumentException e) {
                    throw new InvalidInputException("Invalid lesson status format: " + status, ErrorCode.INVALID_INPUT_VALUE, e);
                }
            }

            if (months != null && !months.isEmpty()) {
                predicates.add(criteriaBuilder.function("MONTH", Integer.class, root.get("startDate")).in(months));
            }

            // Logic for date range: finds lessons that *overlap* with the given range.
            // A lesson (ls, le) overlaps with query range (qs, qe) if lesson_start_date <= query_end_date AND lesson_end_date >= query_start_date.
            if (startDate != null && endDate != null) {
                predicates.add(criteriaBuilder.lessThanOrEqualTo(root.get("startDate"), endDate));
                predicates.add(criteriaBuilder.greaterThanOrEqualTo(root.get("endDate"), startDate));
            } else if (startDate != null) {
                // If only startDate is given, find lessons that are ongoing or start after this date (i.e., their endDate is after this startDate).
                predicates.add(criteriaBuilder.greaterThanOrEqualTo(root.get("endDate"), startDate));
            } else if (endDate != null) {
                // If only endDate is given, find lessons that start on or before this date.
                predicates.add(criteriaBuilder.lessThanOrEqualTo(root.get("startDate"), endDate));
            }

            return criteriaBuilder.and(predicates.toArray(new Predicate[0]));
        };
        Page<Lesson> lessonPage = lessonRepository.findAll(spec, pageable);
        List<LessonDto> dtoList = lessonPage.getContent().stream()
                                        .map(this::convertToLessonDto)
                                        .collect(Collectors.toList());
        return new PageImpl<>(dtoList, pageable, lessonPage.getTotalElements());
    }

    @Override
    public LessonDto getLessonById(Long lessonId) {
        Lesson lesson = lessonRepository.findById(lessonId)
                .orElseThrow(() -> new EntityNotFoundException("강습을 찾을 수 없습니다. ID: " + lessonId));
        return convertToLessonDto(lesson);
    }

    @Override
    public long countCurrentEnrollments(Long lessonId) {
        // This counts only PAID enrollments for the specific lessonId.
        // It does not consider lesson status or date ranges directly.
        // If "current" means active lessons, additional checks on Lesson status/dates might be needed here or in calling code.
        return enrollRepository.countByLessonLessonIdAndPayStatus(lessonId, "PAID");
    }

    @Override
    public long countLockersByGender(Long lessonId, String gender) {
        if (gender == null || gender.trim().isEmpty()) {
            throw new IllegalArgumentException("Gender cannot be null or empty for counting lockers.");
        }
        // Ensure lesson exists to avoid counting lockers for non-existent lesson
        if (!lessonRepository.existsById(lessonId)) {
            throw new EntityNotFoundException("강습을 찾을 수 없습니다. ID: " + lessonId);
        }
        return enrollRepository.countUsedLockersByLessonAndUserGender(lessonId, gender.toUpperCase());
    }
} 