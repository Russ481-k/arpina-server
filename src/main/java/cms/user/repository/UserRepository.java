package cms.user.repository;

import cms.user.domain.User;
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
} 