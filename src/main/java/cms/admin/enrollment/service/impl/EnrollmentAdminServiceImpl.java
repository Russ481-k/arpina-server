package cms.admin.enrollment.service.impl;

import cms.admin.enrollment.dto.CancelRequestAdminDto;
import cms.admin.enrollment.dto.EnrollAdminResponseDto;
import cms.admin.enrollment.model.dto.TemporaryEnrollmentRequestDto;
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
import cms.user.domain.UserRoleType;
import cms.user.repository.UserRepository;
import cms.swimming.domain.Lesson;
import cms.swimming.repository.LessonRepository;
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
import java.util.UUID;
import java.util.List;

import cms.admin.enrollment.dto.CalculatedRefundDetailsDto;
import cms.admin.enrollment.dto.AdminCancelRequestDto;

@Service
@RequiredArgsConstructor
@Transactional
public class EnrollmentAdminServiceImpl implements EnrollmentAdminService {

    private final EnrollRepository enrollRepository;
    private final PaymentRepository paymentRepository;
    private final EnrollmentService enrollmentService; // For approve/deny AND calculating display refund
    private final LockerService lockerService;
    private final UserRepository userRepository;
    private final LessonRepository lessonRepository;

    @Value("${app.default-locker-fee:5000}")
    private int defaultLockerFee;

    private static final Logger logger = LoggerFactory.getLogger(EnrollmentAdminServiceImpl.class);

