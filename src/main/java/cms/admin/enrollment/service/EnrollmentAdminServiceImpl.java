package cms.admin.enrollment.service;

import cms.admin.enrollment.dto.CancelRequestAdminDto;
import cms.admin.enrollment.dto.EnrollAdminResponseDto;
import cms.admin.enrollment.dto.DiscountStatusUpdateRequestDto;
import cms.enroll.domain.Enroll;
import cms.enroll.repository.EnrollRepository;
import cms.enroll.repository.specification.EnrollSpecification;
import cms.enroll.service.EnrollmentService; // Main service for approve/deny logic
import cms.payment.domain.Payment;
import cms.payment.repository.PaymentRepository;
import cms.common.exception.ResourceNotFoundException;
import cms.common.exception.ErrorCode;
import cms.common.exception.BusinessRuleException;
import cms.locker.service.LockerService;
import cms.enroll.domain.Enroll.DiscountStatusType;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
@Transactional
public class EnrollmentAdminServiceImpl implements EnrollmentAdminService {

    private final EnrollRepository enrollRepository;
    private final PaymentRepository paymentRepository;
    private final EnrollmentService enrollmentService; // For approve/deny AND calculating display refund
    private final LockerService lockerService;

    @Value("${app.default-locker-fee:5000}")
    private int defaultLockerFee;

    private static final Logger logger = LoggerFactory.getLogger(EnrollmentAdminServiceImpl.class);

    private EnrollAdminResponseDto convertToEnrollAdminResponseDto(Enroll enroll) {
        if (enroll == null) return null;
        Payment payment = paymentRepository.findByEnroll_EnrollId(enroll.getEnrollId()).orElse(null);
        EnrollAdminResponseDto.PaymentInfoForEnrollAdmin paymentInfo = null;
        if (payment != null) {
            paymentInfo = EnrollAdminResponseDto.PaymentInfoForEnrollAdmin.builder()
                    .tid(payment.getTid())
                    .paidAmt(payment.getPaidAmt())
                    .refundedAmt(payment.getRefundedAmt())
                    .payMethod(payment.getPayMethod())
                    .paidAt(payment.getPaidAt())
                    .build();
        }

        return EnrollAdminResponseDto.builder()
                .enrollId(enroll.getEnrollId())
                .userId(enroll.getUser() != null ? enroll.getUser().getUuid() : null)
                .userName(enroll.getUser() != null ? enroll.getUser().getName() : null)
                .userPhone(enroll.getUser() != null ? enroll.getUser().getPhone() : null)
                .status(enroll.getStatus())
                .payStatus(enroll.getPayStatus())
                .usesLocker(enroll.isUsesLocker())
                .lockerAllocated(enroll.isLockerAllocated())
                .userGender(enroll.getUser() != null ? enroll.getUser().getGender() : null)
                .createdAt(enroll.getCreatedAt())
                .expireDt(enroll.getExpireDt())
                .lessonId(enroll.getLesson() != null ? enroll.getLesson().getLessonId() : null)
                .lessonTitle(enroll.getLesson() != null ? enroll.getLesson().getTitle() : null)
                .payment(paymentInfo)
                .cancelStatus(enroll.getCancelStatus() != null ? enroll.getCancelStatus().name() : null)
                .cancelReason(enroll.getCancelReason())
                .build();
    }

    private CancelRequestAdminDto convertToCancelRequestAdminDto(Enroll enroll) {
        if (enroll == null) return null;
        Payment payment = paymentRepository.findByEnroll_EnrollId(enroll.getEnrollId())
                .orElseThrow(() -> new ResourceNotFoundException("Payment not found for enroll: " + enroll.getEnrollId(), ErrorCode.PAYMENT_INFO_NOT_FOUND));

        int originalLessonFee = (enroll.getLesson() != null && enroll.getLesson().getPrice() != null) ? enroll.getLesson().getPrice() : 0;
        int originalLockerFee = 0;
        if (enroll.isUsesLocker() || enroll.isLockerAllocated()) {
             if (payment.getPaidAmt() != null && payment.getPaidAmt() > originalLessonFee) {
                originalLockerFee = payment.getPaidAmt() - originalLessonFee;
            } else {
                originalLockerFee = 0; 
            }
        }
        
        CancelRequestAdminDto.PaymentDetailsForCancel paymentDetails = CancelRequestAdminDto.PaymentDetailsForCancel.builder()
                .tid(payment.getTid())
                .paidAmt(payment.getPaidAmt())
                .lessonPaidAmt(originalLessonFee)
                .lockerPaidAmt(originalLockerFee)
                .build();

        // Use the centralized method from EnrollmentService to calculate display refund amount
        BigDecimal calculatedRefundDecimal = enrollmentService.calculateDisplayRefundAmount(enroll.getEnrollId());
        int calculatedRefundInt = calculatedRefundDecimal.intValue();

        return CancelRequestAdminDto.builder()
                .enrollId(enroll.getEnrollId())
                .userId(enroll.getUser() != null ? enroll.getUser().getUuid() : null)
                .userName(enroll.getUser() != null ? enroll.getUser().getName() : null)
                .lessonTitle(enroll.getLesson() != null ? enroll.getLesson().getTitle() : null)
                .paymentInfo(paymentDetails)
                .calculatedRefundAmtByNewPolicy(calculatedRefundInt) // Using the result from service
                .requestedAt(enroll.getUpdatedAt()) // Or a dedicated request timestamp field
                .userReason(enroll.getCancelReason())
                .build();
    }

