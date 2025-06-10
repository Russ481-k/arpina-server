package cms.admin.payment.service;

import cms.admin.payment.dto.PaymentAdminDto;
import cms.payment.domain.PaymentStatus;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import java.time.LocalDate;

public interface PaymentAdminService {
    Page<PaymentAdminDto> getAllPayments(Long lessonId, Long enrollId, String userId, String tid,
            LocalDate startDate, LocalDate endDate, PaymentStatus status,
            Pageable pageable);

    PaymentAdminDto getPaymentById(Long paymentId);

    PaymentAdminDto manualRefund(Long paymentId, int amount, String reason, String adminNote);
}