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
import java.util.stream.Collectors;
import java.util.Arrays;

public class EnrollSpecification {

    public static Specification<Enroll> filterByAdminCriteria(
            Long lessonId, String userId, String payStatus, String cancelStatus, Integer year, Integer month,
            boolean excludeUnpaidParam) {
        return (root, query, criteriaBuilder) -> {
            List<Predicate> predicates = new ArrayList<>();
            Join<Enroll, Lesson> lessonJoin = null;
            Join<Enroll, User> userJoin = null;

            if (lessonId != null) {
                if (lessonJoin == null)
                    lessonJoin = root.join("lesson");
                predicates.add(criteriaBuilder.equal(lessonJoin.get("lessonId"), lessonId));
            }

            if (userId != null && !userId.trim().isEmpty()) {
                if (userJoin == null)
                    userJoin = root.join("user");
                predicates.add(criteriaBuilder.equal(userJoin.get("uuid"), userId));
            }

            // Default payStatus filtering for general enrollment list
            if (payStatus != null && !payStatus.trim().isEmpty()) {
                predicates.add(
                        criteriaBuilder.equal(criteriaBuilder.upper(root.get("payStatus")), payStatus.toUpperCase()));
            } else {
                // If no specific payStatus is given, default to PAID or UNPAID for the general
                // list
                Predicate paid = criteriaBuilder.equal(criteriaBuilder.upper(root.get("payStatus")), "PAID");
                Predicate unpaid = criteriaBuilder.equal(criteriaBuilder.upper(root.get("payStatus")), "UNPAID");
                predicates.add(criteriaBuilder.or(paid, unpaid));
            }

            // Exclude canceled statuses for the general enrollment list
            // Assuming 'status' field holds values like 'APPLIED', 'CANCELED',
            // 'CANCELED_BY_ADMIN'
            List<String> excludedStatuses = Arrays.asList("CANCELED", "CANCELED_BY_ADMIN"); // Add other canceled status
                                                                                            // if any
            predicates.add(criteriaBuilder.not(root.get("status").in(excludedStatuses)));

            if (cancelStatus != null && !cancelStatus.trim().isEmpty()) {
                try {
                    Enroll.CancelStatusType csType = Enroll.CancelStatusType.valueOf(cancelStatus.toUpperCase());
                    predicates.add(criteriaBuilder.equal(root.get("cancelStatus"), csType));
                } catch (IllegalArgumentException e) {
                    // Invalid cancel status string - effectively ignored
                }
            }

            if (year != null) {
                if (lessonJoin == null)
                    lessonJoin = root.join("lesson");
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

            // The excludeUnpaidParam from controller is for getAllEnrollments, typically
            // false.
            // If it's true for some reason, and payStatus was not specified (defaulting to
            // PAID/UNPAID),
            // then UNPAID would be excluded. If payStatus *was* specified as UNPAID, this
            // param wouldn't override that.
            // This existing excludeUnpaidParam seems to conflict a bit with the new default
            // PAID/UNPAID logic.
            // For now, keeping its effect. If payStatus is not specified,
            // excludeUnpaidParam=true will make it PAID only.
            if (excludeUnpaidParam) {
                predicates.add(criteriaBuilder.notEqual(criteriaBuilder.upper(root.get("payStatus")), "UNPAID"));
            }

            // Ensure a default sort order if query.orderBy() is empty to avoid issues with
            // pagination
            if (query.getResultType().equals(Enroll.class) && query.getOrderList().isEmpty()) {
                query.orderBy(criteriaBuilder.desc(root.get("createdAt")));
            }

            return criteriaBuilder.and(predicates.toArray(new Predicate[0]));
        };
    }

    public static Specification<Enroll> filterForCancelAndRefundManagement(
            Long lessonId,
            List<Enroll.CancelStatusType> cancelStatusList,
            List<String> payStatusInList,
            boolean useCombinedLogic,
            boolean excludeUnpaid) {
        return (root, query, criteriaBuilder) -> {
            List<Predicate> finalAndPredicates = new ArrayList<>();

            // 1. Lesson ID Filter
            if (lessonId != null) {
                Join<Enroll, Lesson> lessonJoin = root.join("lesson"); // Ensure join is made
                finalAndPredicates.add(criteriaBuilder.equal(lessonJoin.get("lessonId"), lessonId));
            }

            // 2. Core Cancel/Refund Logic Predicate
            Predicate coreCancelRefundPredicate;

            // Define the base conditions for what constitutes a "cancel/refund item"
            // This logic will be used for useCombinedLogic=true, AND as a fallback if
            // explicit filters are empty.
            Predicate userCancelRequests = criteriaBuilder.equal(root.get("cancelStatus"), Enroll.CancelStatusType.REQ);
            Predicate adminPendingRefund = criteriaBuilder.and(
                    criteriaBuilder.equal(root.get("cancelStatus"), Enroll.CancelStatusType.APPROVED),
                    criteriaBuilder.equal(criteriaBuilder.upper(root.get("payStatus")), "REFUND_PENDING_ADMIN_CANCEL"));

            // Include items with ANY cancelStatus that is not NONE. This is the simplest
            // way.
            Predicate anyCancelStatus = criteriaBuilder.isNotNull(root.get("cancelStatus"));
            Predicate notNoneCancelStatus = criteriaBuilder.notEqual(root.get("cancelStatus"),
                    Enroll.CancelStatusType.NONE);

            Predicate baseCancelRefundConditions = criteriaBuilder.and(anyCancelStatus, notNoneCancelStatus);

            if (useCombinedLogic) {
                // A. Default view for /cancel-requests without specific filters
                coreCancelRefundPredicate = baseCancelRefundConditions;
            } else {
                // B. Explicit status filters provided by the client
                List<Predicate> explicitOrPredicates = new ArrayList<>();
                if (cancelStatusList != null && !cancelStatusList.isEmpty()) {
                    explicitOrPredicates.add(root.get("cancelStatus").in(cancelStatusList));
                }
                if (payStatusInList != null && !payStatusInList.isEmpty()) {
                    List<String> upperCasePayStatusInList = payStatusInList.stream()
                            .map(String::toUpperCase)
                            .collect(Collectors.toList());
                    explicitOrPredicates.add(criteriaBuilder.upper(root.get("payStatus")).in(upperCasePayStatusInList));
                }

                if (!explicitOrPredicates.isEmpty()) {
                    coreCancelRefundPredicate = criteriaBuilder.or(explicitOrPredicates.toArray(new Predicate[0]));
                } else {
                    // No explicit status filters were given, but lessonId might be (handled above).
                    // In this case (e.g., only lessonId is provided), we should still apply the
                    // base cancel/refund criteria.
                    coreCancelRefundPredicate = baseCancelRefundConditions;
                }
            }
            finalAndPredicates.add(coreCancelRefundPredicate);

            // 3. Unpaid Exclusion (Conditional)
            // For "cancel/refund management", we generally want to see all relevant items,
            // including UNPAID CANCELED items (like enrollId: 21).
            // So, the 'excludeUnpaid' parameter should typically be false for this specific
            // API.
            if (excludeUnpaid) {
                // This will only apply if the service layer explicitly sets excludeUnpaid to
                // true.
                Predicate unpaidPredicate = criteriaBuilder.notEqual(criteriaBuilder.upper(root.get("payStatus")),
                        "UNPAID");
                finalAndPredicates.add(unpaidPredicate);
            }

            Predicate finalPredicateConstruct;
            if (finalAndPredicates.isEmpty()) {
                // This should not happen if baseCancelRefundConditions is always applied when
                // no explicit filters.
                // But as a fallback, prevent returning everything. Return nothing or throw
                // error.
                // For now, returning a predicate that matches nothing.
                finalPredicateConstruct = criteriaBuilder.disjunction(); // Always false
            } else {
                finalPredicateConstruct = criteriaBuilder.and(finalAndPredicates.toArray(new Predicate[0]));
            }

            // Default sort order
            if (query.getResultType().equals(Enroll.class) && query.getOrderList().isEmpty()) {
                query.orderBy(criteriaBuilder.desc(root.get("createdAt"))); // or root.get("updatedAt") or specific
                                                                            // cancel date
            }

            return finalPredicateConstruct;
        };
    }

    public static Specification<Enroll> filterByStatus(String status) {
        // Example basic implementation, assuming 'status' is a direct field in Enroll
        // This method was causing a linter error for not returning.
        // Returning null is a valid way to indicate no specification if an empty or
        // invalid status is passed,
        // or it can be adapted to throw an error or return a "match all" / "match none"
        // spec.
        if (status == null || status.trim().isEmpty()) {
            return null; // Or (root, query, criteriaBuilder) -> criteriaBuilder.conjunction(); for match
                         // all
        }
        return (root, query, criteriaBuilder) -> criteriaBuilder.equal(criteriaBuilder.upper(root.get("status")),
                status.toUpperCase());
    }
}