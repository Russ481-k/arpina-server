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
import cms.swimming.repository.LockerRepository;
import cms.user.repository.UserRepository;
import cms.payment.repository.PaymentRepository;

// Services
import cms.swimming.service.LessonService;
import cms.swimming.service.LockerService;

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
    private final LockerRepository lockerRepository;

    public EnrollmentServiceImpl(EnrollRepository enrollRepository,
                                 PaymentRepository paymentRepository,
                                 @Qualifier("swimmingLessonServiceImpl") LessonService lessonService, // Assuming qualifier based on domain
                                 @Qualifier("swimmingLockerServiceImpl") LockerService lockerService, // Assuming qualifier
                                 UserRepository userRepository,
                                 LessonRepository lessonRepository,
                                 LockerRepository lockerRepository) {
        this.enrollRepository = enrollRepository;
        this.paymentRepository = paymentRepository;
        this.lessonService = lessonService;
        this.lockerService = lockerService;
        this.userRepository = userRepository;
        this.lessonRepository = lessonRepository;
        this.lockerRepository = lockerRepository;
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

        Locker locker = null;
        String lockerZone = null;
        if (initialEnrollRequest.getLockerId() != null) {
            locker = lockerRepository.findById(initialEnrollRequest.getLockerId())
                    .orElseThrow(() -> new EntityNotFoundException("사물함을 찾을 수 없습니다. ID: " + initialEnrollRequest.getLockerId()));

            if (!locker.getIsActive()) {
                throw new IllegalStateException("사용할 수 없는 사물함입니다.");
            }
            if (user.getGender() == null || user.getGender().trim().isEmpty()) {
                throw new IllegalStateException("User gender is not specified for locker assignment.");
            }
            Locker.LockerGender lockerGenderEnum = Locker.LockerGender.valueOf(user.getGender().toUpperCase());
            if (locker.getGender() != lockerGenderEnum) {
                throw new IllegalStateException("선택한 사물함은 회원님의 성별과 맞지 않습니다.");
            }
            long genderLockerCount = enrollRepository.countLockersByGender(lesson.getLessonId(), lockerGenderEnum);
            if ((Locker.LockerGender.M == lockerGenderEnum && genderLockerCount >= lesson.getMaleLockerCap()) ||
                (Locker.LockerGender.F == lockerGenderEnum && genderLockerCount >= lesson.getFemaleLockerCap())) {
                throw new IllegalStateException(lockerGenderEnum.name() + " 사물함 정원이 초과되었습니다.");
            }
            lockerZone = locker.getZone();
        }

        Enroll enroll = Enroll.builder()
                .user(user)
                .userName(user.getName())
                .lesson(lesson)
                .locker(locker)
                .lockerZone(lockerZone)
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

        long potentiallyPaidEnrollments = enrollRepository.countByLessonLessonIdAndPayStatus(lesson.getLessonId(), "PAID") +
                                          enrollRepository.countByLessonLessonIdAndStatusAndPayStatus(lesson.getLessonId(), "APPLIED", "UNPAID");
        if (potentiallyPaidEnrollments >= lesson.getCapacity() && lesson.getStatus() == Lesson.LessonStatus.OPEN) {
            lesson.updateStatus(Lesson.LessonStatus.CLOSED);
            lessonRepository.save(lesson);
        }

        return EnrollResponseDto.builder()
            .enrollId(savedEnroll.getEnrollId())
            .userId(user.getUuid())
            .userName(savedEnroll.getUserName())
            .status(savedEnroll.getStatus())
            .createdAt(savedEnroll.getCreatedAt())
            .expireDt(savedEnroll.getExpireDt()) // Added to DTO
            .lessonId(lesson.getLessonId())
            .lessonTitle(lesson.getTitle())
            .lockerId(locker != null ? locker.getLockerId() : null)
            .lockerNumber(locker != null ? locker.getLockerNumber() : null)
            .lockerGender(locker != null ? locker.getGender().name() : null)
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
        Locker assignedLocker = enroll.getLocker();
        if (assignedLocker != null) {
            assignedLocker.toggleActive(true); // Locker entity has toggleActive(Boolean)
            lockerRepository.save(assignedLocker);
            enroll.setLocker(null); enroll.setLockerZone(null); enroll.setLockerCarryOver(false);
        }
        Lesson lesson = enroll.getLesson();
        boolean lessonStarted = lesson.getStartDate().isBefore(LocalDate.now());

        if ("PAID".equals(enroll.getPayStatus())) {
            if (lessonStarted) enroll.setCancelStatus(CancelStatusType.REQ);
            else {
                // TODO: PG Refund Logic for real
                enroll.setCancelStatus(CancelStatusType.APPROVED); enroll.setStatus("CANCELED");
                enroll.setPayStatus("REFUND_PENDING");
                if (lesson.getStatus() == Lesson.LessonStatus.CLOSED) {
                     long currentPaid = enrollRepository.countByLessonLessonIdAndPayStatus(lesson.getLessonId(), "PAID");
                     if (currentPaid - 1 < lesson.getCapacity()) { // -1 for this cancelling one
                        lesson.updateStatus(Lesson.LessonStatus.OPEN); lessonRepository.save(lesson);
                     }
                }
            }
        } else {
            enroll.setCancelStatus(CancelStatusType.APPROVED); enroll.setStatus("CANCELED");
            if (!"EXPIRED".equals(enroll.getPayStatus())) enroll.setPayStatus("CANCELED_UNPAID");
        }
        enroll.setCancelReason(reason);
        enrollRepository.save(enroll);
    }

    @Override
    @Transactional
    public EnrollDto processRenewal(User user, RenewalRequestDto renewalRequestDto) {
        Lesson lesson = lessonRepository.findById(renewalRequestDto.getLessonId())
            .orElseThrow(() -> new NoSuchElementException("Lesson not found: " + renewalRequestDto.getLessonId()));
        // TODO: Add more renewal validations

        Enroll.EnrollBuilder newEnrollBuilder = Enroll.builder()
            .user(user).userName(user.getName()).lesson(lesson)
            .status("APPLIED").payStatus("UNPAID").expireDt(LocalDateTime.now().plusHours(24))
            .renewalFlag(true).cancelStatus(CancelStatusType.NONE)
            .createdBy(user.getName()).updatedBy(user.getName())
            .createdIp("UNKNOWN_IP_RENEWAL") // Placeholder for IP in renewal
            .updatedIp("UNKNOWN_IP_RENEWAL"); // Placeholder for IP in renewal

        if (renewalRequestDto.isCarryLocker() && renewalRequestDto.getExistingLockerIdToCarry() != null) {
             lockerRepository.findById(renewalRequestDto.getExistingLockerIdToCarry()).ifPresent(l -> {
                 // TODO: Validate if existingLocker 'l' can be carried over (gender, availability for this lesson, etc.)
                 newEnrollBuilder.locker(l).lockerZone(l.getZone()).lockerCarryOver(true);
             });
        } else if (renewalRequestDto.isWantsNewLocker() && user.getGender() != null && !user.getGender().isEmpty()) {
            // TODO: Implement robust lockerService.findAndAssignAvailableLocker(gender, lessonId) for new assignment
            // Optional<Locker> assignedLocker = lockerService.findAndAssignAvailableLocker(Locker.LockerGender.valueOf(user.getGender().toUpperCase()), lesson.getLessonId());
            // assignedLocker.ifPresent(l -> newEnrollBuilder.locker(l).lockerZone(l.getZone()).lockerCarryOver(false));
        }
        Enroll newEnroll = newEnrollBuilder.build();
        return convertToMypageEnrollDto(enrollRepository.save(newEnroll));
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
        enroll.setPayStatus("REFUNDED"); // Or PARTIALLY_REFUNDED depending on pct
        enroll.setCancelStatus(Enroll.CancelStatusType.APPROVED);
        enroll.setCancelApprovedAt(LocalDateTime.now());
        enroll.setRefundAmount(BigDecimal.valueOf(enroll.getLesson().getPrice()).multiply(BigDecimal.valueOf(refundPct)).divide(BigDecimal.valueOf(100)));
        enroll.setUpdatedBy("ADMIN"); // Consider getting actual admin username
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
        Locker locker = enroll.getLocker();
        User user = enroll.getUser(); // User might be needed for some fields

        return EnrollResponseDto.builder()
                .enrollId(enroll.getEnrollId())
                .userId(user != null ? user.getUuid() : null) // Assuming userId is UUID string
                .userName(enroll.getUserName()) // Or user.getName() if preferred and available
                .status(enroll.getStatus())
                .payStatus(enroll.getPayStatus()) // Added payStatus
                .createdAt(enroll.getCreatedAt())
                .expireDt(enroll.getExpireDt())
                .lessonId(lesson != null ? lesson.getLessonId() : null)
                .lessonTitle(lesson != null ? lesson.getTitle() : null)
                .lessonPrice(lesson != null ? lesson.getPrice() : null) // Added lessonPrice
                .lockerId(locker != null ? locker.getLockerId() : null)
                .lockerNumber(locker != null ? locker.getLockerNumber() : null)
                .lockerZone(locker != null ? locker.getZone() : null) // Added lockerZone
                .lockerGender(locker != null && locker.getGender() != null ? locker.getGender().name() : null)
                .renewalFlag(enroll.isRenewalFlag()) // Added renewalFlag
                .cancelStatus(enroll.getCancelStatus() != null ? enroll.getCancelStatus().name() : null) // Added cancelStatus
                .cancelReason(enroll.getCancelReason()) // Added cancelReason
                .build();
    }

    private EnrollDto convertToMypageEnrollDto(Enroll enroll) {
        if (enroll == null) return null;
        Lesson lesson = enroll.getLesson();
        if (lesson == null && enroll.getLesson() != null) { 
            lesson = lessonRepository.findById(enroll.getLesson().getLessonId()).orElse(null);
        }
        EnrollDto.LessonDetails lessonDetails = new EnrollDto.LessonDetails();
        if (lesson != null) {
            lessonDetails.setTitle(lesson.getTitle());
            lessonDetails.setPeriod(lesson.getStartDate().toString() + " ~ " + lesson.getEndDate().toString());
            // lessonDetails.setTime(lesson.getTime()); // Lesson DTO needs time field
            lessonDetails.setPrice(new BigDecimal(lesson.getPrice()));
        }
        EnrollDto.LockerDetails lockerDetails = null;
        if (enroll.getLocker() != null) {
            lockerDetails = new EnrollDto.LockerDetails();
            lockerDetails.setId(enroll.getLocker().getLockerId());
            lockerDetails.setZone(enroll.getLockerZone());
            lockerDetails.setCarryOver(enroll.isLockerCarryOver());
        }
        EnrollDto.RenewalWindow renewalWindow = null;
        // Example: if (lesson != null && lesson.isRenewalWindowOpen(LocalDateTime.now())) {
        // renewalWindow = new EnrollDto.RenewalWindow(true, lesson.getRenewalOpenDate(), lesson.getRenewalCloseDate()); }

        return EnrollDto.builder()
            .enrollId(enroll.getEnrollId())
            .lesson(lessonDetails)
            .status(enroll.getPayStatus())
            .applicationDate(enroll.getCreatedAt() != null ? enroll.getCreatedAt().atOffset(ZoneOffset.UTC) : null)
            .paymentExpireDt(enroll.getExpireDt() != null ? enroll.getExpireDt().atOffset(ZoneOffset.UTC) : null)
            .locker(lockerDetails)
            .renewalWindow(renewalWindow)
            .isRenewal(enroll.isRenewalFlag())
            .cancelStatus(enroll.getCancelStatus() != null ? enroll.getCancelStatus().name() : null)
            .cancelReason(enroll.getCancelReason())
            .build();
    }
} 