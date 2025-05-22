package cms.payment.repository;

import cms.payment.domain.Payment;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;
import java.util.List;
import cms.enroll.domain.Enroll;
import java.util.Optional;

@Repository
public interface PaymentRepository extends JpaRepository<Payment, Long> {
    List<Payment> findByEnrollOrderByCreatedAtDesc(Enroll enroll);
    List<Payment> findByEnroll_User_UuidOrderByCreatedAtDesc(String userUuid); // Find by user UUID through enroll
    
    // Methods needed by KispgWebhookServiceImpl
    Optional<Payment> findByTid(String tid);
    Optional<Payment> findByEnroll_EnrollId(Long enrollId);
    // Add more custom query methods as needed
} 