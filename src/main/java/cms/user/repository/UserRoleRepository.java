package cms.user.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import cms.user.domain.UserRole;

import java.util.List;
import java.util.Optional;

@Repository
public interface UserRoleRepository extends JpaRepository<UserRole, Long> {
    
    Optional<UserRole> findByRoleName(String roleName);
    
    @Query("SELECT r FROM UserRole r WHERE r.isActive = true")
    List<UserRole> findAllActiveRoles();
    
    @Query("SELECT r FROM UserRole r WHERE r.roleName LIKE %:keyword%")
    List<UserRole> searchByRoleName(@Param("keyword") String keyword);
    
    List<UserRole> findByRoleType(String roleType);
    
    boolean existsByRoleName(String roleName);
} 