package egov.com.uss.umt.repository;

import cms.user.domain.User;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

@Repository
public interface UserManageRepository extends JpaRepository<User, Long> {
    User findByUsername(String userId);
    
    @Query("SELECT u FROM User u WHERE (:searchCondition = 'USER_ID' AND u.username LIKE CONCAT('%',:searchKeyword,'%')) OR (:searchCondition = 'USER_NM' AND u.name LIKE CONCAT('%',:searchKeyword,'%')) OR (:searchCondition = 'EMAIL' AND u.email LIKE CONCAT('%',:searchKeyword,'%'))")
    Page<User> searchUsers(
        @Param("searchCondition") String searchCondition,
        @Param("searchKeyword") String searchKeyword,
        Pageable pageable
    );
} 