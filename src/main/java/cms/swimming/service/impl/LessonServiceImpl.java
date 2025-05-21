package cms.swimming.service.impl;

import cms.swimming.domain.Lesson;
import cms.swimming.dto.LessonDto;
import cms.swimming.repository.LessonRepository;
import cms.swimming.service.LessonService;
import cms.enroll.repository.EnrollRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import javax.persistence.EntityNotFoundException;
import java.time.LocalDate;
import java.util.List;
import java.util.stream.Collectors;
import javax.persistence.criteria.Predicate;
import java.util.ArrayList;
import org.springframework.data.jpa.domain.Specification;

@Service("swimmingLessonServiceImpl")
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class LessonServiceImpl implements LessonService {

    private final LessonRepository lessonRepository;
    private final EnrollRepository enrollRepository;

    @Override
    public Page<LessonDto> getLessons(String status, List<Integer> months, LocalDate startDate, LocalDate endDate, Pageable pageable) {
        Specification<Lesson> spec = (root, query, criteriaBuilder) -> {
            List<Predicate> predicates = new ArrayList<>();

            if (status != null && !status.trim().isEmpty()) {
                try {
                    Lesson.LessonStatus lessonStatus = Lesson.LessonStatus.valueOf(status.toUpperCase());
                    predicates.add(criteriaBuilder.equal(root.get("status"), lessonStatus));
                } catch (IllegalArgumentException e) {
                    // TODO: Log this error or throw a specific business exception for invalid status format
                    // For instance: throw new BusinessRuleException(ErrorCode.INVALID_LESSON_STATUS_FORMAT);
                    // Depending on requirements, can also choose to ignore or add a predicate for no results.
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
        return lessonRepository.findAll(spec, pageable).map(LessonDto::fromEntity);
    }

    @Override
    public LessonDto getLessonById(Long lessonId) {
        Lesson lesson = lessonRepository.findById(lessonId)
                .orElseThrow(() -> new EntityNotFoundException("강습을 찾을 수 없습니다. ID: " + lessonId));
        return LessonDto.fromEntity(lesson);
    }

    @Override
    public long countCurrentEnrollments(Long lessonId) {
        return lessonRepository.countCurrentEnrollments(lessonId);
    }

    @Override
    public long countLockersByGender(Long lessonId, String gender) {
        if (gender == null || gender.trim().isEmpty()) {
            throw new IllegalArgumentException("Gender cannot be null or empty for counting lockers.");
        }
        return enrollRepository.countUsedLockersByLessonAndUserGender(lessonId, gender.toUpperCase());
    }
} 