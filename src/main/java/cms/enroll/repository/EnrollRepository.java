package cms.enroll.repository;

import cms.enroll.domain.Enroll;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;
import java.util.List;
import cms.user.domain.User;

@Repository
public interface EnrollRepository extends JpaRepository<Enroll, Long> {
    List<Enroll> findByUserOrderByCreatedAtDesc(User user);
    List<Enroll> findByUserAndStatusOrderByCreatedAtDesc(User user, String status);
    // Add more custom query methods as needed, e.g., for checking existing enrollments
} 