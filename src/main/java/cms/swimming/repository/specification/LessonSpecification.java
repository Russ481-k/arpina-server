package cms.swimming.repository.specification;

import cms.swimming.domain.Lesson;
import org.springframework.data.jpa.domain.Specification;
import javax.persistence.criteria.Predicate;
import java.time.LocalDate;
import java.time.YearMonth;
import java.time.DateTimeException;
import java.util.ArrayList;
import java.util.List;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class LessonSpecification {

    private static final Logger logger = LoggerFactory.getLogger(LessonSpecification.class);

    public static Specification<Lesson> filterBy(Integer year, Integer month) {
        return (root, query, criteriaBuilder) -> {
            List<Predicate> predicates = new ArrayList<>();

            if (year != null) {
                try {
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
                } catch (DateTimeException e) { 
                    logger.error("DateTimeException in LessonSpecification for year: {}, month: {}. Error: {}", year, month, e.getMessage(), e);
                    // To make the error propagation clear during debugging and ensure transaction rollback
                    throw new RuntimeException("Error processing date/time in LessonSpecification for year: " + year + ", month: " + month + ". Original error: " + e.getMessage(), e);
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