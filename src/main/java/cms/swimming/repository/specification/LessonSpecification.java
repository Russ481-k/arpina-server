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
                    YearMonth targetMonth = YearMonth.of(year, month != null && month >= 1 && month <= 12 ? month : 1);
                    YearMonth currentMonth = YearMonth.now();
                    LocalDate today = LocalDate.now();

                    // 1. Target the correct date range based on year/month input
                    LocalDate startDate;
                    LocalDate endDate;
                    if (month != null && month >= 1 && month <= 12) {
                        startDate = targetMonth.atDay(1);
                        endDate = targetMonth.atEndOfMonth();
                    } else {
                        startDate = targetMonth.atDay(1);
                        endDate = targetMonth.with(YearMonth.of(year, 12)).atEndOfMonth();
                    }
                    predicates.add(criteriaBuilder.between(root.get("startDate"), startDate, endDate));

                    // 2. Add visibility rule for future (next month's) lessons - REMOVED FOR ADMIN
                    // VIEW
                    // The logic that hid future lessons before the 26th of the month
                    // has been removed to allow administrators to see all lessons at any time.

                } catch (DateTimeException e) {
                    logger.error("DateTimeException in LessonSpecification for year: {}, month: {}. Error: {}", year,
                            month, e.getMessage(), e);
                    throw new RuntimeException("Error processing date/time in LessonSpecification for year: " + year
                            + ", month: " + month + ". Original error: " + e.getMessage(), e);
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