package cms.payment.repository;

import cms.payment.domain.Payment;
import cms.payment.domain.PaymentStatus;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.stereotype.Repository;
import java.util.List;
import cms.enroll.domain.Enroll;
import java.util.Optional;

@Repository
public interface PaymentRepository extends JpaRepository<Payment, Long>, JpaSpecificationExecutor<Payment> {
    List<Payment> findByEnrollOrderByCreatedAtDesc(Enroll enroll);

    List<Payment> findByEnroll_User_UuidOrderByCreatedAtDesc(String userUuid); // Find by user UUID through enroll

    // KISPG specific methods
    Optional<Payment> findByTid(String tid);

    List<Payment> findByEnroll_EnrollIdOrderByCreatedAtDesc(Long enrollId);

    Optional<Payment> findByMoid(String moid);

    long countByEnrollEnrollId(Long enrollId);

    // Status-based queries
    List<Payment> findByStatusOrderByCreatedAtDesc(PaymentStatus status);

    List<Payment> findByEnroll_User_UuidAndStatusOrderByCreatedAtDesc(String userUuid, PaymentStatus status);
    // Add more custom query methods as needed
}