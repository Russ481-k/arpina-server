package cms.user.domain;

import org.springframework.data.jpa.domain.Specification;
import javax.persistence.criteria.Predicate;
import java.util.ArrayList;
import java.util.List;

import cms.user.domain.User;
import cms.user.domain.UserRoleType;

public class UserSpecification {

    public static Specification<User> search(String username, String name, String phone, String searchKeyword) {
        return (root, query, criteriaBuilder) -> {
            List<Predicate> predicates = new ArrayList<>();

            if (searchKeyword != null && !searchKeyword.trim().isEmpty()) {
                // searchKeyword가 있으면 통합 검색
                Predicate searchPredicate = criteriaBuilder.or(
                        criteriaBuilder.like(root.get("username"), "%" + searchKeyword + "%"),
                        criteriaBuilder.like(root.get("name"), "%" + searchKeyword + "%"),
                        criteriaBuilder.like(root.get("phone"), "%" + searchKeyword + "%"));
                predicates.add(searchPredicate);
            } else {
                // searchKeyword가 없으면 개별 검색
                if (username != null && !username.trim().isEmpty()) {
                    predicates.add(criteriaBuilder.like(root.get("username"), "%" + username + "%"));
                }
                if (name != null && !name.trim().isEmpty()) {
                    predicates.add(criteriaBuilder.like(root.get("name"), "%" + name + "%"));
                }
                if (phone != null && !phone.trim().isEmpty()) {
                    predicates.add(criteriaBuilder.like(root.get("phone"), "%" + phone + "%"));
                }
            }

            // 역할(Role)은 항상 USER로 고정
            predicates.add(criteriaBuilder.equal(root.get("role"), UserRoleType.USER));

            return criteriaBuilder.and(predicates.toArray(new Predicate[0]));
        };
    }
}