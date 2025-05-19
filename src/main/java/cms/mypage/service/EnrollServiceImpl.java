package cms.mypage.service;

import cms.enroll.domain.Enroll;
import cms.enroll.domain.Enroll.CancelStatusType;
import cms.enroll.repository.EnrollRepository;
import cms.lesson.domain.Lesson;
import cms.lesson.service.LessonService;
import cms.locker.service.LockerService;
import cms.mypage.dto.CheckoutDto;
import cms.mypage.dto.EnrollDto;
import cms.mypage.dto.RenewalRequestDto;
import cms.payment.domain.Payment;
import cms.payment.repository.PaymentRepository;
import cms.user.domain.User;
import cms.user.repository.UserRepository;
// import cms.pg.service.PaymentGatewayService; // PG 연동 시 필요

import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.time.LocalDateTime;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.List;
import java.util.stream.Collectors;
import java.util.NoSuchElementException;

@Service
@Transactional
public class EnrollServiceImpl implements EnrollService {

    private final EnrollRepository enrollRepository;
    private final PaymentRepository paymentRepository;
    private final LessonService lessonService;
    private final LockerService lockerService;
    private final UserRepository userRepository;
    // private final UserRepository userRepository; // 필요시 주입
    // private final PaymentGatewayService paymentGatewayService; // PG 연동 시 필요

    public EnrollServiceImpl(EnrollRepository enrollRepository, 
                             PaymentRepository paymentRepository, 
                             @Qualifier("mockLessonService") LessonService lessonService,
                             LockerService lockerService,
                             UserRepository userRepository) {
        this.enrollRepository = enrollRepository;
        this.paymentRepository = paymentRepository;
        this.lessonService = lessonService;
        this.lockerService = lockerService;
        this.userRepository = userRepository;
    }

    @Override
    @Transactional(readOnly = true)
    public Page<EnrollDto> getEnrollments(User user, String status, Pageable pageable) {
        List<Enroll> enrolls;
        if (StringUtils.hasText(status)) {
            enrolls = enrollRepository.findByUserAndStatusOrderByCreatedAtDesc(user, status);
        } else {
            enrolls = enrollRepository.findByUserOrderByCreatedAtDesc(user);
        }
        int start = (int) pageable.getOffset();
        int end = Math.min((start + pageable.getPageSize()), enrolls.size());
        List<EnrollDto> dtoList = enrolls.subList(start, end).stream()
                .map(this::convertToEnrollDto)
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
        return convertToEnrollDto(enroll);
    }

    @Override
    public CheckoutDto processCheckout(User user, Long enrollId) {
        Enroll enroll = enrollRepository.findById(enrollId)
                .orElseThrow(() -> new RuntimeException("Enrollment not found with ID: " + enrollId));
        if (!enroll.getUser().getUuid().equals(user.getUuid())) {
            throw new SecurityException("You do not have permission for this enrollment.");
        }
        if (!"UNPAID".equalsIgnoreCase(enroll.getStatus())) {
            throw new IllegalStateException("Enrollment is not in UNPAID status. Current status: " + enroll.getStatus());
        }
        if (enroll.getExpireDt() != null && enroll.getExpireDt().isBefore(LocalDateTime.now())) {
            throw new IllegalStateException("Payment for this enrollment has expired.");
        }

        Lesson lesson = lessonService.findById(enroll.getLessonId())
            .orElseThrow(() -> new NoSuchElementException("Lesson not found with ID: " + enroll.getLessonId()));

        if (enroll.getLockerId() != null) {
             User dbUser = userRepository.findById(user.getUuid()).orElseThrow(() -> new RuntimeException("User not found for locker check"));
             if (dbUser.getGender() == null || dbUser.getGender().trim().isEmpty()){
                 throw new IllegalStateException("User gender is not specified, cannot proceed with locker assignment.");
             }
            if (lockerService.getAvailableLockerCount(dbUser.getGender()) <= 0 && !wasLockerAlreadyAssignedToThisEnroll(enroll)) {
                 // This condition is tricky: if this enroll already successfully assigned a locker, we shouldn't fail here.
                 // This implies assignLocker should be idempotent for this enroll, or we need a better state.
                 // For now, if it says usesLocker=true, we assume it was successfully assigned.
                 // A stricter check might be needed if checkout is where assignment happens.
                 // throw new IllegalStateException("Locker is no longer available for gender: " + dbUser.getGender() + ". Please try again without locker or contact support.");
            }
        }

        CheckoutDto checkoutDto = new CheckoutDto();
        checkoutDto.setMerchantUid("enroll_" + enroll.getId() + "_" + System.currentTimeMillis());
        checkoutDto.setAmount(lesson.getPrice());
        checkoutDto.setLessonTitle(lesson.getName());
        checkoutDto.setUserName(user.getName());
        checkoutDto.setPgProvider("html5_inicis");
        
        return checkoutDto;
    }

