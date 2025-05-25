package cms.enroll.repository.specification;

import cms.enroll.domain.Enroll;
import cms.swimming.domain.Lesson;
import cms.user.domain.User;
import org.springframework.data.jpa.domain.Specification;
import javax.persistence.criteria.Join;
import javax.persistence.criteria.Predicate;
import java.util.ArrayList;
import java.util.List;
import java.time.LocalDate;
import java.time.YearMonth;

public class EnrollSpecification {

    public static Specification<Enroll> filterByAdminCriteria(
            Long lessonId, String userId, String payStatus, String cancelStatus, Integer year, Integer month, boolean excludeUnpaid) {
        return (root, query, criteriaBuilder) -> {
            List<Predicate> predicates = new ArrayList<>();
            Join<Enroll, Lesson> lessonJoin = null;
            Join<Enroll, User> userJoin = null;

            if (lessonId != null) {
                if (lessonJoin == null) lessonJoin = root.join("lesson");
                predicates.add(criteriaBuilder.equal(lessonJoin.get("lessonId"), lessonId));
            }

            if (userId != null && !userId.trim().isEmpty()) {
                if (userJoin == null) userJoin = root.join("user");
                predicates.add(criteriaBuilder.equal(userJoin.get("uuid"), userId));
            }

            if (payStatus != null && !payStatus.trim().isEmpty()) {
                predicates.add(criteriaBuilder.equal(criteriaBuilder.upper(root.get("payStatus")), payStatus.toUpperCase()));
            }
            
            if (cancelStatus != null && !cancelStatus.trim().isEmpty()) {
                try {
                    Enroll.CancelStatusType csType = Enroll.CancelStatusType.valueOf(cancelStatus.toUpperCase());
                    predicates.add(criteriaBuilder.equal(root.get("cancelStatus"), csType));
                } catch (IllegalArgumentException e) {
                    // Invalid cancel status string - effectively ignored
                }
            }

            if (year != null) {
                if (lessonJoin == null) lessonJoin = root.join("lesson");
                if (month != null && month >= 1 && month <= 12) {
                    YearMonth yearMonth = YearMonth.of(year, month);
                    LocalDate monthStart = yearMonth.atDay(1);
                    LocalDate monthEnd = yearMonth.atEndOfMonth();
                    predicates.add(criteriaBuilder.between(lessonJoin.get("startDate"), monthStart, monthEnd));
                } else {
                    LocalDate yearStart = LocalDate.of(year, 1, 1);
                    LocalDate yearEnd = LocalDate.of(year, 12, 31);
                    predicates.add(criteriaBuilder.between(lessonJoin.get("startDate"), yearStart, yearEnd));
                }
            }

            if (excludeUnpaid) {
                predicates.add(criteriaBuilder.notEqual(criteriaBuilder.upper(root.get("payStatus")), "UNPAID"));
            }

            // Ensure a default sort order if query.orderBy() is empty to avoid issues with pagination
            if (query.getResultType().equals(Enroll.class) && query.getOrderList().isEmpty()) {
                query.orderBy(criteriaBuilder.desc(root.get("createdAt")));
            }

            return criteriaBuilder.and(predicates.toArray(new Predicate[0]));
        };
    }
} 