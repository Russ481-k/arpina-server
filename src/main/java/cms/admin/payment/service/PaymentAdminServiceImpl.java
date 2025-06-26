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
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.LocalDateTime;
import cms.payment.domain.PaymentStatus;

@Service
@RequiredArgsConstructor
@Transactional
public class PaymentAdminServiceImpl implements PaymentAdminService {

    private static final Logger logger = LoggerFactory.getLogger(PaymentAdminServiceImpl.class);
    private final PaymentRepository paymentRepository;
    private final EnrollRepository enrollRepository; // For DTO enrichment

    @Override
    @Transactional(readOnly = true)
    public Page<PaymentAdminDto> getAllPayments(Long lessonId, Long enrollId, String userId, String tid,
            LocalDate startDate, LocalDate endDate, PaymentStatus status,
            Pageable pageable) {
        Specification<Payment> spec = PaymentSpecification.filterByAdminCriteria(lessonId, enrollId, userId, tid,
                startDate,
                endDate, status);
        Page<Payment> paymentPage = paymentRepository.findAll(spec, pageable);
        return paymentPage.map(this::convertToPaymentAdminDto);
    }

    @Override
    @Transactional(readOnly = true)
    public PaymentAdminDto getPaymentById(Long paymentId) {
        Payment payment = paymentRepository.findById(paymentId)
                .orElseThrow(() -> new ResourceNotFoundException("Payment not found with id: " + paymentId,
                        ErrorCode.PAYMENT_INFO_NOT_FOUND));
        return convertToPaymentAdminDto(payment);
    }

    @Override
    @Transactional
    public PaymentAdminDto manualRefund(Long paymentId, int amount, String reason, String adminNote) {
        Payment payment = paymentRepository.findById(paymentId)
                .orElseThrow(() -> new ResourceNotFoundException("결제 정보를 찾을 수 없습니다: " + paymentId,
                        ErrorCode.PAYMENT_INFO_NOT_FOUND));

        // Ensure payment is in a state that allows refund
        if (payment.getStatus() != PaymentStatus.PAID && payment.getStatus() != PaymentStatus.PARTIAL_REFUNDED) {
            throw new BusinessRuleException("환불 가능한 결제 상태가 아닙니다. 현재 상태: " + payment.getStatus().getDescription(),
                    ErrorCode.INVALID_PAYMENT_STATUS_FOR_OPERATION);
        }

        int currentRefundedAmount = payment.getRefundedAmt() != null ? payment.getRefundedAmt() : 0;
        int totalPaidAmount = payment.getPaidAmt() != null ? payment.getPaidAmt() : 0;

        if (amount <= 0) {
            throw new BusinessRuleException("환불 금액은 0보다 커야 합니다.", ErrorCode.INVALID_INPUT_VALUE);
        }
        if (amount + currentRefundedAmount > totalPaidAmount) {
            throw new BusinessRuleException(
                    "환불 금액이 결제 금액을 초과할 수 없습니다. (기 환불액: " + currentRefundedAmount + ", 총 결제액: " + totalPaidAmount + ")",
                    ErrorCode.INVALID_INPUT_VALUE);
        }

        payment.setRefundedAmt(currentRefundedAmount + amount);
        payment.setRefundDt(LocalDateTime.now());
        // payment.setAdminNote(adminNote); // Assuming Payment entity has adminNote
        // field and you want to set it

        if (payment.getRefundedAmt() >= totalPaidAmount) {
            payment.setStatus(PaymentStatus.CANCELED); // Full refund
            // Update related Enroll status if applicable
            Enroll enroll = payment.getEnroll();
            if (enroll != null) {
                enroll.setPayStatus("REFUNDED"); // Assuming Enroll has payStatus as String. Adapt if it's an Enum too.
                // enroll.setCancelStatus(Enroll.CancelStatusType.APPROVED); // Or similar,
                // depending on business logic
                enrollRepository.save(enroll);
            }
        } else {
            payment.setStatus(PaymentStatus.PARTIAL_REFUNDED); // Partial refund
            Enroll enroll = payment.getEnroll();
            if (enroll != null) {
                enroll.setPayStatus("PARTIAL_REFUNDED"); // Assuming Enroll has payStatus as String.
                enrollRepository.save(enroll);
            }
        }

        Payment updatedPayment = paymentRepository.save(payment);
        return convertToPaymentAdminDto(updatedPayment);
    }

    private PaymentAdminDto convertToPaymentAdminDto(Payment payment) {
        if (payment == null)
            return null;

        Enroll enroll = payment.getEnroll();
        User user = enroll != null ? enroll.getUser() : null;
        Lesson lesson = enroll != null ? enroll.getLesson() : null;

        // VAT and supply amount calculations are kept, but not added to DTO if fields
        // are missing
        int finalPaidAmount = payment.getPaidAmt() != null ? payment.getPaidAmt() : 0;
        int vatAmount = 0;
        int supplyAmount = 0;
        if (finalPaidAmount > 0) {
            vatAmount = finalPaidAmount / 11;
            supplyAmount = finalPaidAmount - vatAmount;
        }

        PaymentAdminDto.PaymentAdminDtoBuilder builder = PaymentAdminDto.builder()
                .paymentId(payment.getId())
                .enrollId(enroll != null ? enroll.getEnrollId() : null)
                .userId(user != null ? user.getUsername() : null)
                .userName(user != null ? user.getName() : null)
                .userPhone(user != null ? user.getPhone() : null)
                .lessonTitle(lesson != null ? lesson.getTitle() : null)
                .tid(payment.getTid())
                .paidAmt(payment.getPaidAmt()) // Original paidAmt
                .refundedAmt(payment.getRefundedAmt())
                .payMethod(payment.getPayMethod())
                .status(payment.getStatus().getDbValue())
                .pgResultCode(payment.getPgResultCode())
                .pgProvider("Nice Pay") // Set pgProvider to Nice Pay
                .paidAt(payment.getPaidAt());

        if (payment.getRefundDt() != null) {
            builder.lastRefundDt(payment.getRefundDt());
        }

        return builder.build();
    }
}