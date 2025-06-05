package cms.payment.repository.specification;

import cms.payment.domain.Payment;
import cms.payment.domain.PaymentStatus;
import cms.enroll.domain.Enroll;
import cms.user.domain.User;
import org.springframework.data.jpa.domain.Specification;
import javax.persistence.criteria.Join;
import javax.persistence.criteria.Predicate;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.ArrayList;
import java.util.List;

public class PaymentSpecification {

    public static Specification<Payment> filterByAdminCriteria(
            Long enrollId, String userId, String tid,
            LocalDate startDate, LocalDate endDate, PaymentStatus status) {
        return (root, query, criteriaBuilder) -> {
            List<Predicate> predicates = new ArrayList<>();
            Join<Payment, Enroll> enrollJoin = null;
            Join<Enroll, User> userJoin = null; // Declare userJoin here

            if (enrollId != null) {
                if (enrollJoin == null)
                    enrollJoin = root.join("enroll");
                predicates.add(criteriaBuilder.equal(enrollJoin.get("enrollId"), enrollId));
            }

            if (userId != null && !userId.trim().isEmpty()) {
                if (enrollJoin == null)
                    enrollJoin = root.join("enroll");
                if (userJoin == null)
                    userJoin = enrollJoin.join("user"); // Assign userJoin
                predicates.add(criteriaBuilder.equal(userJoin.get("uuid"), userId));
            }

            if (tid != null && !tid.trim().isEmpty()) {
                predicates.add(criteriaBuilder.like(root.get("tid"), "%" + tid + "%"));
            }

            if (status != null) {
                predicates.add(criteriaBuilder.equal(root.get("status"), status));
            }

            if (startDate != null) {
                predicates.add(criteriaBuilder.greaterThanOrEqualTo(root.get("paidAt"),
                        LocalDateTime.of(startDate, LocalTime.MIN)));
            }

            if (endDate != null) {
                predicates.add(criteriaBuilder.lessThanOrEqualTo(root.get("paidAt"),
                        LocalDateTime.of(endDate, LocalTime.MAX)));
            }

            if (query.getResultType().equals(Payment.class) && query.getOrderList().isEmpty()) {
                query.orderBy(criteriaBuilder.desc(root.get("paidAt"))); // Changed to paidAt
            }

            return criteriaBuilder.and(predicates.toArray(new Predicate[0]));
        };
    }
}