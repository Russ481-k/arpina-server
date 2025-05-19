package cms.payment.repository;

import cms.payment.domain.Payment;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;
import java.util.List;
import cms.enroll.domain.Enroll;

@Repository
public interface PaymentRepository extends JpaRepository<Payment, Long> {
    List<Payment> findByEnrollOrderByCreatedAtDesc(Enroll enroll);
    List<Payment> findByEnroll_User_UuidOrderByCreatedAtDesc(String userUuid); // Find by user UUID through enroll
    // Add more custom query methods as needed
} 