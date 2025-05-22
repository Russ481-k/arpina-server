package cms.enroll.service.impl;

import cms.enroll.domain.Enroll;
import cms.enroll.domain.Enroll.CancelStatusType;
import cms.enroll.repository.EnrollRepository;
import cms.enroll.service.EnrollmentService;

// Domain entities
import cms.swimming.domain.Lesson;
import cms.swimming.domain.Locker;
import cms.user.domain.User;

// Repositories
import cms.swimming.repository.LessonRepository;
import cms.user.repository.UserRepository;
import cms.payment.repository.PaymentRepository;

// Services
import cms.swimming.service.LessonService;
import cms.locker.service.LockerService;

// DTOs - directly import from specified packages
import cms.mypage.dto.CheckoutDto;
// cms.mypage.dto.EnrollDto is used for Mypage responses
import cms.mypage.dto.EnrollDto; 
import cms.mypage.dto.RenewalRequestDto;
// cms.swimming.dto.EnrollRequestDto and EnrollResponseDto are used for initial enrollment
import cms.swimming.dto.EnrollRequestDto;
import cms.swimming.dto.EnrollResponseDto;

import cms.payment.domain.Payment;

import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import javax.persistence.EntityNotFoundException;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneOffset; // Import for ZoneOffset
import java.util.Collections;
import java.util.Comparator;
import java.util.List;
import java.util.NoSuchElementException;
import java.util.Optional;
import java.util.stream.Collectors;

import cms.common.exception.BusinessRuleException;
import cms.common.exception.ErrorCode;
import cms.common.exception.ResourceNotFoundException;
import org.springframework.http.HttpStatus; // HttpStatus 추가

@Service("enrollmentServiceImpl")
@Transactional
public class EnrollmentServiceImpl implements EnrollmentService {

    private final EnrollRepository enrollRepository;
    private final PaymentRepository paymentRepository;
    private final LessonService lessonService;
    private final LockerService lockerService;
    private final UserRepository userRepository;
    private final LessonRepository lessonRepository;

    public EnrollmentServiceImpl(EnrollRepository enrollRepository,
                                 PaymentRepository paymentRepository,
                                 @Qualifier("swimmingLessonServiceImpl") LessonService lessonService,
                                 @Qualifier("lockerServiceImpl") LockerService lockerService,
                                 UserRepository userRepository,
                                 LessonRepository lessonRepository) {
        this.enrollRepository = enrollRepository;
        this.paymentRepository = paymentRepository;
        this.lessonService = lessonService;
        this.lockerService = lockerService;
        this.userRepository = userRepository;
        this.lessonRepository = lessonRepository;
    }

    @Override
    @Transactional(readOnly = true)
    public Page<EnrollDto> getEnrollments(User user, String payStatusFilter, Pageable pageable) {
        // TODO: 이 부분도 User가 null일 경우를 대비하거나, Controller에서 @AuthenticationPrincipal User user가 null이 아님을 보장해야 함.
        // 현재는 user 객체가 null이 아니라고 가정하고 진행.
        List<Enroll> enrolls;
        if (StringUtils.hasText(payStatusFilter)) {
            enrolls = enrollRepository.findByUserUuid(user.getUuid()).stream()
                        .filter(e -> payStatusFilter.equalsIgnoreCase(e.getPayStatus()))
                        .sorted(Comparator.comparing(Enroll::getCreatedAt).reversed())
                        .collect(Collectors.toList());
        } else {
            enrolls = enrollRepository.findByUserUuid(user.getUuid());
            // enrolls.sort(Comparator.comparing(Enroll::getCreatedAt).reversed()); // findByUserUuid가 정렬을 보장하지 않으면 필요
        }

        if (user == null || user.getUuid() == null) { // 방어 코드 추가
            // 이 경우는 사실상 Controller 단에서 @AuthenticationPrincipal에 의해 걸러지거나, 
            // Spring Security 설정 오류로 인해 발생할 수 있습니다.
            // USER_NOT_FOUND 또는 AUTHENTICATION_FAILED가 적절할 수 있습니다.
            throw new BusinessRuleException(ErrorCode.AUTHENTICATION_FAILED, HttpStatus.UNAUTHORIZED);
        }

        // enrolls 리스트가 비어있을 경우 ResourceNotFoundException을 던질지, 아니면 빈 페이지를 반환할지는 정책에 따라 다름.
        // 현재 코드는 빈 페이지를 반환하므로 그대로 둡니다.
        int start = (int) pageable.getOffset();
        int end = Math.min((start + pageable.getPageSize()), enrolls.size());
        List<EnrollDto> dtoList = enrolls.isEmpty() ? Collections.emptyList() :
                                  enrolls.subList(start, end).stream()
                                         .map(this::convertToMypageEnrollDto)
                                         .collect(Collectors.toList());
        return new PageImpl<>(dtoList, pageable, enrolls.size());
    }

