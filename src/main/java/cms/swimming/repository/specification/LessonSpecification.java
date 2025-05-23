package cms.swimming.repository.specification;

import cms.swimming.domain.Lesson;
import org.springframework.data.jpa.domain.Specification;
import javax.persistence.criteria.Predicate;
import java.time.LocalDate;
import java.time.YearMonth;
import java.util.ArrayList;
import java.util.List;

public class LessonSpecification {

    public static Specification<Lesson> filterBy(String status, Integer year, Integer month) {
        return (root, query, criteriaBuilder) -> {
            List<Predicate> predicates = new ArrayList<>();

            if (status != null && !status.trim().isEmpty()) {
                try {
                    Lesson.LessonStatus lessonStatus = Lesson.LessonStatus.valueOf(status.toUpperCase());
                    predicates.add(criteriaBuilder.equal(root.get("status"), lessonStatus));
                } catch (IllegalArgumentException e) {
                    // Invalid status string - effectively ignored
                }
            }

            if (year != null) {
                if (month != null && month >= 1 && month <= 12) {
                    // Filter by lessons starting in the specific year and month
                    YearMonth yearMonth = YearMonth.of(year, month);
                    LocalDate monthStart = yearMonth.atDay(1);
                    LocalDate monthEnd = yearMonth.atEndOfMonth();
                    predicates.add(criteriaBuilder.between(root.get("startDate"), monthStart, monthEnd));
                } else {
                    // Filter by lessons starting in the specific year
                    LocalDate yearStart = LocalDate.of(year, 1, 1);
                    LocalDate yearEnd = LocalDate.of(year, 12, 31);
                    predicates.add(criteriaBuilder.between(root.get("startDate"), yearStart, yearEnd));
                }
            }
            
            // Default sort order if none provided by Pageable
            if (query.getResultType().equals(Lesson.class) && query.getOrderList().isEmpty()) {
                query.orderBy(criteriaBuilder.desc(root.get("startDate")));
            }

            return criteriaBuilder.and(predicates.toArray(new Predicate[0]));
        };
    }
} 