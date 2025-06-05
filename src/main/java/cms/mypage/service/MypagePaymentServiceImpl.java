package cms.mypage.service;

import cms.enroll.domain.Enroll;
import cms.enroll.domain.Enroll.CancelStatusType;
import cms.enroll.repository.EnrollRepository;
import cms.mypage.dto.PaymentDto;
import cms.payment.domain.Payment;
import cms.payment.repository.PaymentRepository;
import cms.pg.service.PaymentGatewayService;
import cms.user.domain.User;
import cms.common.exception.BusinessRuleException;
import cms.common.exception.ErrorCode;
import cms.common.exception.ResourceNotFoundException;
import cms.payment.domain.PaymentStatus;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;
import org.springframework.http.HttpStatus; // For potential use in BusinessRuleException
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
                .orElseThrow(() -> new ResourceNotFoundException("결제 정보를 찾을 수 없습니다 (ID: " + paymentId + ")",
                        ErrorCode.PAYMENT_INFO_NOT_FOUND));

        Enroll enroll = payment.getEnroll();
        if (enroll == null) {
            throw new BusinessRuleException("결제에 연결된 수강 신청 정보를 찾을 수 없습니다.", ErrorCode.ENROLLMENT_NOT_FOUND,
                    HttpStatus.INTERNAL_SERVER_ERROR);
        }
        if (!enroll.getUser().getUuid().equals(user.getUuid())) {
            throw new BusinessRuleException(ErrorCode.ACCESS_DENIED, HttpStatus.FORBIDDEN);
        }

        if (payment.getStatus() != PaymentStatus.PAID) {
            throw new BusinessRuleException("성공 상태의 결제만 취소할 수 있습니다. 현재 상태: " + payment.getStatus().getDescription(),
                    ErrorCode.PAYMENT_CANCEL_NOT_ALLOWED);
        }

        if (enroll.getCancelStatus() != null && enroll.getCancelStatus() != CancelStatusType.NONE) {
            throw new BusinessRuleException("이미 취소 절차가 진행 중이거나 완료/거절된 수강 신청입니다.",
                    ErrorCode.ALREADY_CANCELLED_ENROLLMENT);
        }

        boolean refundInitiated;
        try {
            refundInitiated = paymentGatewayService.requestRefund(
                    payment.getTid(),
                    payment.getMoid(),
                    payment.getPaidAmt(),
                    "User requested cancellation via My Page");
        } catch (Exception e) {
            throw new BusinessRuleException("결제 게이트웨이를 통한 환불 요청에 실패했습니다. 잠시 후 다시 시도해주세요.",
                    ErrorCode.PAYMENT_REFUND_FAILED);
        }

        if (refundInitiated) {
            payment.setStatus(PaymentStatus.REFUND_REQUESTED);
            paymentRepository.save(payment);

            enroll.setCancelStatus(CancelStatusType.REQ);
            enrollRepository.save(enroll);

            // TODO: Notify admin about the refund request
        } else {
            throw new BusinessRuleException("결제 게이트웨이에서 환불 요청 처리를 시작하지 못했습니다.", ErrorCode.PAYMENT_REFUND_FAILED);
        }
    }

    private PaymentDto convertToPaymentDto(Payment payment) {
        if (payment == null)
            return null;

        PaymentDto dto = new PaymentDto();
        dto.setPaymentId(payment.getId());
        dto.setAmount(payment.getPaidAmt() != null ? payment.getPaidAmt() : 0);

        if (payment.getPaidAt() != null) {
            dto.setPaidAt(payment.getPaidAt().atOffset(ZoneOffset.UTC));
        }
        if (payment.getStatus() != null) {
            dto.setStatus(payment.getStatus().getDbValue());
        } else {
            dto.setStatus(null);
        }

        // Enroll 정보 설정
        Enroll enroll = payment.getEnroll();
        if (enroll != null) {
            dto.setEnrollId(enroll.getEnrollId());
            dto.setUsesLocker(enroll.isUsesLocker());
            dto.setFinalAmount(payment.getPaidAmt() != null ? payment.getPaidAmt()
                    : (enroll.getFinalAmount() != null ? enroll.getFinalAmount() : 0));
            dto.setDiscountPercentage(enroll.getDiscountAppliedPercentage());

            // 회원권 유형
            if (enroll.getMembershipType() != null) {
                dto.setMembershipType(enroll.getMembershipType().name());
            }

            // 할인 유형 설정
            if (enroll.getDiscountType() != null) {
                dto.setDiscountType(enroll.getDiscountType());
            }

            // Lesson 정보 설정
            if (enroll.getLesson() != null) {
                dto.setLessonTitle(enroll.getLesson().getTitle());
                dto.setLessonStartDate(enroll.getLesson().getStartDate());
                dto.setLessonEndDate(enroll.getLesson().getEndDate());
                dto.setLessonTime(enroll.getLesson().getLessonTime());
                dto.setInstructorName(enroll.getLesson().getInstructorName());
                dto.setLocationName(enroll.getLesson().getLocationName());
                dto.setLessonPrice(enroll.getLesson().getPrice());
            }
        }

        // Payment 필드에서 사물함 요금 설정
        if (payment.getLockerAmount() != null) {
            dto.setLockerFee(payment.getLockerAmount());
        }

        return dto;
    }
}