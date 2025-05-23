package cms.admin.payment.service;

import cms.admin.payment.dto.PaymentAdminDto;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import java.time.LocalDate;

public interface PaymentAdminService {
    Page<PaymentAdminDto> getAllPayments(Long enrollId, String userId, String tid, 
                                         LocalDate startDate, LocalDate endDate, String status, 
                                         Pageable pageable);
    PaymentAdminDto getPaymentById(Long paymentId);
    PaymentAdminDto manualRefund(Long paymentId, Integer amount, String reason, String adminNote);
} 