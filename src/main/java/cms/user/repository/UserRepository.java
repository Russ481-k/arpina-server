package cms.user.repository;

import cms.user.domain.User;
import cms.user.domain.UserRoleType;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;

import java.util.List;
import java.util.Optional;

@Repository
public interface UserRepository extends JpaRepository<User, String>, JpaSpecificationExecutor<User> {
        Optional<User> findByUsername(String username);

        Optional<User> findByEmail(String email);

        Optional<User> findByResetToken(String resetToken);

        List<User> findByStatus(String status);

        boolean existsByEmail(String email);

        @Query("SELECT u FROM User u WHERE u.username LIKE %:keyword% OR u.email LIKE %:keyword%")
        List<User> searchUsers(@Param("keyword") String keyword);

        @Query("SELECT u FROM User u WHERE u.groupId = :groupId")
        List<User> findByGroupId(@Param("groupId") String groupId);

        boolean existsByUsername(String username);

        boolean existsByDi(String di);

        Optional<User> findByDi(String di);

        Optional<User> findByUuid(String uuid);

        Optional<User> findByPhone(String phone);

        // UUID prefix로 사용자를 찾는 메서드 (웹훅 temp moid 처리용)
        @Query("SELECT u FROM User u WHERE u.uuid LIKE :uuidPrefix%")
        List<User> findByUuidStartingWith(@Param("uuidPrefix") String uuidPrefix);

        Page<User> findByRole(UserRoleType role, Pageable pageable);

        @Query(value = "SELECT u.* FROM user u JOIN " +
                        "(SELECT e.user_uuid, e.pay_status, l.lesson_time, " +
                        "ROW_NUMBER() OVER(PARTITION BY e.user_uuid ORDER BY p.created_at DESC, e.created_at DESC) as rn "
                        +
                        "FROM enroll e " +
                        "JOIN lesson l ON e.lesson_id = l.lesson_id " +
                        "LEFT JOIN payment p ON e.id = p.enroll_id) as latest_enroll " +
                        "ON u.uuid = latest_enroll.user_uuid " +
                        "WHERE latest_enroll.rn = 1 " +
                        "AND u.role = 'USER' " +
                        "AND (:#{#searchKeyword == null || #searchKeyword.isEmpty()} = true OR (u.username LIKE CONCAT('%', :searchKeyword, '%') OR u.name LIKE CONCAT('%', :searchKeyword, '%') OR u.phone LIKE CONCAT('%', :searchKeyword, '%') OR latest_enroll.lesson_time LIKE CONCAT('%', :searchKeyword, '%'))) "
                        +
                        "AND (:#{#searchKeyword != null && !#searchKeyword.isEmpty()} = true OR ( " +
                        "    (:#{#username == null || #username.isEmpty()} = true OR u.username LIKE CONCAT('%', :username, '%')) "
                        +
                        "    AND (:#{#name == null || #name.isEmpty()} = true OR u.name LIKE CONCAT('%', :name, '%')) "
                        +
                        "    AND (:#{#phone == null || #phone.isEmpty()} = true OR u.phone LIKE CONCAT('%', :phone, '%')) "
                        +
                        ")) " +
                        "AND (:#{#lessonTime == null || #lessonTime.isEmpty()} = true OR latest_enroll.lesson_time LIKE CONCAT('%', :lessonTime, '%')) "
                        +
                        "AND (:#{#payStatus == null || #payStatus.isEmpty()} = true OR latest_enroll.pay_status = :payStatus)", countQuery = "SELECT count(*) FROM user u JOIN "
                                        +
                                        "(SELECT e.user_uuid, e.pay_status, l.lesson_time, " +
                                        "ROW_NUMBER() OVER(PARTITION BY e.user_uuid ORDER BY p.created_at DESC, e.created_at DESC) as rn "
                                        +
                                        "FROM enroll e " +
                                        "JOIN lesson l ON e.lesson_id = l.lesson_id " +
                                        "LEFT JOIN payment p ON e.id = p.enroll_id) as latest_enroll " +
                                        "ON u.uuid = latest_enroll.user_uuid " +
                                        "WHERE latest_enroll.rn = 1 " +
                                        "AND u.role = 'USER' " +
                                        "AND (:#{#searchKeyword == null || #searchKeyword.isEmpty()} = true OR (u.username LIKE CONCAT('%', :searchKeyword, '%') OR u.name LIKE CONCAT('%', :searchKeyword, '%') OR u.phone LIKE CONCAT('%', :searchKeyword, '%') OR latest_enroll.lesson_time LIKE CONCAT('%', :searchKeyword, '%'))) "
                                        +
                                        "AND (:#{#searchKeyword != null && !#searchKeyword.isEmpty()} = true OR ( " +
                                        "    (:#{#username == null || #username.isEmpty()} = true OR u.username LIKE CONCAT('%', :username, '%')) "
                                        +
                                        "    AND (:#{#name == null || #name.isEmpty()} = true OR u.name LIKE CONCAT('%', :name, '%')) "
                                        +
                                        "    AND (:#{#phone == null || #phone.isEmpty()} = true OR u.phone LIKE CONCAT('%', :phone, '%')) "
                                        +
                                        ")) " +
                                        "AND (:#{#lessonTime == null || #lessonTime.isEmpty()} = true OR latest_enroll.lesson_time LIKE CONCAT('%', :lessonTime, '%')) "
                                        +
                                        "AND (:#{#payStatus == null || #payStatus.isEmpty()} = true OR latest_enroll.pay_status = :payStatus)", nativeQuery = true)
        Page<User> findUsersWithEnrollmentFilters(
                        @Param("username") String username,
                        @Param("name") String name,
                        @Param("phone") String phone,
                        @Param("lessonTime") String lessonTime,
                        @Param("payStatus") String payStatus,
                        @Param("searchKeyword") String searchKeyword,
                        Pageable pageable);
}