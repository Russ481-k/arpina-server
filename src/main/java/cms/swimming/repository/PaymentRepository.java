package cms.swimming.repository;

import cms.swimming.domain.Payment;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.Optional;

@Repository
public interface PaymentRepository extends JpaRepository<Payment, Long> {
    
    // 특정 사용자의 결제 내역 조회
    Page<Payment> findByEnrollUserId(Long userId, Pageable pageable);
    
    // 특정 기간의 결제 내역 조회
    Page<Payment> findByCreatedAtBetween(LocalDateTime startDate, LocalDateTime endDate, Pageable pageable);
    
    // 특정 상태의 결제 내역 조회
    Page<Payment> findByStatus(Payment.PaymentStatus status, Pageable pageable);
    
    // 특정 PG사 거래ID로 결제 내역 조회
    Optional<Payment> findByTid(String tid);
    
    // 특정 신청ID로 결제 내역 조회
    Optional<Payment> findByEnrollEnrollId(Long enrollId);
} 