    @Override
    @Transactional(readOnly = true)
    public Page<EnrollAdminResponseDto> getAllEnrollments(Integer year, Integer month, Long lessonId, String userId, String payStatus, Pageable pageable) {
        Specification<Enroll> spec = EnrollSpecification.filterByAdminCriteria(lessonId, userId, payStatus, null, year, month);
        Page<Enroll> enrollPage = enrollRepository.findAll(spec, pageable);
        return enrollPage.map(this::convertToEnrollAdminResponseDto);
    }

    @Override
    @Transactional(readOnly = true)
    public EnrollAdminResponseDto getEnrollmentById(Long enrollId) {
        Enroll enroll = enrollRepository.findById(enrollId)
                .orElseThrow(() -> new ResourceNotFoundException("Enrollment not found with id: " + enrollId, ErrorCode.ENROLLMENT_NOT_FOUND));
        return convertToEnrollAdminResponseDto(enroll);
    }

    @Override
    @Transactional(readOnly = true)
    public Page<CancelRequestAdminDto> getCancelRequests(String status, Pageable pageable) {
        Specification<Enroll> spec = EnrollSpecification.filterByAdminCriteria(null, null, null, status, null, null);
        Page<Enroll> enrollPage = enrollRepository.findAll(spec, pageable);
        return enrollPage.map(this::convertToCancelRequestAdminDto);
    }

    @Override
    public EnrollAdminResponseDto approveCancellationWithManualDays(Long enrollId, String adminComment, Integer manualUsedDays) {
        enrollmentService.approveEnrollmentCancellationAdmin(enrollId, manualUsedDays);
        Enroll updatedEnroll = enrollRepository.findById(enrollId)
            .orElseThrow(() -> new ResourceNotFoundException("Enrollment not found after approval: " + enrollId, ErrorCode.ENROLLMENT_NOT_FOUND));
        
        if (adminComment != null && !adminComment.isEmpty()) {
            String currentReason = updatedEnroll.getCancelReason() == null ? "" : updatedEnroll.getCancelReason();
            updatedEnroll.setCancelReason(currentReason + " [Admin: " + adminComment + "]"); 
            enrollRepository.save(updatedEnroll);
        }
        return convertToEnrollAdminResponseDto(updatedEnroll);
    }

    @Override
    public EnrollAdminResponseDto denyCancellation(Long enrollId, String adminComment) {
        enrollmentService.denyEnrollmentCancellationAdmin(enrollId, adminComment); 
        Enroll updatedEnroll = enrollRepository.findById(enrollId)
            .orElseThrow(() -> new ResourceNotFoundException("Enrollment not found after denial: " + enrollId, ErrorCode.ENROLLMENT_NOT_FOUND));
        return convertToEnrollAdminResponseDto(updatedEnroll);
    }

    @Override
    @Transactional
    public EnrollAdminResponseDto adminCancelEnrollment(Long enrollId, String adminComment) {
        Enroll enroll = enrollRepository.findById(enrollId)
            .orElseThrow(() -> new ResourceNotFoundException("신청 정보를 찾을 수 없습니다.", ErrorCode.ENROLLMENT_NOT_FOUND));

        logger.info("관리자에 의한 직접 취소 처리 (enrollId: {}). 사유: {}", enrollId, adminComment);

        boolean lockerWasAllocated = enroll.isLockerAllocated();

        enroll.setStatus("CANCELED_BY_ADMIN");
        enroll.setPayStatus("REFUND_PENDING_ADMIN_CANCEL");
        enroll.setCancelStatus(Enroll.CancelStatusType.APPROVED);
        enroll.setCancelReason("관리자 직접 취소: " + adminComment);
        enroll.setAdminCancelComment(adminComment);
        enroll.setCancelApprovedAt(LocalDateTime.now());

        if (lockerWasAllocated) {
            String userGender = enroll.getUser().getGender();
            if (userGender != null && !userGender.trim().isEmpty()) {
                lockerService.decrementUsedQuantity(userGender.toUpperCase());
                enroll.setLockerAllocated(false);
            }
        }
        enroll.setUsesLocker(false);
        enroll.setLockerPgToken(null);

        Enroll savedEnroll = enrollRepository.save(enroll);

        return convertToEnrollAdminResponseDto(savedEnroll);
    }

    @Override
    @Transactional
    public EnrollAdminResponseDto updateEnrollmentDiscountStatus(Long enrollId, DiscountStatusUpdateRequestDto request) {
        Enroll enroll = enrollRepository.findById(enrollId)
            .orElseThrow(() -> new ResourceNotFoundException("신청 정보를 찾을 수 없습니다.", ErrorCode.ENROLLMENT_NOT_FOUND));

        enroll.setDiscountType(request.getDiscountType());
        try {
            enroll.setDiscountStatus(Enroll.DiscountStatusType.valueOf(request.getDiscountStatus().toUpperCase()));
        } catch (IllegalArgumentException e) {
            throw new BusinessRuleException(ErrorCode.INVALID_INPUT_VALUE, "유효하지 않은 할인 상태값입니다: " + request.getDiscountStatus());
        }
        
        if (enroll.getDiscountStatus() == DiscountStatusType.APPROVED || enroll.getDiscountStatus() == DiscountStatusType.DENIED) {
            enroll.setDiscountApprovedAt(LocalDateTime.now());
        }
        enroll.setDiscountAdminComment(request.getAdminComment());

        Enroll savedEnroll = enrollRepository.save(enroll);

        return convertToEnrollAdminResponseDto(savedEnroll);
    }
} 