    @Override
    @Transactional(readOnly = true)
    public EnrollDto getEnrollmentDetails(User user, Long enrollId) {
        if (user == null || user.getUuid() == null) { // 방어 코드
             throw new BusinessRuleException(ErrorCode.AUTHENTICATION_FAILED, HttpStatus.UNAUTHORIZED);
        }
        Enroll enroll = enrollRepository.findById(enrollId)
                .orElseThrow(() -> new ResourceNotFoundException("수강 신청 정보를 찾을 수 없습니다 (ID: " + enrollId + ")", ErrorCode.ENROLLMENT_NOT_FOUND));
        
        if (!enroll.getUser().getUuid().equals(user.getUuid())) {
            throw new BusinessRuleException(ErrorCode.ACCESS_DENIED, HttpStatus.FORBIDDEN);
        }
        return convertToMypageEnrollDto(enroll);
    }

    @Override
    @Transactional
    public EnrollResponseDto createInitialEnrollment(User user, EnrollRequestDto initialEnrollRequest, String ipAddress) {
        Lesson lesson = lessonRepository.findById(initialEnrollRequest.getLessonId())
                .orElseThrow(() -> new EntityNotFoundException("강습을 찾을 수 없습니다. ID: " + initialEnrollRequest.getLessonId()));

        if (lesson.getStatus() != Lesson.LessonStatus.OPEN) {
            throw new BusinessRuleException(ErrorCode.LESSON_NOT_OPEN_FOR_ENROLLMENT, "신청 가능한 강습이 아닙니다. 현재 상태: " + lesson.getStatus());
        }

        long activeEnrollments = enrollRepository.countActiveEnrollmentsForLesson(lesson.getLessonId(), LocalDateTime.now());
        if (activeEnrollments >= lesson.getCapacity()) {
            throw new BusinessRuleException(ErrorCode.LESSON_CAPACITY_EXCEEDED, "강습 정원이 초과되었습니다. 현재 신청 인원: " + activeEnrollments + "/" + lesson.getCapacity());
        }

        Optional<Enroll> existingEnrollOpt = enrollRepository.findByUserUuidAndLessonLessonIdAndStatus(
                user.getUuid(), initialEnrollRequest.getLessonId(), "APPLIED");
        if (existingEnrollOpt.isPresent()) {
            Enroll exEnroll = existingEnrollOpt.get();
            if ("PAID".equals(exEnroll.getPayStatus())) {
                throw new BusinessRuleException(ErrorCode.DUPLICATE_ENROLLMENT_ATTEMPT, "이미 해당 강습에 대해 결제 완료된 신청 내역이 존재합니다.");
            }
            if ("UNPAID".equals(exEnroll.getPayStatus()) && exEnroll.getExpireDt().isAfter(LocalDateTime.now())) {
                 throw new BusinessRuleException(ErrorCode.DUPLICATE_ENROLLMENT_ATTEMPT, "이미 신청한 강습의 결제가능 시간이 남아있습니다. 마이페이지에서 결제를 진행해주세요. 만료시간: " + exEnroll.getExpireDt());
            }
        }

        long monthlyEnrollments = enrollRepository.countUserEnrollmentsInMonth(user.getUuid(), lesson.getStartDate());
        if (monthlyEnrollments > 0) {
            throw new BusinessRuleException(ErrorCode.MONTHLY_ENROLLMENT_LIMIT_EXCEEDED, "같은 달에 이미 다른 강습을 신청하셨습니다. 한 달에 한 개의 강습만 신청 가능합니다.");
        }

        Enroll enroll = Enroll.builder()
                .user(user)
                .lesson(lesson)
                .usesLocker(false)
                .status("APPLIED")
                .payStatus("UNPAID")
                .expireDt(LocalDateTime.now().plusHours(1))
                .renewalFlag(false)
                .createdBy(user.getName())
                .createdIp(ipAddress)
                .updatedBy(user.getName())
                .updatedIp(ipAddress)
                .build();
        Enroll savedEnroll = enrollRepository.save(enroll);

        // Update lesson status if capacity is met (this logic might remain similar)
        long potentiallyPaidEnrollments = enrollRepository.countByLessonLessonIdAndPayStatus(lesson.getLessonId(), "PAID") +
                                          enrollRepository.countByLessonLessonIdAndStatusAndPayStatus(lesson.getLessonId(), "APPLIED", "UNPAID");
        if (potentiallyPaidEnrollments >= lesson.getCapacity() && lesson.getStatus() == Lesson.LessonStatus.OPEN) {
            lesson.updateStatus(Lesson.LessonStatus.CLOSED);
            lessonRepository.save(lesson);
        }

        // Modify EnrollResponseDto to reflect changes
        return EnrollResponseDto.builder()
            .enrollId(savedEnroll.getEnrollId())
            .userId(user.getUuid())
            .userName(user.getName())
            .status(savedEnroll.getStatus())
            .payStatus(savedEnroll.getPayStatus())
            .createdAt(savedEnroll.getCreatedAt())
            .expireDt(savedEnroll.getExpireDt())
            .lessonId(lesson.getLessonId())
            .lessonTitle(lesson.getTitle())
            .lessonPrice(lesson.getPrice())
            .usesLocker(savedEnroll.isUsesLocker())
            .renewalFlag(savedEnroll.isRenewalFlag())
            .cancelStatus(savedEnroll.getCancelStatus() != null ? savedEnroll.getCancelStatus().name() : null)
            .cancelReason(savedEnroll.getCancelReason())
            .build();
    }

