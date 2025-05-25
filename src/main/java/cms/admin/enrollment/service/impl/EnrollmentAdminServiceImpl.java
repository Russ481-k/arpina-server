package cms.admin.enrollment.service.impl;

import cms.admin.enrollment.dto.CancelRequestAdminDto;
import cms.admin.enrollment.dto.EnrollAdminResponseDto;
import cms.admin.enrollment.service.EnrollmentAdminService;
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
import cms.user.domain.User;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;

import cms.admin.enrollment.dto.CalculatedRefundDetailsDto;

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
        logger.info("Attempting to convert Enroll to EnrollAdminResponseDto for enrollId: {}", enroll != null ? enroll.getEnrollId() : "null_enroll_object");
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
                .userLoginId(enroll.getUser() != null ? enroll.getUser().getUsername() : null)
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
        logger.info("Attempting to convert Enroll to CancelRequestAdminDto for enrollId: {}", enroll != null ? enroll.getEnrollId() : "null_enroll_object");
        if (enroll == null) return null;

        Payment payment = paymentRepository.findByEnroll_EnrollId(enroll.getEnrollId()).orElse(null); // UNPAID의 경우 Payment가 없을 수 있음

        CancelRequestAdminDto.PaymentDetailsForCancel paymentDetails = null;
        CalculatedRefundDetailsDto refundDetailsDto = null;
        int calculatedRefundInt = 0;

        if (payment != null) { // Payment 정보가 있는 경우에만 처리
            int originalLessonFee = (enroll.getLesson() != null && enroll.getLesson().getPrice() != null) ? enroll.getLesson().getPrice() : 0;
            int originalLockerFee = 0;
            if (enroll.isUsesLocker() || enroll.isLockerAllocated()) {
                 if (payment.getPaidAmt() != null && payment.getPaidAmt() > originalLessonFee) {
                    originalLockerFee = payment.getLockerAmount() != null ? payment.getLockerAmount() : defaultLockerFee; 
                    if (payment.getPaidAmt() < originalLessonFee + originalLockerFee) {
                        originalLockerFee = payment.getPaidAmt() - originalLessonFee > 0 ? payment.getPaidAmt() - originalLessonFee : 0;
                    }
                } else {
                    originalLockerFee = 0;
                }
            }
            
            paymentDetails = CancelRequestAdminDto.PaymentDetailsForCancel.builder()
                    .tid(payment.getTid())
                    .paidAmt(payment.getPaidAmt())
                    .lessonPaidAmt(payment.getLessonAmount() != null ? payment.getLessonAmount() : (payment.getPaidAmt() != null ? payment.getPaidAmt() - originalLockerFee : 0) )
                    .lockerPaidAmt(payment.getLockerAmount() != null ? payment.getLockerAmount() : originalLockerFee) 
                    .build();

            // UNPAID 상태가 아닌 경우에만 환불 로직 호출
            if (enroll.getPayStatus() != null && !enroll.getPayStatus().equalsIgnoreCase("UNPAID")) {
                refundDetailsDto = enrollmentService.getRefundPreview(enroll.getEnrollId(), enroll.getDaysUsedForRefund());
                if (refundDetailsDto != null && refundDetailsDto.getFinalRefundAmount() != null) {
                    calculatedRefundInt = refundDetailsDto.getFinalRefundAmount().intValue();
                }
            }
        } else {
             // Payment 정보가 없는 UNPAID 건 등의 경우, 기본값 또는 빈 정보로 설정
            paymentDetails = CancelRequestAdminDto.PaymentDetailsForCancel.builder().build(); // 빈 객체 또는 기본값
        }


        return CancelRequestAdminDto.builder()
                .enrollId(enroll.getEnrollId())
                .userName(enroll.getUser() != null ? enroll.getUser().getName() : null)
                .lessonTitle(enroll.getLesson() != null ? enroll.getLesson().getTitle() : null)
                .paymentInfo(paymentDetails) // Null일 수 있음
                .calculatedRefundAmtByNewPolicy(calculatedRefundInt) // 0 또는 계산된 값
                .calculatedRefundDetails(refundDetailsDto) // Null일 수 있음
                .requestedAt(enroll.getCancelRequestedAt() != null ? enroll.getCancelRequestedAt() : enroll.getUpdatedAt())
                .userReason(enroll.getCancelReason())
                .adminComment(enroll.getCancelReason()) 
                .build();
    }

    @Override
    @Transactional(readOnly = true)
    public Page<EnrollAdminResponseDto> getAllEnrollments(Integer year, Integer month, Long lessonId, String userId, String payStatus, Pageable pageable) {
        // getAllEnrollments는 UNPAID도 포함하여 조회하는 것이 일반적이므로 Specification은 그대로 둡니다.
        // DTO 변환 시 Null 처리를 강화합니다.
        Specification<Enroll> spec = EnrollSpecification.filterByAdminCriteria(lessonId, userId, payStatus, null, year, month, false); // excludeUnpaid = false
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
        // 취소 요청 목록에서는 UNPAID 건을 제외합니다.
        Specification<Enroll> spec = EnrollSpecification.filterByAdminCriteria(null, null, null, status, null, null, true); // excludeUnpaid = true
        Page<Enroll> enrollPage = enrollRepository.findAll(spec, pageable);
        return enrollPage.map(this::convertToCancelRequestAdminDto);
    }

    @Override
    @Transactional(readOnly = true)
    public CalculatedRefundDetailsDto getRefundPreview(Long enrollId, Integer manualUsedDays) {
        // UNPAID 상태의 enrollId에 대해서는 preview를 호출하지 않도록 controller/service 상위에서 방어하거나, 
        // 여기서도 enroll 상태를 확인하여 UNPAID면 빈 DTO를 반환하는 것을 고려할 수 있습니다.
        // 현재는 EnrollmentService의 getRefundPreview가 이를 처리한다고 가정합니다.
        return enrollmentService.getRefundPreview(enrollId, manualUsedDays);
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
        enroll.setPayStatus("REFUND_PENDING_ADMIN_CANCEL"); // UNPAID 였던 건도 이 상태로 변경될 수 있음
        enroll.setCancelStatus(Enroll.CancelStatusType.APPROVED);
        enroll.setCancelReason("관리자 직접 취소: " + adminComment);
        enroll.setCancelApprovedAt(LocalDateTime.now());

        if (lockerWasAllocated) {
            // 사용자 정보 및 성별 확인은 User 엔티티를 통해 안전하게 접근해야 함
            User user = enroll.getUser();
            if (user != null && user.getGender() != null && !user.getGender().trim().isEmpty()) {
                lockerService.decrementUsedQuantity(user.getGender().toUpperCase());
                enroll.setLockerAllocated(false);
            } else {
                logger.warn("Cannot decrement locker quantity for enrollId {} due to missing user or gender info.", enrollId);
            }
        }
        enroll.setUsesLocker(false); // 사물함 사용 안함으로 변경
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