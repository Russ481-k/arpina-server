package cms.mypage.service;

import cms.enroll.domain.Enroll;
import cms.enroll.domain.Enroll.CancelStatusType;
import cms.enroll.repository.EnrollRepository;
import cms.mypage.dto.PaymentDto;
import cms.payment.domain.Payment;
import cms.payment.repository.PaymentRepository;
import cms.pg.service.PaymentGatewayService;
import cms.user.domain.User;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.ZoneOffset;
import java.util.List;
import java.util.stream.Collectors;

@Service
@Transactional
public class MypagePaymentServiceImpl implements MypagePaymentService {

    private final PaymentRepository paymentRepository;
    private final EnrollRepository enrollRepository;
    private final PaymentGatewayService paymentGatewayService;

    public MypagePaymentServiceImpl(PaymentRepository paymentRepository, 
                                    EnrollRepository enrollRepository,
                                    @Qualifier("mockPaymentGatewayService") PaymentGatewayService paymentGatewayService) {
        this.paymentRepository = paymentRepository;
        this.enrollRepository = enrollRepository;
        this.paymentGatewayService = paymentGatewayService;
    }

    @Override
    @Transactional(readOnly = true)
    public Page<PaymentDto> getPaymentHistory(User user, Pageable pageable) {
        List<Payment> payments = paymentRepository.findByEnroll_User_UuidOrderByCreatedAtDesc(user.getUuid());

        int start = (int) pageable.getOffset();
        int end = Math.min((start + pageable.getPageSize()), payments.size());
        List<PaymentDto> dtoList = payments.subList(start, end).stream()
                .map(this::convertToPaymentDto)
                .collect(Collectors.toList());
        return new PageImpl<>(dtoList, pageable, payments.size());
    }

    @Override
    public void requestPaymentCancellation(User user, Long paymentId) {
        Payment payment = paymentRepository.findById(paymentId)
                .orElseThrow(() -> new RuntimeException("Payment not found with ID: " + paymentId));
        
        Enroll enroll = payment.getEnroll();
        if (enroll == null) {
            throw new IllegalStateException("Enrollment information not found for this payment.");
        }
        if (!enroll.getUser().getUuid().equals(user.getUuid())) {
            throw new SecurityException("You do not have permission to cancel this payment.");
        }

        if (!"SUCCESS".equalsIgnoreCase(payment.getStatus())) {
            throw new IllegalStateException("Payment is not in a cancellable state (must be SUCCESS). Current status: " + payment.getStatus());
        }
        
        if (enroll.getCancelStatus() != null && enroll.getCancelStatus() != CancelStatusType.NONE) {
             throw new IllegalStateException("Enrollment cancellation is already in progress, completed, or denied.");
        }

        boolean refundInitiated = paymentGatewayService.requestRefund(
            payment.getPgToken(), 
            payment.getMerchantUid(), 
            payment.getAmount(),
            "User requested cancellation via My Page"
        );

        if (refundInitiated) {
            payment.setStatus("REFUND_REQUESTED");
            paymentRepository.save(payment);

            enroll.setCancelStatus(CancelStatusType.REQ);
            enrollRepository.save(enroll);
            
            // TODO: Notify admin about the refund request
        } else {
            throw new RuntimeException("Failed to initiate payment cancellation with Payment Gateway for payment ID: " + paymentId);
        }
    }

    private PaymentDto convertToPaymentDto(Payment payment) {
        if (payment == null) return null;
        PaymentDto dto = new PaymentDto();
        dto.setPaymentId(payment.getId());
        if (payment.getEnroll() != null) {
            dto.setEnrollId(payment.getEnroll().getEnrollId());
        }
        dto.setAmount(payment.getAmount());
        if (payment.getPaidAt() != null) {
            dto.setPaidAt(payment.getPaidAt().atOffset(ZoneOffset.UTC));
        }
        dto.setStatus(payment.getStatus());
        return dto;
    }
} 