    @Override
    @Transactional // Ensure transactional behavior for updates
    public CheckoutDto processCheckout(User user, Long enrollId, cms.mypage.dto.CheckoutRequestDto checkoutRequest) {
        if (user == null || user.getUuid() == null) {
             throw new BusinessRuleException(ErrorCode.AUTHENTICATION_FAILED, HttpStatus.UNAUTHORIZED);
        }
        Enroll enroll = enrollRepository.findById(enrollId)
                .orElseThrow(() -> new ResourceNotFoundException("수강 신청 정보를 찾을 수 없습니다 (ID: " + enrollId + ")", ErrorCode.ENROLLMENT_NOT_FOUND));
        
        if (!enroll.getUser().getUuid().equals(user.getUuid())) {
            throw new BusinessRuleException(ErrorCode.ACCESS_DENIED, HttpStatus.FORBIDDEN);
        }
        if (!"UNPAID".equalsIgnoreCase(enroll.getPayStatus())) {
            throw new BusinessRuleException("결제 대기 상태의 수강 신청이 아닙니다. 현재 상태: " + enroll.getPayStatus(), ErrorCode.NOT_UNPAID_ENROLLMENT_STATUS);
        }
        if (enroll.getExpireDt().isBefore(LocalDateTime.now())) {
            enroll.setStatus("EXPIRED");
            enroll.setPayStatus("EXPIRED");
            enrollRepository.save(enroll);
            throw new BusinessRuleException("결제 가능 시간이 만료되었습니다 (ID: " + enrollId + ")", ErrorCode.ENROLLMENT_PAYMENT_EXPIRED);
        }
        
        Lesson lesson = enroll.getLesson();
        if (lesson == null) {
            throw new ResourceNotFoundException("연결된 강좌 정보를 찾을 수 없습니다 (수강신청 ID: " + enrollId + ")", ErrorCode.LESSON_NOT_FOUND);
        }

        // Locker logic starts here -- 이 부분의 레거시 사물함 로직 제거
        // if (Boolean.TRUE.equals(checkoutRequest.getWantsLocker())) { ... } 부분 전체 삭제
        // 최종 사물함 선택은 /api/v1/payment/confirm 에서 처리.

        // BigDecimal finalAmount = BigDecimal.valueOf(lesson.getPrice()); // 기본 강습료
        // // 사물함 요금 로직이 필요하다면 여기서 CheckoutDto에 반영할 수 있으나, 현재 스키마는 그렇지 않음.
        // // if (Boolean.TRUE.equals(checkoutRequest.getWantsLocker()) && 사물함요금 > 0) {
        // //    finalAmount = finalAmount.add(BigDecimal.valueOf(사물함요금));
        // // }

        CheckoutDto checkoutDto = new CheckoutDto();
        // merchantUid는 실제 PG 연동 시 더 견고한 방식으로 생성해야 함.
        checkoutDto.setMerchantUid("enroll_" + enroll.getEnrollId() + "_" + System.currentTimeMillis()); 
        checkoutDto.setAmount(BigDecimal.valueOf(lesson.getPrice())); // 사물함 요금은 PG 결제 페이지에서 최종 결정 후 confirm에서 처리
        checkoutDto.setLessonTitle(lesson.getTitle());
        checkoutDto.setUserName(user.getName());
        checkoutDto.setPgProvider("html5_inicis"); // PG 제공자는 설정에서 가져오도록 변경하는 것이 좋음
        return checkoutDto;
    }

