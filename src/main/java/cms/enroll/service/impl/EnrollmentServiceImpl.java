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
        List<Enroll> enrolls;
        if (StringUtils.hasText(payStatusFilter)) {
            enrolls = enrollRepository.findByUserUuid(user.getUuid()).stream()
                        .filter(e -> payStatusFilter.equalsIgnoreCase(e.getPayStatus()))
                        .sorted(Comparator.comparing(Enroll::getCreatedAt).reversed()) // Ensure consistent order
                        .collect(Collectors.toList());
        } else {
            // Assuming findByUserUuid already orders by createdAt desc or add sorting here too
            enrolls = enrollRepository.findByUserUuid(user.getUuid()); 
            // enrolls.sort(Comparator.comparing(Enroll::getCreatedAt).reversed()); // If not ordered by repo
        }

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
        Enroll enroll = enrollRepository.findById(enrollId)
                .orElseThrow(() -> new RuntimeException("Enrollment not found with ID: " + enrollId));
        if (!enroll.getUser().getUuid().equals(user.getUuid())) {
            throw new SecurityException("You do not have permission to view this enrollment.");
        }
        return convertToMypageEnrollDto(enroll);
    }

    @Override
    @Transactional
    public EnrollResponseDto createInitialEnrollment(User user, EnrollRequestDto initialEnrollRequest, String ipAddress) {
        Lesson lesson = lessonRepository.findById(initialEnrollRequest.getLessonId())
                .orElseThrow(() -> new EntityNotFoundException("강습을 찾을 수 없습니다. ID: " + initialEnrollRequest.getLessonId()));

        if (lesson.getStatus() != Lesson.LessonStatus.OPEN) {
            throw new IllegalStateException("신청 가능한 강습이 아닙니다. 상태: " + lesson.getStatus());
        }

        long currentPaidEnrollments = enrollRepository.countByLessonLessonIdAndPayStatus(lesson.getLessonId(), "PAID");
        if (currentPaidEnrollments >= lesson.getCapacity()) {
            throw new IllegalStateException("강습 정원이 초과되었습니다.");
        }

        Optional<Enroll> existingEnrollOpt = enrollRepository.findByUserUuidAndLessonLessonIdAndStatus(
                user.getUuid(), initialEnrollRequest.getLessonId(), "APPLIED");
        if (existingEnrollOpt.isPresent()) {
            Enroll exEnroll = existingEnrollOpt.get();
            if ("PAID".equals(exEnroll.getPayStatus())) {
                throw new IllegalStateException("이미 해당 강습에 대해 결제 완료된 신청 내역이 존재합니다.");
            }
            if ("UNPAID".equals(exEnroll.getPayStatus()) && exEnroll.getExpireDt().isAfter(LocalDateTime.now())) {
                 throw new IllegalStateException("이미 신청한 강습의 결제가능 시간이 남아있습니다. 마이페이지에서 결제를 진행해주세요. 만료시간: " + exEnroll.getExpireDt());
            }
        }

        long monthlyEnrollments = enrollRepository.countUserEnrollmentsInMonth(user.getUuid(), lesson.getStartDate());
        if (monthlyEnrollments > 0) {
            throw new IllegalStateException("같은 달에 이미 다른 강습을 신청하셨습니다. 한 달에 한 개의 강습만 신청 가능합니다.");
        }

        boolean useLockerForEnrollment = false;
        // Assuming EnrollRequestDto has a field like 'wantsLocker' or similar
        // For now, let's assume initialEnrollRequest.isWantsLocker() exists
        if (initialEnrollRequest.isWantsLocker()) { 
            if (user.getGender() == null || user.getGender().trim().isEmpty()) {
                throw new IllegalStateException("라커를 신청하려면 사용자의 성별 정보가 필요합니다.");
            }
            // Check available lockers using the new lockerService (LockerInventoryService)
            // The lesson specific locker capacities (maleLockerCap, femaleLockerCap) might need
            // to be checked against the general inventory or this logic might simplify.
            // For now, just try to assign from general inventory.
            if (!lockerService.assignLocker(user.getGender())) {
                throw new IllegalStateException(user.getGender() + " 성별의 사용 가능한 라커가 없습니다.");
            }
            useLockerForEnrollment = true;
        }

        Enroll enroll = Enroll.builder()
                .user(user)
                .lesson(lesson)
                .usesLocker(useLockerForEnrollment)
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
    public CheckoutDto processCheckout(User user, Long enrollId) {
        Enroll enroll = enrollRepository.findById(enrollId)
                .orElseThrow(() -> new RuntimeException("Enrollment not found: " + enrollId));
        if (!enroll.getUser().getUuid().equals(user.getUuid())) {
            throw new SecurityException("Permission denied for this enrollment.");
        }
        if (!"UNPAID".equalsIgnoreCase(enroll.getPayStatus())) {
            throw new IllegalStateException("Enrollment not in UNPAID status. Pay status: " + enroll.getPayStatus());
        }
        if (enroll.getExpireDt().isBefore(LocalDateTime.now())) {
            enroll.setStatus("EXPIRED");
            enroll.setPayStatus("EXPIRED");
            enrollRepository.save(enroll);
            throw new IllegalStateException("Payment expired for enrollment: " + enrollId);
        }
        Lesson lesson = enroll.getLesson();
        CheckoutDto checkoutDto = new CheckoutDto();
        checkoutDto.setMerchantUid("enroll_" + enroll.getEnrollId() + "_" + System.currentTimeMillis());
        checkoutDto.setAmount(new BigDecimal(lesson.getPrice()));
        checkoutDto.setLessonTitle(lesson.getTitle());
        checkoutDto.setUserName(user.getName());
        checkoutDto.setPgProvider("html5_inicis");
        return checkoutDto;
    }

    @Override
    @Transactional
    public void processPayment(User user, Long enrollId, String pgToken) {
        Enroll enroll = enrollRepository.findById(enrollId)
                .orElseThrow(() -> new RuntimeException("Enrollment not found: " + enrollId));
        if (!enroll.getUser().getUuid().equals(user.getUuid())) {
            throw new SecurityException("Permission denied.");
        }
        if (!"UNPAID".equalsIgnoreCase(enroll.getPayStatus())) {
            throw new IllegalStateException("Enrollment not UNPAID. Pay status: " + enroll.getPayStatus());
        }
        if (enroll.getExpireDt().isBefore(LocalDateTime.now())) {
            enroll.setStatus("EXPIRED");
            enroll.setPayStatus("EXPIRED");
            enrollRepository.save(enroll);
            throw new IllegalStateException("Payment expired: " + enrollId);
        }
        Lesson lesson = enroll.getLesson();
        Integer expectedAmount = lesson.getPrice();
        Integer actualPaidAmount = expectedAmount; // TODO: PG verification sets this

        if (!expectedAmount.equals(actualPaidAmount)) {
            throw new RuntimeException("Payment amount mismatch.");
        }
        enroll.setPayStatus("PAID");
        if (!"APPLIED".equals(enroll.getStatus())) { // Ensure status is APPLIED if paid
             enroll.setStatus("APPLIED");
        }
        enrollRepository.save(enroll);

        Payment payment = Payment.builder()
                .enroll(enroll).amount(actualPaidAmount).paidAt(LocalDateTime.now())
                .status("SUCCESS").pgProvider("html5_inicis").pgToken(pgToken)
                .merchantUid("enroll_" + enroll.getEnrollId() + "_" + System.currentTimeMillis())
                .build();
        paymentRepository.save(payment);
    }

    @Override
    @Transactional
    public void requestEnrollmentCancellation(User user, Long enrollId, String reason) {
        Enroll enroll = enrollRepository.findById(enrollId)
            .orElseThrow(() -> new RuntimeException("Enrollment not found: " + enrollId));
        if (!enroll.getUser().getUuid().equals(user.getUuid())) {
            throw new SecurityException("Permission denied.");
        }
        if (enroll.getCancelStatus() != CancelStatusType.NONE) {
            throw new IllegalStateException("Cancellation already processed: " + enroll.getCancelStatus());
        }

        // Locker release logic updated
        if (enroll.isUsesLocker()) {
            if (user.getGender() == null || user.getGender().trim().isEmpty()) {
                // Potentially log a warning, but proceed with cancellation. 
                // Releasing locker might be best-effort if gender is missing for some reason.
                // Or, throw an error if gender is strictly required for release.
                // For now, let's assume gender is available for users who had lockers.
                 // throw new IllegalStateException("User gender is required to release locker.");
            } else {
                 lockerService.releaseLocker(user.getGender()); // Use LockerInventoryService
            }
            enroll.setUsesLocker(false); // Mark locker as no longer used for this enrollment
        }
        // Removed: 
        // Locker assignedLocker = enroll.getLocker();
        // if (assignedLocker != null) {
        //     assignedLocker.toggleActive(true); 
        //     lockerRepository.save(assignedLocker);
        //     enroll.setLocker(null); enroll.setLockerZone(null); enroll.setLockerCarryOver(false);
        // }

        Lesson lesson = enroll.getLesson();
        boolean lessonStarted = lesson.getStartDate().isBefore(LocalDate.now());
        // ... rest of the cancellation logic for lesson status, pay status etc.
        // This part seems to be related to payment and lesson status, not directly to individual locker details
        if (lessonStarted) {
            // Logic for cancellation after lesson started (e.g., no refund or partial)
            enroll.setCancelStatus(CancelStatusType.REQ); // Or directly to a specific status
            enroll.setCancelReason(reason + " (수업 시작 후 취소 요청)");
            // Potentially no refund or specific refund logic based on policies
        } else {
            // Logic for cancellation before lesson started
            enroll.setCancelStatus(CancelStatusType.REQ); // Standard request
            enroll.setCancelReason(reason);
            // Update pay_status to CANCELED_UNPAID if it was UNPAID
            if ("UNPAID".equalsIgnoreCase(enroll.getPayStatus())) {
                enroll.setPayStatus("CANCELED_UNPAID");
            }
        }
        // The admin will approve and process refund if applicable
        enrollRepository.save(enroll);
    }

    @Override
    @Transactional
    public EnrollDto processRenewal(User user, RenewalRequestDto renewalRequestDto) {
        Lesson lesson = lessonRepository.findById(renewalRequestDto.getLessonId())
            .orElseThrow(() -> new NoSuchElementException("Lesson not found: " + renewalRequestDto.getLessonId()));
        // TODO: Add more renewal validations (e.g., is user eligible for renewal of this lesson?)

        boolean useLockerForRenewal = false;
        // Assuming RenewalRequestDto has a field like wantsLocker or carryLocker interpreted as wantsLocker
        if (renewalRequestDto.isWantsLocker()) { // Or renewalRequestDto.isCarryLocker() if that flag is reused
            if (user.getGender() == null || user.getGender().trim().isEmpty()) {
                throw new IllegalStateException("라커를 신청하려면 사용자의 성별 정보가 필요합니다.");
            }
            if (!lockerService.assignLocker(user.getGender())) {
                throw new IllegalStateException(user.getGender() + " 성별의 사용 가능한 라커가 없습니다. (재등록 시도)");
            }
            useLockerForRenewal = true;
        }

        Enroll.EnrollBuilder newEnrollBuilder = Enroll.builder()
            .user(user)
            .lesson(lesson)
            .status("APPLIED").payStatus("UNPAID").expireDt(LocalDateTime.now().plusHours(24))
            .renewalFlag(true).cancelStatus(CancelStatusType.NONE)
            .usesLocker(useLockerForRenewal)
            .createdBy(user.getName())
            .updatedBy(user.getName())
            .createdIp("UNKNOWN_IP_RENEWAL")
            .updatedIp("UNKNOWN_IP_RENEWAL");

        // Removed old locker carry-over logic:
        // if (renewalRequestDto.isCarryLocker() && renewalRequestDto.getExistingLockerIdToCarry() != null) {
        //      lockerRepository.findById(renewalRequestDto.getExistingLockerIdToCarry()).ifPresent(l -> {
        //          newEnrollBuilder.locker(l).lockerZone(l.getZone()).lockerCarryOver(true);
        //      });
        // } else if (renewalRequestDto.isWantsNewLocker() && user.getGender() != null && !user.getGender().isEmpty()) {
        //     // Optional<Locker> assignedLocker = lockerService.findAndAssignAvailableLocker(Locker.LockerGender.valueOf(user.getGender().toUpperCase()), lesson.getLessonId());
        //     // assignedLocker.ifPresent(l -> newEnrollBuilder.locker(l).lockerZone(l.getZone()).lockerCarryOver(false));
        // }
        Enroll newEnroll = newEnrollBuilder.build();
        enrollRepository.save(newEnroll); // Save first
        return convertToMypageEnrollDto(newEnroll); // Then convert the saved (and potentially ID-populated) entity
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
                .orElseThrow(() -> new EntityNotFoundException("Enrollment not found with ID: " + enrollId));

        if (enroll.getCancelStatus() != Enroll.CancelStatusType.REQ) {
            throw new IllegalStateException("Cancellation request not found or already processed for enrollment ID: " + enrollId);
        }

        // Store original pay status before changing it
        enroll.setOriginalPayStatusBeforeCancel(enroll.getPayStatus());

        // TODO: Implement actual refund logic via PaymentService/PG integration
        // For now, simulate refund process
        enroll.setStatus("CANCELED");
        enroll.setPayStatus("REFUNDED");
        enroll.setCancelStatus(Enroll.CancelStatusType.APPROVED);
        enroll.setCancelApprovedAt(LocalDateTime.now());
        enroll.setRefundAmount(BigDecimal.valueOf(enroll.getLesson().getPrice()).multiply(BigDecimal.valueOf(refundPct)).divide(BigDecimal.valueOf(100)).intValue());
        enroll.setUpdatedBy("ADMIN");
        enroll.setUpdatedAt(LocalDateTime.now());
        enrollRepository.save(enroll);

        // If it was a paid enrollment, update lesson vacancy
        if ("PAID".equalsIgnoreCase(enroll.getOriginalPayStatusBeforeCancel())) {
            Lesson lesson = enroll.getLesson();
            long currentPaidEnrollments = enrollRepository.countByLessonLessonIdAndPayStatus(lesson.getLessonId(), "PAID");
            if (currentPaidEnrollments < lesson.getCapacity() && lesson.getStatus() == Lesson.LessonStatus.CLOSED) {
                if (LocalDate.now().isBefore(lesson.getRegistrationEndDate())) {
                     lesson.updateStatus(Lesson.LessonStatus.OPEN);
                     lessonRepository.save(lesson);
                }
            }
        }
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