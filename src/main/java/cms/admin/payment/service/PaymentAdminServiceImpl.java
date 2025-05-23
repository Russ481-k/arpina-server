package cms.admin.payment.service;

import cms.admin.payment.dto.PaymentAdminDto;
import cms.payment.domain.Payment;
import cms.payment.repository.PaymentRepository;
import cms.payment.repository.specification.PaymentSpecification;
import cms.enroll.domain.Enroll;
import cms.enroll.repository.EnrollRepository; // Needed for enriching DTO
import cms.user.domain.User; // Needed for enriching DTO
import cms.swimming.domain.Lesson; // Needed for enriching DTO
import cms.common.exception.ResourceNotFoundException;
import cms.common.exception.ErrorCode;
import cms.common.exception.BusinessRuleException;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
@Transactional
public class PaymentAdminServiceImpl implements PaymentAdminService {

    private static final Logger logger = LoggerFactory.getLogger(PaymentAdminServiceImpl.class);
    private final PaymentRepository paymentRepository;
    private final EnrollRepository enrollRepository; // For DTO enrichment

    private PaymentAdminDto convertToDto(Payment payment) {
        if (payment == null) return null;
        Enroll enroll = payment.getEnroll();
        User user = enroll != null ? enroll.getUser() : null;
        Lesson lesson = enroll != null ? enroll.getLesson() : null;

        return PaymentAdminDto.builder()
                .paymentId(payment.getId())
                .enrollId(enroll != null ? enroll.getEnrollId() : null)
                .userId(user != null ? user.getUuid() : null)
                .userName(user != null ? user.getName() : null)
                .lessonTitle(lesson != null ? lesson.getTitle() : null)
                .tid(payment.getTid())
                .paidAmt(payment.getPaidAmt())
                .refundedAmt(payment.getRefundedAmt())
                .status(payment.getStatus())
                .payMethod(payment.getPayMethod())
                .pgResultCode(payment.getPgResultCode())
                .paidAt(payment.getPaidAt())
                .lastRefundDt(payment.getRefundDt())
                .pgProvider(payment.getPgProvider())
                .build();
    }

    @Override
    @Transactional(readOnly = true)
    public Page<PaymentAdminDto> getAllPayments(Long enrollId, String userId, String tid,
                                              LocalDate startDate, LocalDate endDate, String status,
                                              Pageable pageable) {
        Specification<Payment> spec = PaymentSpecification.filterByAdminCriteria(enrollId, userId, tid, startDate, endDate, status);
        Page<Payment> paymentPage = paymentRepository.findAll(spec, pageable);
        return paymentPage.map(this::convertToDto);
    }

    @Override
    @Transactional(readOnly = true)
    public PaymentAdminDto getPaymentById(Long paymentId) {
        Payment payment = paymentRepository.findById(paymentId)
                .orElseThrow(() -> new ResourceNotFoundException("Payment not found with id: " + paymentId, ErrorCode.PAYMENT_INFO_NOT_FOUND));
        return convertToDto(payment);
    }

    @Override
    public PaymentAdminDto manualRefund(Long paymentId, Integer amount, String reason, String adminNote) {
        Payment payment = paymentRepository.findById(paymentId)
                .orElseThrow(() -> new ResourceNotFoundException("Payment not found with id: " + paymentId, ErrorCode.PAYMENT_INFO_NOT_FOUND));

        if (amount <= 0) {
            throw new BusinessRuleException(ErrorCode.INVALID_INPUT_VALUE, "환불 금액은 0보다 커야 합니다.");
        }
        if (payment.getPaidAmt() == null || (payment.getRefundedAmt() != null && (payment.getRefundedAmt() + amount) > payment.getPaidAmt())) {
            throw new BusinessRuleException(ErrorCode.PAYMENT_REFUND_FAILED, "환불 금액이 결제 금액을 초과할 수 없습니다.");
        }

        logger.info("[Manual Refund] PaymentId: {}, Amount: {}, Reason: {}, AdminNote: {}", 
            paymentId, amount, reason, adminNote);

        payment.setRefundedAmt((payment.getRefundedAmt() == null ? 0 : payment.getRefundedAmt()) + amount);
        payment.setRefundDt(LocalDateTime.now());
        
        if (payment.getRefundedAmt() >= payment.getPaidAmt()) {
            payment.setStatus("CANCELED");
            Enroll enroll = payment.getEnroll();
            if (enroll != null) {
                enroll.setPayStatus("REFUNDED");
                enrollRepository.save(enroll);
            }
        } else {
            payment.setStatus("PARTIAL_REFUNDED");
            Enroll enroll = payment.getEnroll();
            if (enroll != null) {
                enroll.setPayStatus("PARTIALLY_REFUNDED");
                enrollRepository.save(enroll);
            }
        }
        Payment updatedPayment = paymentRepository.save(payment);
        return convertToDto(updatedPayment);
    }
} 