    @Override
    @Transactional
    public void processPayment(User user, Long enrollId, String pgToken) {
        Enroll enroll = enrollRepository.findById(enrollId)
                .orElseThrow(() -> new ResourceNotFoundException("수강 신청 정보를 찾을 수 없습니다 (ID: " + enrollId + ")", ErrorCode.ENROLLMENT_NOT_FOUND));

        if (user == null || !enroll.getUser().getUuid().equals(user.getUuid())) {
            throw new BusinessRuleException(ErrorCode.ACCESS_DENIED);
        }
        // Webhook을 통해 주된 상태 변경이 이루어지므로, 이 메소드는 동기적 확인 또는 보조 역할.
        // assignLocker 등의 사물함 상태 변경은 PaymentServiceImpl.confirmPayment에서 담당.
        // 따라서 이 메소드 내에서 lockerService.assignLocker 호출 로직은 완전히 제거.
        // 이전 grep_search 결과에서 라인 358 if (!lockerService.assignLocker(user.getGender())) { ... } 부분에 해당.
        // 이 블록 전체를 삭제합니다.

        // PG 검증 및 Payment 엔티티 생성/저장 로직은 필요 시 여기에 구현.
    }

    @Override
    @Transactional
    public void requestEnrollmentCancellation(User user, Long enrollId, String reason) {
        Enroll enroll = enrollRepository.findById(enrollId)
            .orElseThrow(() -> new ResourceNotFoundException("Enrollment not found with ID: " + enrollId, ErrorCode.ENROLLMENT_NOT_FOUND));

        if (!enroll.getUser().getUuid().equals(user.getUuid())) {
            throw new BusinessRuleException(ErrorCode.ACCESS_DENIED, "You do not have permission to cancel this enrollment.");
        }
        if (enroll.getCancelStatus() != null && enroll.getCancelStatus() != Enroll.CancelStatusType.NONE) {
             throw new BusinessRuleException(ErrorCode.ALREADY_CANCELLED_ENROLLMENT, "This enrollment is already in a cancellation process or has been cancelled/denied.");
        }

        boolean lockerWasActuallyAllocated = enroll.isLockerAllocated();
        
        // ... (PG 환불 로직 연동 TODO) ...

        enroll.setStatus("CANCELED"); 
        enroll.setPayStatus("REFUND_REQUESTED"); 
        enroll.setCancelStatus(Enroll.CancelStatusType.REQ); 
        enroll.setCancelReason(reason);
        enroll.setUsesLocker(false); // User no longer intends to use a locker
        enroll.setLockerAllocated(false); // Locker is no longer allocated (or was never)
        enroll.setLockerPgToken(null); // Clear PG token associated with locker

        if (lockerWasActuallyAllocated) {
            String userGender = enroll.getUser().getGender();
            if (userGender != null && !userGender.trim().isEmpty()) {
                lockerService.decrementUsedQuantity(userGender.toUpperCase());
            }
        }
        enrollRepository.save(enroll);
    }