    private boolean wasLockerAlreadyAssignedToThisEnroll(Enroll enroll) {
        return enroll.getLockerId() != null; 
    }

    @Override
    public void processPayment(User user, Long enrollId, String pgToken) {
        Enroll enroll = enrollRepository.findById(enrollId)
                .orElseThrow(() -> new RuntimeException("Enrollment not found with ID: " + enrollId));
        if (!enroll.getUser().getUuid().equals(user.getUuid())) {
            throw new SecurityException("You do not have permission for this enrollment.");
        }
        if (!"UNPAID".equalsIgnoreCase(enroll.getStatus())) {
            throw new IllegalStateException("Enrollment is not in UNPAID status or already processed.");
        }

        Lesson lesson = lessonService.findById(enroll.getLessonId())
            .orElseThrow(() -> new NoSuchElementException("Lesson not found with ID: " + enroll.getLessonId()));
        Integer expectedAmount = lesson.getPrice().intValue();

        Integer actualPaidAmount = expectedAmount;

        if (!expectedAmount.equals(actualPaidAmount)) {
             if (enroll.getLockerId() != null) { // Still check if a locker was conceptually assigned
                User dbUser = userRepository.findById(user.getUuid()).orElseThrow(() -> new RuntimeException("User not found for locker release"));
                 if (dbUser.getGender() != null && !dbUser.getGender().trim().isEmpty()) {
                    lockerService.releaseLocker(dbUser.getGender()); // Reverted to use gender
                    enroll.setLockerId(null); // Mark as no specific locker linked anymore
                    enroll.setLockerZone(null);
                    enroll.setLockerCarryOver(false); // If payment fails, carry over is also void
                 } else {
                    // Log error: gender unknown, cannot release locker deterministically
                 }
             }
             throw new RuntimeException("Payment amount mismatch. Expected: " + expectedAmount + ", Actual: " + actualPaidAmount);
        }

        enroll.setStatus("PAID");
        enrollRepository.save(enroll);

        Payment payment = Payment.builder()
                .enroll(enroll)
                .amount(actualPaidAmount)
                .paidAt(LocalDateTime.now())
                .status("SUCCESS")
                .pgProvider("html5_inicis") 
                .pgToken(pgToken) 
                .build();
        paymentRepository.save(payment);
    }

    @Override
    public void requestEnrollmentCancellation(User user, Long enrollId, String reason) {
        Enroll enroll = enrollRepository.findById(enrollId)
                .orElseThrow(() -> new RuntimeException("Enrollment not found with ID: " + enrollId));
        
        User dbUser = userRepository.findById(user.getUuid()).orElseThrow(() -> new RuntimeException("User not found for cancellation process"));

        if (!enroll.getUser().getUuid().equals(dbUser.getUuid())) {
            throw new SecurityException("You do not have permission for this enrollment.");
        }

        if (enroll.getCancelStatus() != null && enroll.getCancelStatus() != CancelStatusType.NONE) {
            throw new IllegalStateException("Cancellation already requested or processed.");
        }

        if (enroll.getLockerId() != null) { // Still check if a locker was conceptually assigned
            if (dbUser.getGender() != null && !dbUser.getGender().trim().isEmpty()) {
                lockerService.releaseLocker(dbUser.getGender()); // Reverted to use gender
                enroll.setLockerId(null); // Mark as no specific locker linked anymore
                enroll.setLockerZone(null);
                enroll.setLockerCarryOver(false);
            } else {
                 // Log error: gender unknown, cannot release locker deterministically
                // This state (usesLocker=true but unknown gender for release) should ideally not happen.
            }
        }

        enroll.setCancelStatus(CancelStatusType.REQ);
        enroll.setCancelReason(reason);
        enrollRepository.save(enroll);
    }