    private EnrollAdminResponseDto convertToEnrollAdminResponseDto(Enroll enroll) {
        logger.info("Attempting to convert Enroll to EnrollAdminResponseDto for enrollId: {}",
                enroll != null ? enroll.getEnrollId() : "null_enroll_object");
        if (enroll == null)
            return null;

        List<Payment> payments = paymentRepository.findByEnroll_EnrollIdOrderByCreatedAtDesc(enroll.getEnrollId());
        Payment latestPayment = payments.isEmpty() ? null : payments.get(0);

        EnrollAdminResponseDto.PaymentInfoForEnrollAdmin paymentInfo = null;
        if (latestPayment != null) {
            boolean isFullRefund = latestPayment.getPaidAmt() != null &&
                    latestPayment.getPaidAmt() > 0 &&
                    latestPayment.getPaidAmt().equals(latestPayment.getRefundedAmt());

            paymentInfo = EnrollAdminResponseDto.PaymentInfoForEnrollAdmin.builder()
                    .tid(latestPayment.getTid())
                    .paidAmt(latestPayment.getPaidAmt())
                    .refundedAmt(latestPayment.getRefundedAmt())
                    .payMethod(latestPayment.getPayMethod())
                    .paidAt(latestPayment.getPaidAt())
                    .isFullRefund(isFullRefund)
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
                .membershipType(enroll.getMembershipType() != null ? enroll.getMembershipType().getValue() : null)
                .cancelStatus(enroll.getCancelStatus() != null ? enroll.getCancelStatus().name() : null)
                .cancelReason(enroll.getCancelReason())
                .build();
    }

    private CancelRequestAdminDto convertToCancelRequestAdminDto(Enroll enroll) {
        logger.info("Attempting to convert Enroll to CancelRequestAdminDto for enrollId: {}",
                enroll != null ? enroll.getEnrollId() : "null_enroll_object");
        if (enroll == null)
            return null;

        List<Payment> payments = paymentRepository.findByEnroll_EnrollIdOrderByCreatedAtDesc(enroll.getEnrollId());
        Payment payment = payments.isEmpty() ? null : payments.get(0);

        CancelRequestAdminDto.PaymentDetailsForCancel paymentDetails = null;
        CalculatedRefundDetailsDto refundDetailsDto = null;
        int calculatedRefundInt = 0;

        if (payment != null) { // Payment 정보가 있는 경우에만 처리
            int originalLessonFee = (enroll.getLesson() != null && enroll.getLesson().getPrice() != null)
                    ? enroll.getLesson().getPrice()
                    : 0;
            int originalLockerFee = 0;
            if (enroll.isUsesLocker() || enroll.isLockerAllocated()) {
                if (payment.getPaidAmt() != null && payment.getPaidAmt() > originalLessonFee) {
                    originalLockerFee = payment.getLockerAmount() != null ? payment.getLockerAmount()
                            : defaultLockerFee;
                    if (payment.getPaidAmt() < originalLessonFee + originalLockerFee) {
                        originalLockerFee = payment.getPaidAmt() - originalLessonFee > 0
                                ? payment.getPaidAmt() - originalLessonFee
                                : 0;
                    }
                } else {
                    originalLockerFee = 0;
                }
            }

            paymentDetails = CancelRequestAdminDto.PaymentDetailsForCancel.builder()
                    .tid(payment.getTid())
                    .paidAmt(payment.getPaidAmt())
                    .lessonPaidAmt(payment.getLessonAmount() != null ? payment.getLessonAmount()
                            : (payment.getPaidAmt() != null ? payment.getPaidAmt() - originalLockerFee : 0))
                    .lockerPaidAmt(payment.getLockerAmount() != null ? payment.getLockerAmount() : originalLockerFee)
                    .build();

            // 1. 이미 환불이 완료된 경우, 기록된 환불액을 사용
            if (enroll.getPayStatus() != null && "REFUNDED".equalsIgnoreCase(enroll.getPayStatus())) {
                calculatedRefundInt = payment.getRefundedAmt() != null ? payment.getRefundedAmt() : 0;
                refundDetailsDto = null; // 이미 환불되었으므로 미리보기 DTO는 null
            }
            // 2. 그 외 결제된 건에 대해서는 환불액 미리보기 시도
            else if (enroll.getPayStatus() != null && !"UNPAID".equalsIgnoreCase(enroll.getPayStatus())) {
                try {
                    // getRefundPreview는 REFUNDED, FAILED, CANCELED 등의 상태에 대해 예외를 던질 수 있음
                    refundDetailsDto = enrollmentService.getRefundPreview(enroll.getEnrollId(),
                            enroll.getDaysUsedForRefund());
                    if (refundDetailsDto != null && refundDetailsDto.getFinalRefundAmount() != null) {
                        calculatedRefundInt = refundDetailsDto.getFinalRefundAmount().intValue();
                    }
                } catch (BusinessRuleException e) {
                    // 환불 계산이 불가능한 상태(예: FAILED)의 경우, 미리보기 금액을 0으로 설정하고 로그만 남김
                    logger.warn("환불액 미리보기 계산 중 예외 발생 (enrollId: {}): {}", enroll.getEnrollId(), e.getMessage());
                    calculatedRefundInt = 0;
                    refundDetailsDto = null;
                }
            }
        } else {
            // Payment 정보가 없는 UNPAID 건 등의 경우, 기본값 또는 빈 정보로 설정
            paymentDetails = CancelRequestAdminDto.PaymentDetailsForCancel.builder().build(); // 빈 객체 또는 기본값
        }

        return CancelRequestAdminDto.builder()
                .enrollId(enroll.getEnrollId())
                .userName(enroll.getUser() != null ? enroll.getUser().getName() : null)
                .userLoginId(enroll.getUser() != null ? enroll.getUser().getUsername() : null)
                .userPhone(enroll.getUser() != null ? enroll.getUser().getPhone() : null)
                .lessonTitle(enroll.getLesson() != null ? enroll.getLesson().getTitle() : null)
                .lessonId(enroll.getLesson() != null ? enroll.getLesson().getLessonId() : null)
                .paymentInfo(paymentDetails) // Null일 수 있음
                .paymentStatus(enroll.getPayStatus())
                .cancellationProcessingStatus(enroll.getCancelStatus() != null ? enroll.getCancelStatus().name() : null)
                .calculatedRefundAmtByNewPolicy(calculatedRefundInt) // 0 또는 계산된 값
                .calculatedRefundDetails(refundDetailsDto) // Null일 수 있음
                .requestedAt(
                        enroll.getCancelRequestedAt() != null ? enroll.getCancelRequestedAt() : enroll.getUpdatedAt())
                .userReason(enroll.getCancelReason())
                .build();
    }

    @Override
    @Transactional(readOnly = true)
    public Page<EnrollAdminResponseDto> getAllEnrollments(Integer year, Integer month, Long lessonId, String userId,
            String payStatus, Pageable pageable) {
        Specification<Enroll> spec = EnrollSpecification.filterByAdminCriteria(lessonId, userId, payStatus, null, year,
                month, false);
        Page<Enroll> enrollPage = enrollRepository.findAll(spec, pageable);
        return enrollPage.map(this::convertToEnrollAdminResponseDto);
    }

    @Override
    @Transactional(readOnly = true)
    public EnrollAdminResponseDto getEnrollmentById(Long enrollId) {
        Enroll enroll = enrollRepository.findById(enrollId)
                .orElseThrow(() -> new ResourceNotFoundException("Enrollment not found with id: " + enrollId,
                        ErrorCode.ENROLLMENT_NOT_FOUND));
        return convertToEnrollAdminResponseDto(enroll);
    }

    @Override
    @Transactional(readOnly = true)
    public Page<CancelRequestAdminDto> getCancelRequests(Long lessonId,
            List<cms.enroll.domain.Enroll.CancelStatusType> cancelStatuses, List<String> targetPayStatuses,
            boolean useCombinedLogic, Pageable pageable) {
        // For cancel/refund management, we generally do NOT want to exclude unpaid
        // items if they are otherwise relevant (e.g., CANCELED status).
        // So, setting excludeUnpaid to false here.
        Specification<Enroll> spec = EnrollSpecification.filterForCancelAndRefundManagement(lessonId, cancelStatuses,
                targetPayStatuses, useCombinedLogic, false);
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
    @Transactional
    public void approveCancellation(Long enrollId, AdminCancelRequestDto cancelRequestDto) {
        // Delegate the call to the core EnrollmentService
        enrollmentService.approveEnrollmentCancellationAdmin(enrollId, cancelRequestDto);
    }

    @Override
    public EnrollAdminResponseDto denyCancellation(Long enrollId, String adminComment) {
        enrollmentService.denyEnrollmentCancellationAdmin(enrollId, adminComment);
        Enroll updatedEnroll = enrollRepository.findById(enrollId)
                .orElseThrow(() -> new ResourceNotFoundException("Enrollment not found after denial: " + enrollId,
                        ErrorCode.ENROLLMENT_NOT_FOUND));
        return convertToEnrollAdminResponseDto(updatedEnroll);
    }

    @Override
    @Transactional
    public EnrollAdminResponseDto adminCancelEnrollment(Long enrollId, String adminComment) {
        Enroll enroll = enrollRepository.findById(enrollId)
                .orElseThrow(() -> new ResourceNotFoundException("신청 정보를 찾을 수 없습니다.", ErrorCode.ENROLLMENT_NOT_FOUND));

        String originalPayStatus = enroll.getPayStatus();
        logger.info("관리자 직접 취소 시작. Enroll ID: {}, Original Pay Status: {}", enrollId, originalPayStatus);

        // 이미 최종적으로 취소/환불된 건은 더 이상 처리하지 않음.
        if ("CANCELED".equals(enroll.getStatus())
                && ("REFUNDED".equals(originalPayStatus) || "CANCELED_UNPAID".equals(originalPayStatus))) {
            logger.warn("이미 최종 처리된 신청입니다 (ID: {}). 상태를 변경하지 않습니다.", enrollId);
            throw new BusinessRuleException(ErrorCode.ALREADY_CANCELLED_ENROLLMENT, "이미 취소 또는 환불이 완료된 신청입니다.");
        }

        // [공통] 취소 상태 및 사유 설정
        enroll.setCancelStatus(Enroll.CancelStatusType.ADMIN_CANCELED);
        enroll.setCancelReason(adminComment);
        enroll.setCancelApprovedAt(LocalDateTime.now()); // 관리자가 즉시 취소 승인한 것으로 간주
        enroll.setUpdatedBy("ADMIN");

        // [핵심] '취소/환불 관리' 탭으로 보내기 위한 상태 설정
        enroll.setStatus("CANCELED");

        // 결제된 건(부분 환불 포함)은 '환불 대기' 상태로 변경하여 환불 관리 탭으로 보냄
        if ("PAID".equalsIgnoreCase(originalPayStatus) || "PARTIALLY_REFUNDED".equalsIgnoreCase(originalPayStatus)) {
            enroll.setPayStatus("REFUND_PENDING_ADMIN_CANCEL");
            logger.info("결제 완료 건 (ID: {}) -> 환불 대기 상태로 변경합니다.", enrollId);

        } else if ("UNPAID".equalsIgnoreCase(originalPayStatus)) {
            // 미결제 건은 즉시 취소 완료.
            enroll.setPayStatus("CANCELED_UNPAID");
            logger.info("미결제 건 (ID: {}) -> 즉시 취소 완료 상태로 변경합니다.", enrollId);
            // 미결제 건에 할당된 사물함이 있다면 반납
            if (enroll.isLockerAllocated()) {
                User user = enroll.getUser();
                if (user != null && user.getGender() != null && !user.getGender().isEmpty()) {
                    String lockerGender = "0".equals(user.getGender()) ? "FEMALE"
                            : ("1".equals(user.getGender()) ? "MALE" : null);
                    if (lockerGender != null) {
                        try {
                            lockerService.decrementUsedQuantity(lockerGender);
                            enroll.setLockerAllocated(false);
                            enroll.setUsesLocker(false);
                            logger.info("미결제 건(ID:{}) 관리자 취소에 따라 {} 사물함 반납 처리.", enrollId, lockerGender);
                        } catch (Exception e) {
                            logger.error("미결제 건(ID:{}) 관리자 취소 중 사물함 반납 실패: {}", enrollId, e.getMessage(), e);
                        }
                    }
                }
            }

        } else {
            // 사용자가 취소 요청한 상태(REFUND_REQUESTED) 등 다른 모든 상태도 강제로 관리자 취소 상태로 덮어씀
            enroll.setPayStatus("REFUND_PENDING_ADMIN_CANCEL");
            logger.warn("'{}` 상태의 건 (ID: {}) -> 관리자가 강제로 환불 대기 상태로 변경합니다.", originalPayStatus, enrollId);
        }

        Enroll updatedEnroll = enrollRepository.save(enroll);
        logger.info("관리자 직접 취소 완료. 최종 상태: status={}, payStatus={}", updatedEnroll.getStatus(),
                updatedEnroll.getPayStatus());

        return convertToEnrollAdminResponseDto(updatedEnroll);
    }

    @Override
    @Transactional
    public EnrollAdminResponseDto updateEnrollmentDiscountStatus(Long enrollId,
            DiscountStatusUpdateRequestDto request) {
        Enroll enroll = enrollRepository.findById(enrollId)
                .orElseThrow(() -> new ResourceNotFoundException("신청 정보를 찾을 수 없습니다.", ErrorCode.ENROLLMENT_NOT_FOUND));

        enroll.setDiscountType(request.getDiscountType());
        try {
            enroll.setDiscountStatus(Enroll.DiscountStatusType.valueOf(request.getDiscountStatus().toUpperCase()));
        } catch (IllegalArgumentException e) {
            throw new BusinessRuleException(ErrorCode.INVALID_INPUT_VALUE,
                    "유효하지 않은 할인 상태값입니다: " + request.getDiscountStatus());
        }

        if (enroll.getDiscountStatus() == DiscountStatusType.APPROVED
                || enroll.getDiscountStatus() == DiscountStatusType.DENIED) {
            enroll.setDiscountApprovedAt(LocalDateTime.now());
        }
        enroll.setDiscountAdminComment(request.getAdminComment());

        Enroll savedEnroll = enrollRepository.save(enroll);

        return convertToEnrollAdminResponseDto(savedEnroll);
    }

    @Override
    @Transactional
    public EnrollAdminResponseDto createTemporaryEnrollment(TemporaryEnrollmentRequestDto requestDto) {
        Lesson lesson = lessonRepository.findById(requestDto.getLessonId())
                .orElseThrow(() -> new ResourceNotFoundException("강습 정보를 찾을 수 없습니다.", ErrorCode.LESSON_NOT_FOUND));

        User user;
        if (requestDto.getUserPhone() != null && !requestDto.getUserPhone().trim().isEmpty()) {
            user = userRepository.findByPhone(requestDto.getUserPhone()).orElse(null);
        } else {
            // To prevent multiple enrollments for users with no phone, we could disallow
            // this
            // or generate a truly unique anonymous user each time.
            // For now, let's assume phone is highly recommended or a different unique
            // identifier is used if phone is absent.
            // If phone is not provided, we cannot reliably find an existing user.
            user = null;
        }

        if (user == null) {
            // Create a new temporary user
            String tempUsername = "temp_" + UUID.randomUUID().toString().substring(0, 8);
            user = User.builder()
                    .uuid(UUID.randomUUID().toString())
                    .username(tempUsername)
                    .name(requestDto.getUserName())
                    .phone(requestDto.getUserPhone())
                    .email(tempUsername + "@temporary.com") // Placeholder email
                    .password("tempPassword") // Placeholder, should not be used for login
                    .role(UserRoleType.USER) // Default role
                    .status("TEMP_USER_PROFILE") // Specific status for temporary users
                    .gender(requestDto.getUserGender() != null ? requestDto.getUserGender().toUpperCase() : null)
                    .isTemporary(true)
                    .createdAt(LocalDateTime.now())
                    .updatedAt(LocalDateTime.now())
                    .build();
            user = userRepository.save(user);
        } else {
            // Update existing user's gender if provided and different, or if not set
            if (requestDto.getUserGender() != null &&
                    (user.getGender() == null || !user.getGender().equalsIgnoreCase(requestDto.getUserGender()))) {
                user.setGender(requestDto.getUserGender().toUpperCase());
                user = userRepository.save(user);
            }
        }

        // Check for existing active enrollment for this user and lesson
        enrollRepository
                .findByUserAndLessonAndPayStatusNotIn(user, lesson,
                        java.util.Arrays.asList("PAYMENT_TIMEOUT", "CANCELED_UNPAID", "CANCELED_PAID"))
                .ifPresent(e -> {
                    throw new BusinessRuleException(ErrorCode.DUPLICATE_ENROLLMENT, "이미 해당 강습에 신청 내역이 존재합니다.");
                });

        Enroll enroll = Enroll.builder()
                .user(user)
                .lesson(lesson)
                .status("APPLIED") // Standard status, payStatus will differentiate
                .payStatus("PAID_OFFLINE")
                .expireDt(LocalDateTime.now().plusYears(1)) // Effectively no expiry for admin-created paid offline
                .renewalFlag(false)
                .usesLocker(requestDto.getUsesLocker() != null && requestDto.getUsesLocker())
                .lockerAllocated(false) // Will be set if locker is successfully allocated
                .createdAt(LocalDateTime.now())
                .updatedAt(LocalDateTime.now())
                // Using cancelReason for admin memo as per frontend suggestion, can be a
                // dedicated field later
                .cancelReason(requestDto.getMemo() != null ? "임시등록 메모: " + requestDto.getMemo() : null)
                .build();

        if (enroll.isUsesLocker()) {
            if (user.getGender() == null || user.getGender().trim().isEmpty()) {
                throw new BusinessRuleException(ErrorCode.LOCKER_GENDER_REQUIRED,
                        "사물함 사용 시 사용자의 성별 정보가 필요합니다. 사용자 정보에 성별을 먼저 등록하거나, 임시 등록 시 성별을 지정해주세요.");
            }

            String lockerGender;
            if ("0".equals(user.getGender())) {
                lockerGender = "FEMALE";
            } else if ("1".equals(user.getGender())) {
                lockerGender = "MALE";
            } else {
                logger.warn(
                        "Unknown gender code '{}' for user {} during temporary enrollment. Cannot determine locker gender.",
                        user.getGender(), user.getUuid());
                throw new BusinessRuleException(ErrorCode.INVALID_USER_GENDER,
                        "사용자의 성별 코드가 유효하지 않습니다: " + user.getGender());
            }

            try {
                logger.info(
                        "Attempting to increment locker count for temp enrollment. User-gender: {}, mapped-locker-gender: {} (User: {})",
                        user.getGender(), lockerGender, user.getUuid());
                lockerService.incrementUsedQuantity(lockerGender);
                enroll.setLockerAllocated(true);
                logger.info("Locker count incremented successfully for temp enrollment. Mapped-locker-gender: {}",
                        lockerGender);
            } catch (BusinessRuleException e) {
                logger.warn("임시 등록 중 사물함 할당 실패 (사용자: {}, user-gender: {}, mapped-locker-gender: {}): {}",
                        user.getUuid(), user.getGender(), lockerGender, e.getMessage());
                enroll.setUsesLocker(false);
                enroll.setLockerAllocated(false);
            } catch (Exception e) { // Catch broader exceptions during locker allocation
                logger.error("임시 등록 중 사물함 할당 시 예외 발생 (사용자: {}, user-gender: {}, mapped-locker-gender: {}): {}",
                        user.getUuid(), user.getGender(), lockerGender, e.getMessage(), e);
                enroll.setUsesLocker(false);
                enroll.setLockerAllocated(false);
            }
        }

        Enroll savedEnroll = enrollRepository.save(enroll);
        return convertToEnrollAdminResponseDto(savedEnroll);
    }
}