    @Override
    @Transactional
    public EnrollDto processRenewal(User user, RenewalRequestDto renewalRequestDto) {
        if (user == null || user.getUuid() == null) {
             throw new BusinessRuleException(ErrorCode.AUTHENTICATION_FAILED, HttpStatus.UNAUTHORIZED);
        }
        Lesson lesson = lessonRepository.findById(renewalRequestDto.getLessonId())
            .orElseThrow(() -> new ResourceNotFoundException("재수강 대상 강좌를 찾을 수 없습니다 (ID: " + renewalRequestDto.getLessonId() + ")", ErrorCode.LESSON_NOT_FOUND));
        
        // TODO: Add other renewal eligibility checks here

        // boolean useLockerForRenewal = false; // This variable is not strictly needed now
        // The decision to use a locker is passed to the payment confirmation step.
        // The enroll.usesLocker field will reflect the user's *intent* from the renewal request.

        // if (renewalRequestDto.isWantsLocker()) {
        //     if (user.getGender() == null || user.getGender().trim().isEmpty()) {
        //         throw new BusinessRuleException(ErrorCode.USER_GENDER_REQUIRED_FOR_LOCKER);
        //     }
        //     // DO NOT call lockerService.incrementUsedQuantity or assignLocker here.
        //     // This will be handled by PaymentServiceImpl.confirmPayment after successful payment.
        //     // The original code had: if (!lockerService.assignLocker(user.getGender())) { ... }
        //     // This was incorrect for the new flow.
        //     useLockerForRenewal = true; 
        // }

        Enroll.EnrollBuilder newEnrollBuilder = Enroll.builder()
            .user(user)
            .lesson(lesson)
            .status("APPLIED").payStatus("UNPAID").expireDt(LocalDateTime.now().plusHours(24))
            .renewalFlag(true).cancelStatus(CancelStatusType.NONE)
            // Set usesLocker based on user's choice in renewal request.
            // Actual inventory update happens in PaymentServiceImpl.confirmPayment.
            .usesLocker(renewalRequestDto.isWantsLocker()) 
            .createdBy(user.getName()) 
            .updatedBy(user.getName()) 
            .createdIp("UNKNOWN_IP_RENEWAL") 
            .updatedIp("UNKNOWN_IP_RENEWAL");

        Enroll newEnroll = newEnrollBuilder.build();
        try {
            enrollRepository.save(newEnroll);
            // If renewal involved a locker preference, it's now set on 'newEnroll'.
            // If the save fails, and if we *had* provisionally incremented a locker count here,
            // we would decrement it. But since we don't increment here, the rollback is simpler.
            return convertToMypageEnrollDto(newEnroll);
        } catch (Exception e) {
            // logger.error("Error saving renewed enrollment for user {}: {}", user.getUuid(), e.getMessage(), e);
            // No direct locker inventory to roll back here as it wasn't incremented yet.
            // The original code had: lockerService.releaseLocker(user.getGender()); - this should be decrementUsedQuantity
            // However, if useLockerForRenewal was true AND we had called increment, we would call decrement here.
            // Since we are not calling increment anymore, the direct rollback of quantity is not needed here.
            // If renewalRequestDto.isWantsLocker() was true, it just means the *intent* was recorded.
            // If this save fails, the intent isn't persisted, and no inventory was touched.
            throw new BusinessRuleException("재수강 신청 처리 중 데이터 저장에 실패했습니다.", ErrorCode.INTERNAL_SERVER_ERROR, e);
        }
    }

    // Admin methods implementation
    @Override
    @Transactional(readOnly = true)
    public Page<EnrollResponseDto> getAllEnrollmentsAdmin(Pageable pageable) {
        Page<Enroll> enrollPage = enrollRepository.findAll(pageable);
        return enrollPage.map(this::convertToSwimmingEnrollResponseDto);
    }

    @Override
    @Transactional(readOnly = true)
    public Page<EnrollResponseDto> getAllEnrollmentsByStatusAdmin(String status, Pageable pageable) {
        // Assuming status can be either Enroll.status or Enroll.payStatus
        // This might need more sophisticated logic if "status" can refer to different fields
        // For now, we'll assume it refers to Enroll.status primarily, then Enroll.payStatus if not found.
        // Or, better, the controller should specify which field status refers to.
        // For this implementation, let's assume 'status' refers to the main Enroll.status field.
        // If it can be 'PAID' or 'UNPAID', then it must refer to 'payStatus'.
        
        Page<Enroll> enrollPage;
        if ("PAID".equalsIgnoreCase(status) || "UNPAID".equalsIgnoreCase(status) || "EXPIRED".equalsIgnoreCase(status) || "REFUNDED".equalsIgnoreCase(status)) {
            enrollPage = enrollRepository.findByPayStatus(status.toUpperCase(), pageable);
        } else {
            // Assuming status refers to the main status like APPLIED, CANCELED, COMPLETED
            enrollPage = enrollRepository.findByStatus(status.toUpperCase(), pageable);
        }
        return enrollPage.map(this::convertToSwimmingEnrollResponseDto);
    }

    @Override
    @Transactional(readOnly = true)
    public Page<EnrollResponseDto> getAllEnrollmentsByLessonIdAdmin(Long lessonId, Pageable pageable) {
        Lesson lesson = lessonRepository.findById(lessonId)
                .orElseThrow(() -> new EntityNotFoundException("Lesson not found with ID: " + lessonId));
        Page<Enroll> enrollPage = enrollRepository.findByLesson(lesson, pageable);
        return enrollPage.map(this::convertToSwimmingEnrollResponseDto);
    }