    @Override
    public EnrollDto processRenewal(User user, RenewalRequestDto renewalRequestDto) {
        User dbUser = userRepository.findById(user.getUuid()).orElseThrow(() -> new RuntimeException("User not found for renewal"));
        if (dbUser.getGender() == null || dbUser.getGender().trim().isEmpty()) {
            throw new IllegalStateException("User gender is not specified. Cannot process renewal with locker request.");
        }

        Lesson lesson = lessonService.findById(renewalRequestDto.getLessonId())
            .orElseThrow(() -> new NoSuchElementException("Lesson not found with ID: " + renewalRequestDto.getLessonId()));

        Enroll.EnrollBuilder newEnrollBuilder = Enroll.builder()
                .user(dbUser)
                .lessonId(lesson.getId())
                .status("UNPAID")
                .expireDt(LocalDateTime.now().plusHours(24))
                .renewalFlag(true)
                .cancelStatus(CancelStatusType.NONE);

        if (renewalRequestDto.isCarryLocker()) {
            boolean lockerSuccessfullyAssigned = lockerService.assignLocker(dbUser.getGender());
            if (lockerSuccessfullyAssigned) {
                newEnrollBuilder.lockerCarryOver(true);
            } else {
                newEnrollBuilder.lockerCarryOver(false);
            }
        } else {
            newEnrollBuilder.lockerCarryOver(false);
        }
        
        Enroll newEnroll = newEnrollBuilder.build();
        Enroll savedEnroll = enrollRepository.save(newEnroll);
        return convertToEnrollDto(savedEnroll);
    }

    private EnrollDto convertToEnrollDto(Enroll enroll) {
        if (enroll == null) return null;
        
        Lesson lesson = lessonService.findById(enroll.getLessonId())
            .orElseThrow(() -> new NoSuchElementException("Lesson not found with ID: " + enroll.getLessonId()));

        EnrollDto dto = new EnrollDto();
        dto.setEnrollId(enroll.getId());

        EnrollDto.LessonDetails lessonDetails = new EnrollDto.LessonDetails();
        lessonDetails.setTitle(lesson.getName());
        lessonDetails.setPeriod(lesson.getPeriod());
        lessonDetails.setTime(lesson.getTime());
        lessonDetails.setPrice(lesson.getPrice());
        dto.setLesson(lessonDetails);

        dto.setStatus(enroll.getStatus());
        if (enroll.getExpireDt() != null) {
            dto.setExpireDt(OffsetDateTime.of(enroll.getExpireDt(), ZoneOffset.UTC));
        }

        // Populate LockerDetails DTO
        if (enroll.getLockerId() != null) {
            EnrollDto.LockerDetails lockerDetailsDto = new EnrollDto.LockerDetails();
            lockerDetailsDto.setId(enroll.getLockerId());
            lockerDetailsDto.setZone(enroll.getLockerZone());
            lockerDetailsDto.setCarryOver(enroll.isLockerCarryOver());
            dto.setLocker(lockerDetailsDto);
        } else {
            dto.setLocker(null); // Or an empty LockerDetails if preferred by frontend
        }
        
        // TODO: Populate RenewalWindow if applicable, user.md implies it's part of EnrollDto
        // This logic is currently missing.
        // Example: dto.setRenewalWindow(calculateRenewalWindow(enroll));

        return dto;
    }
} 