    @Override
    @Transactional
    public void approveEnrollmentCancellationAdmin(Long enrollId, Integer refundPct) {
        Enroll enroll = enrollRepository.findById(enrollId)
            .orElseThrow(() -> new ResourceNotFoundException("Enrollment not found with ID: " + enrollId, ErrorCode.ENROLLMENT_NOT_FOUND));
        
        boolean lockerWasActuallyAllocated = enroll.isLockerAllocated();
        // ... (PG 환불 로직 연동 등) ...
        
        enroll.setPayStatus("REFUNDED"); 
        enroll.setCancelStatus(CancelStatusType.APPROVED);
        enroll.setUsesLocker(false); 
        enroll.setLockerAllocated(false);
        enroll.setLockerPgToken(null);
        
        if (lockerWasActuallyAllocated) {
            User lockerUser = enroll.getUser(); 
            if (lockerUser != null && lockerUser.getGender() != null && !lockerUser.getGender().trim().isEmpty()) {
                lockerService.decrementUsedQuantity(lockerUser.getGender().toUpperCase());
            }
        }
        enrollRepository.save(enroll);
    }

    @Override
    @Transactional
    public void denyEnrollmentCancellationAdmin(Long enrollId, String comment) {
        Enroll enroll = enrollRepository.findById(enrollId)
                .orElseThrow(() -> new EntityNotFoundException("Enrollment not found with ID: " + enrollId));

        if (enroll.getCancelStatus() != Enroll.CancelStatusType.REQ) {
            throw new IllegalStateException("Cancellation request not found or already processed for enrollment ID: " + enrollId);
        }

        enroll.setCancelStatus(Enroll.CancelStatusType.DENIED);
        enroll.setCancelReason(comment);
        enroll.setUpdatedBy("ADMIN");
        enroll.setUpdatedAt(LocalDateTime.now());
        enrollRepository.save(enroll);
    }

    private EnrollResponseDto convertToSwimmingEnrollResponseDto(Enroll enroll) {
        Lesson lesson = enroll.getLesson();
        User user = enroll.getUser();

        return EnrollResponseDto.builder()
                .enrollId(enroll.getEnrollId())
                .userId(user != null ? user.getUuid() : null)
                .userName(user != null ? user.getName() : null)
                .status(enroll.getStatus())
                .payStatus(enroll.getPayStatus())
                .createdAt(enroll.getCreatedAt())
                .expireDt(enroll.getExpireDt())
                .lessonId(lesson != null ? lesson.getLessonId() : null)
                .lessonTitle(lesson != null ? lesson.getTitle() : null)
                .lessonPrice(lesson != null ? lesson.getPrice() : null)
                .usesLocker(enroll.isUsesLocker())
                .renewalFlag(enroll.isRenewalFlag())
                .cancelStatus(enroll.getCancelStatus() != null ? enroll.getCancelStatus().name() : null)
                .cancelReason(enroll.getCancelReason())
                .build();
    }

    private EnrollDto convertToMypageEnrollDto(Enroll enroll) {
        Lesson lesson = enroll.getLesson();

        EnrollDto.LessonDetails lessonDetails = null;
        if (lesson != null) {
            lessonDetails = EnrollDto.LessonDetails.builder()
                    .title(lesson.getTitle())
                    .price(BigDecimal.valueOf(lesson.getPrice()))
                    .build();
        }

        EnrollDto.RenewalWindow renewalWindow = null;

        return EnrollDto.builder()
            .enrollId(enroll.getEnrollId())
            .lesson(lessonDetails)
            .status(enroll.getPayStatus())
            .applicationDate(enroll.getCreatedAt() != null ? enroll.getCreatedAt().atOffset(ZoneOffset.UTC) : null)
            .paymentExpireDt(enroll.getExpireDt() != null ? enroll.getExpireDt().atOffset(ZoneOffset.UTC) : null)
            .usesLocker(enroll.isUsesLocker())
            .renewalWindow(renewalWindow)
            .isRenewal(enroll.isRenewalFlag())
            .cancelStatus(enroll.getCancelStatus() != null ? enroll.getCancelStatus().name() : null)
            .cancelReason(enroll.getCancelReason())
            .build();
    }
} 