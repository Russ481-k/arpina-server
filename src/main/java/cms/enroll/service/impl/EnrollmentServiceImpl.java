package cms.enroll.service.impl;

import cms.enroll.domain.Enroll;
import cms.enroll.domain.Enroll.CancelStatusType;
import cms.enroll.repository.EnrollRepository;
import cms.enroll.service.EnrollmentService;

// Domain entities
import cms.swimming.domain.Lesson;
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
import cms.mypage.dto.EnrollInitiationResponseDto;
// cms.swimming.dto.EnrollRequestDto and EnrollResponseDto are used for initial enrollment
import cms.swimming.dto.EnrollRequestDto;
import cms.swimming.dto.EnrollResponseDto;

import cms.payment.domain.Payment;
import cms.payment.domain.PaymentStatus;

import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.annotation.Isolation;

import javax.persistence.EntityNotFoundException;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneOffset; // Import for ZoneOffset
import java.time.temporal.ChronoUnit; // Added for calculating daysBetween
import java.util.Collections;
import java.util.Comparator;
import java.util.List;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import cms.common.exception.BusinessRuleException;
import cms.common.exception.ErrorCode;
import cms.common.exception.ResourceNotFoundException;
import org.springframework.http.HttpStatus; // HttpStatus 추가
import org.springframework.beans.factory.annotation.Value; // Added for defaultLockerFee
import org.springframework.retry.annotation.Backoff;
import org.springframework.retry.annotation.Retryable;
import org.springframework.dao.DeadlockLoserDataAccessException;
import org.springframework.dao.CannotAcquireLockException;
import org.springframework.orm.jpa.JpaOptimisticLockingFailureException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import cms.websocket.handler.LessonCapacityWebSocketHandler;
import cms.admin.enrollment.dto.CalculatedRefundDetailsDto; // 새로 추가한 DTO
import java.time.YearMonth;
import java.time.format.DateTimeFormatter; // Added for formatting
import java.util.regex.Matcher; // Added for regex parsing
import java.util.regex.Pattern; // Added for regex parsing
import java.util.Arrays; // For Arrays.asList
import cms.payment.service.PaymentService;
import cms.admin.enrollment.dto.AdminCancelRequestDto;
import cms.swimming.dto.CheckEnrollmentEligibilityDto;

@Service("enrollmentServiceImpl")
@Transactional
public class EnrollmentServiceImpl implements EnrollmentService {

    private final EnrollRepository enrollRepository;
    private final PaymentRepository paymentRepository;
    private final LessonService lessonService;
    private final LockerService lockerService;
    private final UserRepository userRepository;
    private final LessonRepository lessonRepository;
    private final LessonCapacityWebSocketHandler webSocketHandler;
    private final PaymentService paymentService;

    @Value("${app.default-locker-fee:5000}") // Default to 5000 if not set in properties
    private int defaultLockerFee;

    @Value("${app.enrollment.retry-attempts:3}")
    private int retryAttempts;

    @Value("${app.enrollment.retry-delay:1000}")
    private long retryDelay;

    private static final Logger logger = LoggerFactory.getLogger(EnrollmentServiceImpl.class);
    private static final BigDecimal LESSON_DAILY_RATE = new BigDecimal("3500");
    // private static final BigDecimal LOCKER_DAILY_RATE = new BigDecimal("170"); //
    // 사물함 일일 요금 주석 처리
    // private static final BigDecimal PENALTY_RATE = new BigDecimal("0.10"); // 위약금
    // 비율 주석 처리

    public EnrollmentServiceImpl(EnrollRepository enrollRepository,
            PaymentRepository paymentRepository,
            @Qualifier("swimmingLessonServiceImpl") LessonService lessonService,
            @Qualifier("lockerServiceImpl") LockerService lockerService,
            UserRepository userRepository,
            LessonRepository lessonRepository,
            LessonCapacityWebSocketHandler webSocketHandler,
            PaymentService paymentService
    /* , KispgService kispgService */) { // 주입
        this.enrollRepository = enrollRepository;
        this.paymentRepository = paymentRepository;
        this.lessonService = lessonService;
        this.lockerService = lockerService;
        this.userRepository = userRepository;
        this.lessonRepository = lessonRepository;
        this.webSocketHandler = webSocketHandler;
        this.paymentService = paymentService;
        // this.kispgService = kispgService;
    }

    @Override
    @Transactional(readOnly = true)
    public Page<EnrollDto> getEnrollments(User user, String payStatusFilter, Pageable pageable) {
        if (user == null || user.getUuid() == null) {
            throw new BusinessRuleException(ErrorCode.AUTHENTICATION_FAILED, HttpStatus.UNAUTHORIZED);
        }

        List<Enroll> userEnrollments = enrollRepository.findByUserUuid(user.getUuid());

        List<EnrollDto> dtoList = userEnrollments.stream()
                .map(this::convertToMypageEnrollDto)
                .collect(Collectors.toList());

        LocalDate today = LocalDate.now();
        YearMonth currentMonth = YearMonth.from(today);

        boolean isRenewalWindowActive = today.getDayOfMonth() >= 20 && today.getDayOfMonth() <= 24;

        if (isRenewalWindowActive) {
            List<EnrollDto> renewalPreviews = userEnrollments.stream()
                    .filter(enroll -> "PAID".equals(enroll.getPayStatus())
                            && YearMonth.from(enroll.getLesson().getStartDate()).equals(currentMonth))
                    .map(Enroll::getLesson)
                    .distinct()
                    .flatMap(currentLesson -> {
                        LocalDate nextMonthStart = currentMonth.plusMonths(1).atDay(1);
                        LocalDate nextMonthEnd = currentMonth.plusMonths(1).atEndOfMonth();

                        logger.debug(
                                "Searching for next month's lesson with: Title='{}', Time='{}'",
                                currentLesson.getTitle(), currentLesson.getLessonTime());

                        return lessonRepository.findNextMonthLesson(
                                currentLesson.getTitle(),
                                currentLesson.getLessonTime(),
                                nextMonthStart,
                                nextMonthEnd).map(Stream::of).orElseGet(Stream::empty);
                    })
                    .filter(nextMonthLesson -> {
                        boolean alreadyHasActiveEnrollment = enrollRepository.findByUserUuid(user.getUuid()).stream()
                                .anyMatch(e -> {
                                    if (!e.getLesson().getLessonId().equals(nextMonthLesson.getLessonId())) {
                                        return false;
                                    }
                                    String payStatus = e.getPayStatus();
                                    // PAID 상태는 활성으로 간주
                                    if ("PAID".equals(payStatus)) {
                                        return true;
                                    }
                                    // UNPAID 상태이고 만료되지 않았으면 활성으로 간주
                                    if ("UNPAID".equals(payStatus) && e.getExpireDt() != null
                                            && e.getExpireDt().isAfter(LocalDateTime.now())) {
                                        return true;
                                    }
                                    // REFUNDED, EXPIRED, CANCELED 등 다른 모든 상태는 비활성으로 간주
                                    return false;
                                });

                        if (alreadyHasActiveEnrollment) {
                            logger.debug(
                                    "User {} has already an active enrollment for lesson {}, skipping renewal preview.",
                                    user.getUsername(), nextMonthLesson.getLessonId());
                        }
                        return !alreadyHasActiveEnrollment;
                    })
                    .map(nextMonthLesson -> createRenewalPreviewDto(nextMonthLesson, true))
                    .collect(Collectors.toList());

            dtoList.addAll(renewalPreviews);
        }

        dtoList.sort(Comparator.comparing(dto -> dto.getLesson().getStartDate(), Comparator.reverseOrder()));

        int start = (int) pageable.getOffset();
        int end = Math.min((start + pageable.getPageSize()), dtoList.size());
        List<EnrollDto> pagedDtoList = dtoList.isEmpty() ? Collections.emptyList()
                : dtoList.subList(start, end);

        return new PageImpl<>(pagedDtoList, pageable, dtoList.size());
    }

    private EnrollDto createRenewalPreviewDto(Lesson lesson, boolean isRenewalOpen) {

        EnrollDto.LessonDetails lessonDetails = convertToLessonDetails(lesson);

        EnrollDto.RenewalWindow renewalWindow = null;
        if (lesson.getStartDate() != null) {
            YearMonth lessonStartMonth = YearMonth.from(lesson.getStartDate());
            LocalDate renewalStart = lessonStartMonth.minusMonths(1).atDay(20);
            LocalDate renewalEnd = lessonStartMonth.minusMonths(1).atDay(24);

            renewalWindow = EnrollDto.RenewalWindow.builder()
                    .isOpen(isRenewalOpen)
                    .open(renewalStart.atStartOfDay().atOffset(ZoneOffset.UTC))
                    .close(renewalEnd.atTime(23, 59, 59).atOffset(ZoneOffset.UTC))
                    .build();
        }

        return EnrollDto.builder()
                .enrollId(null) // 실제 수강신청이 아니므로 ID는 null
                .lesson(lessonDetails)
                .status("RENEWAL_AVAILABLE") // 재수강 가능 상태
                .applicationDate(null)
                .usesLocker(false)
                .membershipType(null)
                .renewalWindow(renewalWindow)
                .isRenewal(true) // 재수강 항목임을 명시
                .cancelStatus(Enroll.CancelStatusType.NONE.name())
                .canAttemptPayment(false)
                .build();
    }

    private EnrollDto.LessonDetails convertToLessonDetails(Lesson lesson) {
        if (lesson == null) {
            logger.error("Attempted to convert a null Lesson to LessonDetails.");
            return EnrollDto.LessonDetails.builder().build();
        }

        String periodString = null;
        if (lesson.getStartDate() != null && lesson.getEndDate() != null) {
            periodString = lesson.getStartDate().toString() + " ~ " + lesson.getEndDate().toString();
        }

        Integer remainingSpots = null;
        if (lesson.getCapacity() != null) {
            long paidEnrollments = enrollRepository.countByLessonLessonIdAndPayStatus(lesson.getLessonId(), "PAID");
            long unpaidActiveEnrollments = enrollRepository.countByLessonLessonIdAndStatusAndPayStatusAndExpireDtAfter(
                    lesson.getLessonId(), "APPLIED", "UNPAID", LocalDateTime.now());
            remainingSpots = lesson.getCapacity() - (int) paidEnrollments - (int) unpaidActiveEnrollments;
            if (remainingSpots < 0)
                remainingSpots = 0;
        }

        String days = null;
        String timePrefix = null;
        String timeSlot = null;
        if (lesson.getLessonTime() != null && !lesson.getLessonTime().isEmpty()) {
            String lessonTimeString = lesson.getLessonTime();
            Pattern pattern = Pattern
                    .compile("^(?:(\\(.*?\\))\\s*)?(?:(오전|오후)\\s*)?(\\d{1,2}:\\d{2}\\s*[~-]\\s*\\d{1,2}:\\d{2})$");
            Matcher matcher = pattern.matcher(lessonTimeString.trim());
            if (matcher.find()) {
                days = matcher.group(1);
                timePrefix = matcher.group(2);
                timeSlot = matcher.group(3);
            } else if (lessonTimeString.matches("^\\d{1,2}:\\d{2}\\s*[~-]\\s*\\d{1,2}:\\d{2}$")) {
                timeSlot = lessonTimeString.trim();
            } else {
                logger.warn("LessonTime '{}' did not match expected patterns. Full string stored in time field.",
                        lessonTimeString);
            }
        }

        String displayName = (lesson.getDisplayName() != null && !lesson.getDisplayName().isEmpty())
                ? lesson.getDisplayName()
                : lesson.getTitle();
        String instructorDisplay = lesson.getInstructorName();
        String locationNameValue = lesson.getLocationName();
        String reservationIdString = (lesson.getRegistrationStartDateTime() != null)
                ? lesson.getRegistrationStartDateTime().format(DateTimeFormatter.ofPattern("yyyy.MM.dd HH:mm:ss"))
                        + " 부터"
                : null;
        String receiptIdString = (lesson.getRegistrationEndDateTime() != null)
                ? lesson.getRegistrationEndDateTime().format(DateTimeFormatter.ofPattern("yyyy.MM.dd HH:mm:ss")) + " 까지"
                : null;

        return EnrollDto.LessonDetails.builder()
                .lessonId(lesson.getLessonId())
                .title(lesson.getTitle())
                .name(displayName)
                .period(periodString)
                .startDate(lesson.getStartDate() != null ? lesson.getStartDate().toString() : null)
                .endDate(lesson.getEndDate() != null ? lesson.getEndDate().toString() : null)
                .time(lesson.getLessonTime())
                .days(days)
                .timePrefix(timePrefix)
                .timeSlot(timeSlot)
                .capacity(lesson.getCapacity())
                .remaining(remainingSpots)
                .price(lesson.getPrice() != null ? new BigDecimal(lesson.getPrice()) : null)
                .instructor(instructorDisplay)
                .location(locationNameValue)
                .reservationId(reservationIdString)
                .receiptId(receiptIdString)
                .build();
    }

    @Override
    @Transactional(readOnly = true)
    public EnrollDto getEnrollmentDetails(User user, Long enrollId) {
        if (user == null || user.getUuid() == null) { // 방어 코드
            throw new BusinessRuleException(ErrorCode.AUTHENTICATION_FAILED, HttpStatus.UNAUTHORIZED);
        }
        Enroll enroll = enrollRepository.findById(enrollId)
                .orElseThrow(() -> new ResourceNotFoundException("수강 신청 정보를 찾을 수 없습니다 (ID: " + enrollId + ")",
                        ErrorCode.ENROLLMENT_NOT_FOUND));

        if (!enroll.getUser().getUuid().equals(user.getUuid())) {
            throw new BusinessRuleException(ErrorCode.ACCESS_DENIED, HttpStatus.FORBIDDEN);
        }
        return convertToMypageEnrollDto(enroll);
    }

    /**
     * *** 동시성 제어 및 재시도 로직이 적용된 신규 수강 신청 ***
     * 
     * @Retryable: 교착상태 및 잠금 실패 시 자동 재시도
     *             - DeadlockLoserDataAccessException: 교착상태 감지 시 재시도
     *             - CannotAcquireLockException: 잠금 획득 실패 시 재시도
     *             - JpaOptimisticLockingFailureException: 낙관적 잠금 실패 시 재시도
     */
    @Override
    @Transactional(isolation = Isolation.SERIALIZABLE)
    @Retryable(value = {
            DeadlockLoserDataAccessException.class,
            CannotAcquireLockException.class,
            JpaOptimisticLockingFailureException.class
    }, maxAttempts = 3, backoff = @Backoff(delay = 1000, multiplier = 1.5))
    public EnrollResponseDto createInitialEnrollment(User user, EnrollRequestDto initialEnrollRequest,
            String ipAddress) {
        logger.info("[Enrollment] Starting enrollment process for user: {}, lesson: {}",
                user.getUuid(), initialEnrollRequest.getLessonId());

        long startTime = System.currentTimeMillis();
        try {
            return createInitialEnrollmentInternal(user, initialEnrollRequest, ipAddress);
        } catch (DeadlockLoserDataAccessException e) {
            logger.warn("[Enrollment] Deadlock detected for user: {}, lesson: {}, retrying...",
                    user.getUuid(), initialEnrollRequest.getLessonId());
            throw e; // 재시도를 위해 예외 재발생
        } catch (CannotAcquireLockException e) {
            logger.warn("[Enrollment] Lock acquisition failed for user: {}, lesson: {}, retrying...",
                    user.getUuid(), initialEnrollRequest.getLessonId());
            throw e; // 재시도를 위해 예외 재발생
        } finally {
            long endTime = System.currentTimeMillis();
            logger.info("[Enrollment] Enrollment process completed in {} ms for user: {}",
                    (endTime - startTime), user.getUuid());
        }
    }

    /**
     * 실제 신청 로직 (내부 메소드)
     * // FOR TEMP-ENROLLMENT-BYPASS BRANCH: (기존 주석 제거 또는 업데이트)
     * 
     * 수정된 로직 (2024-05-27):
     * - 신규 신청 시 payStatus를 "UNPAID" 로 설정합니다.
     * - status를 "APPLIED" 로 설정합니다.
     * - expireDt는 현재 결제 모듈 연동 전이므로, UNPAID 신청이 만료되지 않고 "신청 인원"으로 계속 집계되도록 매우 긴
     * 시간(예: 1년 후)으로 설정합니다.
     * (이렇게 하면 남은 정원 계산 시 이 UNPAID 신청이 `unpaidActiveEnrollments`로 카운트됩니다.)
     * - 추후 결제 모듈 연동 시:
     * 1. expireDt를 현재 로직(.plusYears(1)) 대신 짧은 시간(예:
     * LocalDateTime.now().plusMinutes(30))으로 변경해야 합니다.
     * 2. 만료된 UNPAID 신청을 자동으로 'EXPIRED' 또는 'CANCELED_UNPAID' 상태로 변경하고, 필요시 관련 라커 예약도
     * 해제하는 스케줄러(Batch Job) 구현이 필요합니다.
     * 3. 결제가 성공적으로 완료되면 해당 Enroll 레코드의 payStatus를 'PAID'로, status를 상황에 맞게(예:
     * 'ACTIVE') 업데이트하고, expireDt를 null 또는 매우 먼 미래로 변경하여 더 이상 만료되지 않도록 처리해야 합니다.
     * 4. 결제 실패 시 사용자에게 알리고, 신청은 UNPAID 상태로 두거나, 특정 횟수 실패 시 취소 처리할 수 있습니다.
     */
    @Transactional(isolation = Isolation.SERIALIZABLE)
    protected EnrollResponseDto createInitialEnrollmentInternal(User user, EnrollRequestDto initialEnrollRequest,
            String ipAddress) {
        logger.info("Starting initial enrollment process for user: {} with request: {}", user.getUuid(),
                initialEnrollRequest);
        // *** 비관적 잠금으로 동시성 문제 해결 ***
        Lesson lesson = lessonRepository.findByIdWithLock(initialEnrollRequest.getLessonId())
                .orElseThrow(
                        () -> new EntityNotFoundException("강습을 찾을 수 없습니다. ID: " + initialEnrollRequest.getLessonId()));

        // *** START Check for previous admin-cancelled enrollment for this lesson ***
        List<String> adminCancelledPayStatuses = Arrays.asList(
                "REFUNDED",
                "PARTIAL_REFUNDED",
                "REFUND_PENDING_ADMIN_CANCEL");
        boolean hasAdminCancelledEnrollment = enrollRepository
                .existsByUserUuidAndLessonLessonIdAndCancelStatusAndPayStatusIn(
                        user.getUuid(),
                        lesson.getLessonId(),
                        Enroll.CancelStatusType.APPROVED,
                        adminCancelledPayStatuses);

        if (hasAdminCancelledEnrollment) {
            throw new BusinessRuleException(ErrorCode.ENROLLMENT_PREVIOUSLY_CANCELLED_BY_ADMIN,
                    "해당 강습에 대한 이전 신청이 관리자에 의해 취소된 내역이 있어 재신청할 수 없습니다.");
        }
        // *** END Check for previous admin-cancelled enrollment for this lesson ***

        // *** 신규 등록 기간 정책 검사 ***
        LocalDateTime now = LocalDateTime.now();
        LocalDate today = now.toLocalDate();
        YearMonth currentYearMonth = YearMonth.from(today);
        YearMonth lessonStartYearMonth = YearMonth.from(lesson.getStartDate());

        boolean registrationAllowed = false;
        String registrationPolicyMsg = "신청 기간이 아닙니다.";

        if (lessonStartYearMonth.equals(currentYearMonth)) {
            // 현재 달의 강습: 말일까지 신규 신청 가능
            if (!today.isAfter(currentYearMonth.atEndOfMonth())) {
                registrationAllowed = true;
            }
            registrationPolicyMsg = "현재 달의 강습은 말일까지 신청 가능합니다.";
        } else if (lessonStartYearMonth.equals(currentYearMonth.plusMonths(1))) {
            // 다음 달의 강습: 현월 25일 10시 ~ 말일까지 신규 회원 등록 가능
            LocalDateTime registrationStartDateTime = currentYearMonth.atDay(25).atTime(10, 0, 0);
            if (!now.isBefore(registrationStartDateTime) && !today.isAfter(currentYearMonth.atEndOfMonth())) {
                registrationAllowed = true;
            }
            registrationPolicyMsg = "다음 달 강습의 신규회원 등록은 현월 25일 10시부터 말일까지 가능합니다.";
        } else {
            // 그 외 경우 (예: 두 달 후 강습 등)는 현재 정책상 신규 등록 불가
            registrationPolicyMsg = "해당 강습은 현재 신규 등록 기간이 아닙니다.";
        }

        if (!registrationAllowed) {
            throw new BusinessRuleException(ErrorCode.REGISTRATION_PERIOD_INVALID, registrationPolicyMsg);
        }
        // *** END 신규 등록 기간 정책 검사 ***

        // *** 잠금 상태에서 정원 체크 (동시성 안전) ***
        long currentPaidEnrollments = enrollRepository.countByLessonLessonIdAndPayStatus(lesson.getLessonId(), "PAID");
        long currentUnpaidActiveEnrollments = enrollRepository
                .countByLessonLessonIdAndStatusAndPayStatusAndExpireDtAfter(
                        lesson.getLessonId(), "APPLIED", "UNPAID", LocalDateTime.now());
        long totalCurrentEnrollments = currentPaidEnrollments + currentUnpaidActiveEnrollments;
        long availableSlots = lesson.getCapacity() - totalCurrentEnrollments;

        if (availableSlots <= 0) {
            throw new BusinessRuleException(ErrorCode.PAYMENT_PAGE_SLOT_UNAVAILABLE,
                    "정원이 마감되었습니다. 현재 신청된 (결제완료 및 결제대기 포함) 인원: " + totalCurrentEnrollments);
        }

        // *** 기존 신청 체크 (중복 방지) ***
        if (enrollRepository.existsActiveEnrollment(user.getUuid(), initialEnrollRequest.getLessonId())) {
            throw new BusinessRuleException(ErrorCode.DUPLICATE_ENROLLMENT_ATTEMPT,
                    "이미 해당 강습에 대한 활성 신청(결제 완료 또는 대기 포함) 내역이 존재합니다.");
        }

        // *** 월별 신청 제한 체크 ***
        long monthlyEnrollments = enrollRepository.countUserEnrollmentsInMonth(user.getUuid(), lesson.getStartDate());
        if (monthlyEnrollments > 0) {
            throw new BusinessRuleException(ErrorCode.MONTHLY_ENROLLMENT_LIMIT_EXCEEDED,
                    "같은 달에 이미 다른 강습을 신청하셨습니다. 한 달에 한 개의 강습만 신청 가능합니다.");
        }

        // Convert membershipType string to MembershipType enum
        cms.enroll.domain.MembershipType membershipTypeEnum;
        try {
            membershipTypeEnum = cms.enroll.domain.MembershipType.fromValue(initialEnrollRequest.getMembershipType());
        } catch (IllegalArgumentException e) {
            logger.warn("Invalid membershipType received: {}. Details: {}", initialEnrollRequest.getMembershipType(),
                    e.getMessage());
            throw new BusinessRuleException(ErrorCode.INVALID_INPUT_VALUE,
                    "유효하지 않은 할인 유형입니다: " + initialEnrollRequest.getMembershipType());
        }

        // Calculate final price
        int lessonPrice = lesson.getPrice();
        int discountPercentage = membershipTypeEnum.getDiscountPercentage();
        int priceAfterMembershipDiscount = lessonPrice - (lessonPrice * discountPercentage / 100);

        int finalAmount = priceAfterMembershipDiscount;

        boolean isRenewal = isRenewal(user, lesson);

        Enroll enroll = Enroll.builder()
                .user(user)
                .lesson(lesson)
                .status("APPLIED")
                .payStatus("UNPAID")
                .expireDt(LocalDateTime.now().plusMinutes(5))
                .usesLocker(initialEnrollRequest.getUsesLocker())
                .lockerAllocated(false) // Locker is not allocated until payment
                .membershipType(membershipTypeEnum)
                .renewalFlag(isRenewal)
                .finalAmount(finalAmount)
                .discountAppliedPercentage(discountPercentage)
                .createdBy(user.getUuid())
                .createdIp(ipAddress)
                .build();

        Enroll savedEnroll = enrollRepository.save(enroll);
        logger.info("Enrollment record created with ID: {} for user: {}, lesson: {}, membership: {}, finalAmount: {}",
                savedEnroll.getEnrollId(), user.getUuid(), lesson.getLessonId(), membershipTypeEnum, finalAmount);

        // WebSocket으로 용량 업데이트 전송 (이전 로직 복원 및 사용)
        long finalPaidCount = enrollRepository.countByLessonLessonIdAndPayStatus(lesson.getLessonId(), "PAID");
        long finalUnpaidActiveCount = enrollRepository.countByLessonLessonIdAndStatusAndPayStatusAndExpireDtAfter(
                lesson.getLessonId(), "APPLIED", "UNPAID", LocalDateTime.now());

        if (webSocketHandler != null) {
            try {
                webSocketHandler.broadcastLessonCapacityUpdate(
                        lesson.getLessonId(),
                        lesson.getCapacity(), // Total capacity
                        (int) finalPaidCount,
                        (int) finalUnpaidActiveCount);
                logger.info(
                        "Sent capacity update via WebSocket for lessonId: {}, total: {}, paid: {}, unpaidActive: {}",
                        lesson.getLessonId(), lesson.getCapacity(), finalPaidCount, finalUnpaidActiveCount);
            } catch (Exception e) {
                logger.warn("[WebSocket] Failed to broadcast capacity update for lesson {}: {}",
                        lesson.getLessonId(), e.getMessage(), e); // Include exception for better diagnostics
            }
        }

        return convertToSwimmingEnrollResponseDto(savedEnroll);
    }

    private boolean isRenewal(User user, Lesson currentLesson) {
        YearMonth currentLessonMonth = YearMonth.from(currentLesson.getStartDate());
        YearMonth previousMonth = currentLessonMonth.minusMonths(1);
        LocalDate previousMonthStart = previousMonth.atDay(1);
        LocalDate previousMonthEnd = previousMonth.atEndOfMonth();

        List<Enroll> previousMonthEnrollments = enrollRepository.findPaidByUserForPreviousMonthLesson(
                user.getUuid(),
                PaymentStatus.PAID.name(),
                currentLesson.getTitle(),
                currentLesson.getLessonTime(),
                previousMonthStart,
                previousMonthEnd);

        return !previousMonthEnrollments.isEmpty();
    }

    @Override
    @Transactional // Ensure transactional behavior for updates
    public CheckoutDto processCheckout(User user, Long enrollId, cms.mypage.dto.CheckoutRequestDto checkoutRequest) {
        if (user == null || user.getUuid() == null) {
            throw new BusinessRuleException(ErrorCode.AUTHENTICATION_FAILED, HttpStatus.UNAUTHORIZED);
        }
        Enroll enroll = enrollRepository.findById(enrollId)
                .orElseThrow(() -> new ResourceNotFoundException("수강 신청 정보를 찾을 수 없습니다 (ID: " + enrollId + ")",
                        ErrorCode.ENROLLMENT_NOT_FOUND));

        if (!enroll.getUser().getUuid().equals(user.getUuid())) {
            throw new BusinessRuleException(ErrorCode.ACCESS_DENIED, HttpStatus.FORBIDDEN);
        }
        if (!"UNPAID".equalsIgnoreCase(enroll.getPayStatus())) {
            throw new BusinessRuleException("결제 대기 상태의 수강 신청이 아닙니다. 현재 상태: " + enroll.getPayStatus(),
                    ErrorCode.NOT_UNPAID_ENROLLMENT_STATUS);
        }
        if (enroll.getExpireDt().isBefore(LocalDateTime.now())) {
            enroll.setStatus("EXPIRED");
            enroll.setPayStatus("EXPIRED");
            enrollRepository.save(enroll);
            throw new BusinessRuleException("결제 가능 시간이 만료되었습니다 (ID: " + enrollId + ")",
                    ErrorCode.ENROLLMENT_PAYMENT_EXPIRED);
        }

        Lesson lesson = enroll.getLesson();
        if (lesson == null) {
            throw new ResourceNotFoundException("연결된 강좌 정보를 찾을 수 없습니다 (수강신청 ID: " + enrollId + ")",
                    ErrorCode.LESSON_NOT_FOUND);
        }

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
                .orElseThrow(() -> new ResourceNotFoundException("Enrollment not found with ID: " + enrollId));

        if (enroll.isUsesLocker()) {
            logger.info("Locker confirmed for user {} ({}) for lesson {}", user.getName(), user.getGender(),
                    enroll.getLesson().getLessonId());

            String genderString = null;
            if ("1".equals(user.getGender())) {
                genderString = "MALE";
            } else if ("0".equals(user.getGender())) {
                genderString = "FEMALE";
            }

            if (genderString != null) {
                lockerService.incrementUsedQuantity(genderString);
            } else {
                logger.warn("Could not determine gender for locker assignment. User: {}, Gender Code: {}",
                        user.getUsername(), user.getGender());
            }
        }

        // ... (기존 결제 처리 로직)
    }

    @Override
    @Transactional
    public void requestEnrollmentCancellation(User user, Long enrollId, String reason) {
        Enroll enroll = enrollRepository.findById(enrollId)
                .orElseThrow(() -> new ResourceNotFoundException("Enrollment not found with ID: " + enrollId,
                        ErrorCode.ENROLLMENT_NOT_FOUND));

        if (!enroll.getUser().getUuid().equals(user.getUuid())) {
            throw new BusinessRuleException(ErrorCode.ACCESS_DENIED,
                    "You do not have permission to cancel this enrollment.");
        }

        // Check if already cancelled to prevent multiple attempts on a record that
        // might get processed differently.
        if (enroll.getCancelStatus() != null && enroll.getCancelStatus() != Enroll.CancelStatusType.NONE) {
            throw new BusinessRuleException(ErrorCode.ALREADY_CANCELLED_ENROLLMENT,
                    "This enrollment is already in a cancellation process or has been cancelled/denied.");
        }

        Lesson lesson = enroll.getLesson();
        if (lesson == null) {
            throw new ResourceNotFoundException("Lesson not found for enrollment ID: " + enrollId,
                    ErrorCode.LESSON_NOT_FOUND);
        }

        if ("UNPAID".equalsIgnoreCase(enroll.getPayStatus())) {
            // Check for associated payments for this UNPAID enrollment. This should ideally
            // be zero.
            long paymentCount = paymentRepository.countByEnrollEnrollId(enrollId);
            if (paymentCount > 0) {
                logger.error(
                        "UNPAID enrollment (ID: {}) has {} associated payment records. Cannot delete directly. Please review.",
                        enrollId, paymentCount);
                // Fallback: Mark as cancelled instead of deleting, or throw a specific error.
                // For now, let's use the previous logic of marking it cancelled to avoid data
                // loss if payments exist unexpectedly.
                enroll.setCancelRequestedAt(LocalDateTime.now());
                enroll.setCancelReason(reason);
                enroll.setStatus("CANCELED");
                enroll.setPayStatus("CANCELED_UNPAID");
                enroll.setCancelStatus(CancelStatusType.APPROVED);
                enroll.setCancelApprovedAt(LocalDateTime.now());
                enroll.setRefundAmount(0);
                logger.warn(
                        "Enrollment ID: {} was UNPAID but had payments. Marked as CANCELED_UNPAID instead of deleting.",
                        enrollId);
            } else {
                logger.info("UNPAID enrollment cancellation request (enrollId: {}). Deleting enrollment record.",
                        enrollId);

                // ❌ 사용자 취소는 환불이 아니므로 사물함 재고에 영향을 주지 않음
                // 미결제 건 삭제 시에도 사물함 재고는 변경하지 않음
                logger.info("미결제 건(enrollId: {}) 사용자 취소 - 사물함 재고는 변경하지 않음 (환불이 아님)", enrollId);

                enrollRepository.delete(enroll); // Delete the enrollment record
                // No need to save 'enroll' object after deletion.
                return; // Exit after deletion
            }

        } else if ("PAID".equalsIgnoreCase(enroll.getPayStatus())) {
            // Existing logic for PAID enrollments
            LocalDateTime now = LocalDateTime.now();
            enroll.setCancelRequestedAt(now);
            enroll.setCancelReason(reason);
            enroll.setOriginalPayStatusBeforeCancel(enroll.getPayStatus());
            LocalDate today = LocalDate.now();

            if (today.isBefore(lesson.getStartDate())) {
                logger.info(
                        "PAID enrollment cancellation request before lesson start (enrollId: {}). Requesting refund.",
                        enrollId);
                enroll.setStatus("CANCELED");
                enroll.setPayStatus("REFUND_REQUESTED");
                enroll.setCancelStatus(CancelStatusType.REQ);
            } else {
                logger.info(
                        "PAID enrollment cancellation request on/after lesson start (enrollId: {}). Admin review required.",
                        enrollId);
                enroll.setStatus("CANCELED_REQ");
                enroll.setPayStatus("REFUND_REQUESTED");
                enroll.setCancelStatus(CancelStatusType.REQ);
            }
        } else {
            logger.warn(
                    "Cancellation requested for enrollment (enrollId: {}) with unhandled payStatus: {}. No action taken.",
                    enrollId, enroll.getPayStatus());
            throw new BusinessRuleException(ErrorCode.ENROLLMENT_CANCELLATION_NOT_ALLOWED,
                    "Cancellation is not allowed for the current payment status: " + enroll.getPayStatus());
        }
        enrollRepository.save(enroll); // Save changes if not deleted
    }

    /**
     * 환불액 계산을 위한 내부 헬퍼 메소드.
     *
     * @param enroll                 환불 대상 수강 정보
     * @param payment                해당 수강에 대한 결제 정보
     * @param manualUsedDaysOverride 관리자가 입력한 사용일수 (우선 적용). null이면 시스템 자동 계산.
     * @param calculationDate        계산 기준일 (일반적으로 관리자 승인일 또는 현재일)
     * @return 계산된 환불 상세 내역 DTO
     */
    private CalculatedRefundDetailsDto calculateRefundInternal(Enroll enroll, Payment payment,
            Integer manualUsedDaysOverride, LocalDate calculationDate) {

        Lesson lesson = enroll.getLesson();
        if (lesson == null) {
            throw new ResourceNotFoundException("Lesson not found for enrollment: " + enroll.getEnrollId(),
                    ErrorCode.LESSON_NOT_FOUND);
        }

        // 결제 정보에서 총 결제금액 가져오기
        BigDecimal totalPaidAmount = BigDecimal.valueOf(payment.getPaidAmt());

        // 사물함 사용 여부에 따라 강습료와 사물함료 동적 계산
        BigDecimal paidLessonAmount;
        BigDecimal paidLockerAmount;

        if (enroll.isUsesLocker()) {
            paidLockerAmount = BigDecimal.valueOf(defaultLockerFee);
            paidLessonAmount = totalPaidAmount.subtract(paidLockerAmount);
        } else {
            paidLockerAmount = BigDecimal.ZERO;
            paidLessonAmount = totalPaidAmount;
        }

        BigDecimal originalLessonPrice = BigDecimal.valueOf(lesson.getPrice());

        // 시스템 계산 사용일수
        int systemCalculatedUsedDays = 0;
        if (lesson.getStartDate() != null && lesson.getStartDate().isBefore(calculationDate.plusDays(1))) {
            systemCalculatedUsedDays = (int) ChronoUnit.DAYS.between(lesson.getStartDate(), calculationDate) + 1;
        }

        // 실제 사용일수 (수동 입력값이 있으면 우선 사용)
        int effectiveUsedDays = manualUsedDaysOverride != null ? manualUsedDaysOverride : systemCalculatedUsedDays;
        if (effectiveUsedDays < 0) {
            effectiveUsedDays = 0;
        }

        // 강습 사용일수에 따른 차감액 계산 (1일당 3,500원)
        BigDecimal lessonUsageDeduction = BigDecimal.ZERO;
        if (effectiveUsedDays > 0) {
            lessonUsageDeduction = LESSON_DAILY_RATE.multiply(BigDecimal.valueOf(effectiveUsedDays));

            // 차감액이 실제 지불한 강습료를 초과하지 않도록 제한
            if (lessonUsageDeduction.compareTo(paidLessonAmount) > 0) {
                lessonUsageDeduction = paidLessonAmount;
            }
        }

        // 사물함 차감액 계산 (사물함을 사용했다면 전액 차감)
        BigDecimal lockerDeduction = BigDecimal.ZERO;
        if (enroll.isUsesLocker()) {
            lockerDeduction = paidLockerAmount;
        }

        BigDecimal finalRefundAmount = totalPaidAmount.subtract(lessonUsageDeduction).subtract(lockerDeduction);
        if (finalRefundAmount.compareTo(BigDecimal.ZERO) < 0) {
            finalRefundAmount = BigDecimal.ZERO;
        }

        boolean isFullRefund = finalRefundAmount.compareTo(totalPaidAmount) == 0;

        return CalculatedRefundDetailsDto.builder()
                .systemCalculatedUsedDays(systemCalculatedUsedDays)
                .manualUsedDays(manualUsedDaysOverride)
                .effectiveUsedDays(effectiveUsedDays)
                .originalLessonPrice(originalLessonPrice)
                .paidLessonAmount(paidLessonAmount)
                .paidLockerAmount(paidLockerAmount)
                .lessonUsageDeduction(lessonUsageDeduction)
                .lockerDeduction(lockerDeduction)
                .finalRefundAmount(finalRefundAmount)
                .isFullRefund(isFullRefund)
                .build();
    }

    @Override
    @Transactional
    public void approveEnrollmentCancellationAdmin(Long enrollId, AdminCancelRequestDto cancelRequestDto) {
        Enroll enroll = enrollRepository.findById(enrollId)
                .orElseThrow(() -> new ResourceNotFoundException("Enrollment not found with id: " + enrollId));

        // 1. 환불 중복 처리 방지: 이미 환불 절차가 시작되었거나 완료된 건인지 payStatus로 확인
        List<String> nonRefundablePayStatuses = Arrays.asList("REFUNDED", "PARTIAL_REFUNDED");
        if (nonRefundablePayStatuses.contains(enroll.getPayStatus())) {
            throw new BusinessRuleException(ErrorCode.PAYMENT_ALREADY_PROCESSED,
                    "이미 환불이 완료된 건입니다. 현재 상태: " + enroll.getPayStatus());
        }

        // 2. 취소 승인 가능 상태 확인: 사용자가 요청했거나, 관리자가 취소하여 환불 대기 중인 건만 허용
        boolean isUserRequested = enroll.getCancelStatus() == CancelStatusType.REQ;
        boolean isAdminCancelled = enroll.getCancelStatus() == CancelStatusType.ADMIN_CANCELED
                && "REFUND_PENDING_ADMIN_CANCEL".equals(enroll.getPayStatus());

        if (!isUserRequested && !isAdminCancelled) {
            throw new BusinessRuleException(ErrorCode.ENROLLMENT_CANCELLATION_NOT_ALLOWED,
                    "환불 승인이 가능한 상태가 아닙니다. 현재 취소상태: " + enroll.getCancelStatus() + ", 결제상태: " + enroll.getPayStatus());
        }

        List<Payment> payments = paymentRepository.findByEnroll_EnrollIdOrderByCreatedAtDesc(enrollId);
        if (payments.isEmpty()) {
            // Handle unpaid cancellation
            enroll.setStatus("CANCELED");
            enroll.setPayStatus("CANCELED_UNPAID");
            enroll.setCancelStatus(CancelStatusType.APPROVED);
            enroll.setCancelApprovedAt(LocalDateTime.now());
            enroll.setRefundAmount(0);
            enrollRepository.save(enroll);
            logger.info("취소 승인: 결제 내역 없는 수강신청(ID: {})이 취소 처리되었습니다.", enrollId);
            return;
        }

        Payment payment = payments.get(0);
        int totalPaidAmount = payment.getPaidAmt() != null ? payment.getPaidAmt() : 0;
        int finalRefundAmountForPg;
        boolean isPartial;

        if (cancelRequestDto.getFinalRefundAmount() != null) {
            // 관리자가 직접 환불액 입력
            finalRefundAmountForPg = cancelRequestDto.getFinalRefundAmount();
            isPartial = (cancelRequestDto.getIsFullRefund() == null) || !cancelRequestDto.getIsFullRefund();

            enroll.setDaysUsedForRefund(cancelRequestDto.getManualUsedDays());
            logger.info("관리자 직접 환불 처리. enrollId: {}, finalRefundAmount: {}, isPartial: {}", enrollId,
                    finalRefundAmountForPg, isPartial);

        } else {
            // 시스템 계산 로직
            CalculatedRefundDetailsDto refundDetails = calculateRefundInternal(enroll, payment,
                    cancelRequestDto.getManualUsedDays(), LocalDate.now());
            finalRefundAmountForPg = refundDetails.getFinalRefundAmount().intValue();
            isPartial = finalRefundAmountForPg < totalPaidAmount;

            enroll.setDaysUsedForRefund(refundDetails.getEffectiveUsedDays());
        }

        enroll.setRefundAmount(finalRefundAmountForPg);

        if (finalRefundAmountForPg > 0) {
            if (payment.getTid() == null || payment.getTid().trim().isEmpty()) {
                throw new BusinessRuleException(ErrorCode.PAYMENT_CANCEL_NOT_ALLOWED,
                        "PG사 거래 ID(TID)가 없어 자동 환불 불가 (enrollId: " + enrollId + ")");
            }
            String cancelReason = "관리자 승인에 의한 환불 처리";
            if (enroll.getCancelReason() != null && !enroll.getCancelReason().isEmpty()) {
                cancelReason = enroll.getCancelReason();
            }
            paymentService.requestCancelPayment(payment.getId(), finalRefundAmountForPg, cancelReason, isPartial);

        } else { // 환불 금액이 0원인 경우
            enroll.setPayStatus("REFUNDED");
            if (payment != null) {
                payment.setStatus(PaymentStatus.CANCELED); // 0원 환불 시에도 payment 상태를 CANCELED로 업데이트
                paymentRepository.save(payment);
            }
        }

        // PG사 연동 성공 후 Enroll 상태 변경 (PaymentService에서 Exception이 나지 않은 경우)
        Payment updatedPayment = paymentRepository.findById(payment.getId())
                .orElseThrow(() -> new ResourceNotFoundException("Payment record not found after cancellation",
                        ErrorCode.PAYMENT_INFO_NOT_FOUND));

        if (updatedPayment.getStatus() == PaymentStatus.CANCELED) {
            enroll.setPayStatus("REFUNDED");
        } else if (updatedPayment.getStatus() == PaymentStatus.PARTIAL_REFUNDED) {
            enroll.setPayStatus("PARTIAL_REFUNDED");
        }

        // [수정] 환불(전액/부분) 성공 시, 수강 상태를 'CANCELED'로 명확히 설정
        enroll.setStatus("CANCELED");
        enroll.setCancelStatus(CancelStatusType.APPROVED);
        enroll.setCancelApprovedAt(LocalDateTime.now());
        enroll.setUpdatedBy("ADMIN"); // 또는 현재 관리자 ID

        // === 사물함 반납 처리 로직 추가 ===
        if (enroll.isLockerAllocated()) {
            User user = enroll.getUser();
            if (user != null && user.getGender() != null) {
                logger.info("Returning locker for user {} ({}) due to cancellation.", user.getName(), user.getGender());

                String genderString = null;
                if ("1".equals(user.getGender())) {
                    genderString = "MALE";
                } else if ("0".equals(user.getGender())) {
                    genderString = "FEMALE";
                }

                if (genderString != null) {
                    lockerService.decrementUsedQuantity(genderString);
                } else {
                    logger.warn("Could not determine gender for locker return. User: {}, Gender Code: {}",
                            user.getUsername(), user.getGender());
                }
            }
        }
        // === 로직 추가 완료 ===

        // 취소 요청 관련 정보 초기화
        enroll.setCancelRequestedAt(null);
        enroll.setOriginalPayStatusBeforeCancel(null);
        enroll.setCancelApprovedAt(null); // 관리자 취소 승인 시간도 초기화

        enroll.setUpdatedBy("ADMIN");
        enroll.setUpdatedAt(LocalDateTime.now());

        enrollRepository.save(enroll);

        logger.info("환불 요청 거부 완료. enrollId: {}, 복원된 상태: status={}, payStatus={}, usesLocker={}, lockerAllocated={}",
                enrollId, enroll.getStatus(), enroll.getPayStatus(), enroll.isUsesLocker(), enroll.isLockerAllocated());
    }

    @Override
    @Transactional(readOnly = true)
    public CalculatedRefundDetailsDto getRefundPreview(Long enrollId, Integer manualUsedDaysPreview) {
        Enroll enroll = enrollRepository.findById(enrollId)
                .orElseThrow(() -> new ResourceNotFoundException("신청 정보를 찾을 수 없습니다.", ErrorCode.ENROLLMENT_NOT_FOUND));

        if ("UNPAID".equalsIgnoreCase(enroll.getPayStatus())
                || "CANCELED_UNPAID".equalsIgnoreCase(enroll.getPayStatus())) {
            logger.info("미결제(UNPAID or CANCELED_UNPAID) 건(ID: {})에 대한 환불 미리보기 요청. 빈 값(0원)을 반환합니다.", enrollId);
            return CalculatedRefundDetailsDto.createEmpty();
        }

        Payment payment = paymentRepository.findByEnroll_EnrollIdOrderByCreatedAtDesc(enroll.getEnrollId())
                .stream()
                .findFirst()
                .orElse(null);

        // 결제 원본이 없는 PAID/REFUNDED 건은 로직상 문제. 하지만 에러 대신 빈 값 처리.
        if (payment == null) {
            logger.error("환불 처리에 필요한 결제 원본 내역을 찾을 수 없습니다. 데이터 확인이 필요합니다. (신청 ID: {})", enrollId);
            return CalculatedRefundDetailsDto.createEmpty();
        }

        // manualUsedDaysPreview가 null이면, 시스템 로직에 따라 사용일수를 다시 계산
        LocalDate today = LocalDate.now();
        return calculateRefundInternal(enroll, payment, manualUsedDaysPreview, today);
    }

    @Override
    @Transactional(readOnly = true)
    public BigDecimal calculateDisplayRefundAmount(Long enrollId) {
        // enroll.getDaysUsedForRefund()는 DB에 저장된 관리자 최종 입력일 수 있음.
        // 또는 null이면 시스템 계산일 사용
        Enroll enroll = enrollRepository.findById(enrollId)
                .orElseThrow(() -> new ResourceNotFoundException("Enrollment not found with ID: " + enrollId,
                        ErrorCode.ENROLLMENT_NOT_FOUND));

        // 만약 enroll.getDaysUsedForRefund()가 있다면 그 값을 manualUsedDaysPreview로 전달
        // 없다면 null을 전달하여 calculateRefundInternal 내부에서 시스템 자동 계산일 사용토록 함
        Integer previouslySetManualDays = enroll.getDaysUsedForRefund();

        CalculatedRefundDetailsDto details = getRefundPreview(enrollId, previouslySetManualDays);
        return details.getFinalRefundAmount();
    }

    @Override
    @Transactional
    public void denyEnrollmentCancellationAdmin(Long enrollId, String comment) {
        Enroll enroll = enrollRepository.findById(enrollId)
                .orElseThrow(() -> new EntityNotFoundException("Enrollment not found with ID: " + enrollId));

        // 거부 가능한 상태 확인: REQ(요청), DENIED(이미 거부됨), ADMIN_CANCELED(관리자 취소됨) - 모두 재처리 허용
        if (enroll.getCancelStatus() != Enroll.CancelStatusType.REQ &&
                enroll.getCancelStatus() != Enroll.CancelStatusType.DENIED &&
                enroll.getCancelStatus() != Enroll.CancelStatusType.ADMIN_CANCELED) {
            throw new BusinessRuleException(ErrorCode.ENROLLMENT_CANCELLATION_NOT_ALLOWED,
                    "취소 요청 거부가 불가능한 상태입니다. 현재 상태: " + enroll.getCancelStatus());
        }

        // 이미 정상 상태로 복원된 경우 추가 처리 불필요
        if (enroll.getCancelStatus() == Enroll.CancelStatusType.DENIED &&
                "APPLIED".equals(enroll.getStatus()) &&
                ("PAID".equals(enroll.getPayStatus()) || "PAID_OFFLINE".equals(enroll.getPayStatus())) &&
                enroll.isLockerAllocated() == enroll.isUsesLocker()) {
            logger.info("이미 거부 처리 및 상태 복원이 완료된 건입니다. enrollId: {}", enrollId);
            // 코멘트만 업데이트
            enroll.setCancelReason(comment);
            enroll.setUpdatedBy("ADMIN");
            enroll.setUpdatedAt(LocalDateTime.now());
            enrollRepository.save(enroll);
            return;
        }

        logger.info(
                "환불 거부 처리 시작. enrollId: {}, 현재 cancelStatus: {}, status: {}, payStatus: {}, usesLocker: {}, lockerAllocated: {}",
                enrollId, enroll.getCancelStatus(), enroll.getStatus(), enroll.getPayStatus(),
                enroll.isUsesLocker(), enroll.isLockerAllocated());

        // 환불 거부 시 원래 상태로 복원
        enroll.setCancelStatus(Enroll.CancelStatusType.NONE);
        enroll.setCancelReason(comment);

        // 원래 결제 상태로 복원 (취소 요청 전 상태로)
        String originalPayStatus = enroll.getOriginalPayStatusBeforeCancel();
        if (originalPayStatus != null && !originalPayStatus.trim().isEmpty()) {
            enroll.setPayStatus(originalPayStatus);
            logger.info("환불 거부로 인한 payStatus 복원: {} -> {}", enroll.getPayStatus(), originalPayStatus);
        } else {
            // 원래 상태 정보가 없는 경우 현재 상태에 따라 적절히 설정
            if ("REFUND_REQUESTED".equals(enroll.getPayStatus()) ||
                    "REFUND_PENDING_ADMIN_CANCEL".equals(enroll.getPayStatus()) ||
                    "CANCELED_UNPAID".equals(enroll.getPayStatus())) {
                enroll.setPayStatus("PAID");
                logger.info("환불 거부로 인한 payStatus 복원: 원래 상태 불명으로 PAID로 설정. enrollId: {}", enrollId);
            }
        }

        // status도 정상 상태로 복원
        if ("CANCELED".equals(enroll.getStatus()) ||
                "CANCELED_REQ".equals(enroll.getStatus()) ||
                "EXPIRED".equals(enroll.getStatus())) {
            enroll.setStatus("APPLIED");
            logger.info("환불 거부로 인한 status 복원: APPLIED로 설정. enrollId: {}", enrollId);
        }

        // === 사물함 재고 복원 로직 추가 ===
        // 환불 거부 시 사물함을 사용하려고 했으나 할당되지 않은 경우 재할당 시도
        if (enroll.isUsesLocker() && !enroll.isLockerAllocated()) {
            User user = enroll.getUser();
            if (user != null && user.getGender() != null && !user.getGender().isEmpty()) {
                String lockerGender;
                if ("0".equals(user.getGender())) {
                    lockerGender = "FEMALE";
                } else if ("1".equals(user.getGender())) {
                    lockerGender = "MALE";
                } else {
                    lockerGender = null;
                    logger.warn("환불 거부 처리(enrollId: {})에 따른 사물함 재할당 실패: 사용자의 성별 코드가 유효하지 않음 (gender: {})",
                            enrollId, user.getGender());
                }

                if (lockerGender != null) {
                    try {
                        lockerService.incrementUsedQuantity(lockerGender);
                        enroll.setLockerAllocated(true);
                        logger.info("환불 거부 처리(enrollId: {})에 따라 {} 사물함이 성공적으로 재할당되었습니다.", enrollId, lockerGender);
                    } catch (Exception e) {
                        // 사물함 재할당에 실패하더라도 환불 거부 절차는 계속 진행
                        logger.error("환불 거부 처리(enrollId: {}) 중 사물함 재할당에 실패했습니다. (gender: {}): {}",
                                enrollId, lockerGender, e.getMessage(), e);
                        // 사물함 할당에 실패한 경우 usesLocker를 false로 설정
                        enroll.setUsesLocker(false);
                    }
                }
            } else {
                logger.warn("환불 거부 처리(enrollId: {})에 따른 사물함 재할당 실패: 사용자 정보 또는 성별이 없습니다.", enrollId);
                enroll.setUsesLocker(false);
            }
        }
        // === 사물함 재고 복원 로직 완료 ===

        // 취소 요청 관련 정보 초기화
        enroll.setCancelRequestedAt(null);
        enroll.setOriginalPayStatusBeforeCancel(null);
        enroll.setCancelApprovedAt(null); // 관리자 취소 승인 시간도 초기화

        enroll.setUpdatedBy("ADMIN");
        enroll.setUpdatedAt(LocalDateTime.now());

        enrollRepository.save(enroll);

        logger.info("환불 요청 거부 완료. enrollId: {}, 복원된 상태: status={}, payStatus={}, usesLocker={}, lockerAllocated={}",
                enrollId, enroll.getStatus(), enroll.getPayStatus(), enroll.isUsesLocker(), enroll.isLockerAllocated());
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
                .finalAmount(enroll.getFinalAmount())
                .membershipType(enroll.getMembershipType() != null ? enroll.getMembershipType().getValue() : null)
                .usesLocker(enroll.isUsesLocker())
                .renewalFlag(enroll.isRenewalFlag())
                .cancelStatus(enroll.getCancelStatus() != null ? enroll.getCancelStatus().name() : null)
                .cancelReason(enroll.getCancelReason())
                .build();
    }

    private EnrollDto convertToMypageEnrollDto(Enroll enroll) {
        Lesson lesson = enroll.getLesson();
        EnrollDto.LessonDetails lessonDetails = convertToLessonDetails(lesson);

        // Renewal window logic
        EnrollDto.RenewalWindow renewalWindowDto = null;
        LocalDate today = LocalDate.now();
        if (lesson != null && lesson.getStartDate() != null) {
            LocalDate lessonStartDate = lesson.getStartDate();
            YearMonth currentMonth = YearMonth.from(today);
            YearMonth lessonStartMonth = YearMonth.from(lessonStartDate);

            if (lessonStartMonth.equals(currentMonth.plusMonths(1))) {
                LocalDate renewalStart = LocalDate.of(today.getYear(), today.getMonth(), 20);
                LocalDate renewalEnd = LocalDate.of(today.getYear(), today.getMonth(), 24);

                boolean isRenewalOpen = !today.isBefore(renewalStart) && !today.isAfter(renewalEnd);

                renewalWindowDto = EnrollDto.RenewalWindow.builder()
                        .isOpen(isRenewalOpen)
                        .open(renewalStart.atStartOfDay().atOffset(ZoneOffset.UTC))
                        .close(renewalEnd.atTime(23, 59, 59).atOffset(ZoneOffset.UTC))
                        .build();
            }
        }

        boolean canAttemptPayment = "UNPAID".equals(enroll.getPayStatus()) &&
                (enroll.getExpireDt() == null || LocalDateTime.now().isBefore(enroll.getExpireDt()));

        String paymentPageUrl = null;
        // TODO: If direct payment URLs are needed, logic to generate/fetch them should
        // go here.

        return EnrollDto.builder()
                .enrollId(enroll.getEnrollId())
                .lesson(lessonDetails)
                .status(enroll.getPayStatus())
                .applicationDate(enroll.getCreatedAt().atOffset(ZoneOffset.UTC))
                .paymentExpireDt(enroll.getExpireDt() != null ? enroll.getExpireDt().atOffset(ZoneOffset.UTC) : null)
                .usesLocker(enroll.isUsesLocker())
                .membershipType(enroll.getMembershipType() != null ? enroll.getMembershipType().getValue() : null)
                .renewalWindow(renewalWindowDto)
                .isRenewal(enroll.isRenewalFlag())
                .cancelStatus(enroll.getCancelStatus() != null ? enroll.getCancelStatus().name()
                        : Enroll.CancelStatusType.NONE.name())
                .cancelReason(enroll.getCancelReason())
                .canAttemptPayment(canAttemptPayment)
                .paymentPageUrl(paymentPageUrl)
                .build();
    }

    @Override
    @Transactional(isolation = Isolation.SERIALIZABLE)
    public EnrollInitiationResponseDto processRenewal(User user, RenewalRequestDto renewalRequestDto) {
        if (user == null || user.getUuid() == null) {
            throw new BusinessRuleException(ErrorCode.AUTHENTICATION_FAILED, HttpStatus.UNAUTHORIZED);
        }

        Lesson lesson = lessonRepository.findByIdWithLock(renewalRequestDto.getLessonId())
                .orElseThrow(() -> new ResourceNotFoundException(
                        "재수강 대상 강좌를 찾을 수 없습니다 (ID: " + renewalRequestDto.getLessonId() + ")",
                        ErrorCode.LESSON_NOT_FOUND));

        // Check registration window for renewal: 20th 10:00 AM to 24th of current month
        // for next month's lesson
        LocalDateTime now = LocalDateTime.now();
        YearMonth currentMonth = YearMonth.from(now);
        YearMonth lessonStartMonth = YearMonth.from(lesson.getStartDate());

        boolean isLessonForNextMonth = lessonStartMonth.equals(currentMonth.plusMonths(1));

        LocalDateTime renewalStartDateTime = currentMonth.atDay(20).atTime(10, 0, 0);
        LocalDateTime renewalEndDateTime = currentMonth.atDay(24).atTime(23, 59, 59);

        boolean isRenewalWindowActive = isLessonForNextMonth && !now.isBefore(renewalStartDateTime)
                && !now.isAfter(renewalEndDateTime);

        if (!isRenewalWindowActive) {
            throw new BusinessRuleException(ErrorCode.RENEWAL_PERIOD_INVALID,
                    "재수강 신청 기간이 아닙니다. (다음 달 강습: 현월 20일 10시 ~ 24일 23시 59분)");
        }

        long paidEnrollments = enrollRepository.countByLessonLessonIdAndPayStatus(lesson.getLessonId(), "PAID");
        long unpaidExpiringEnrollments = enrollRepository.countByLessonLessonIdAndStatusAndPayStatusAndExpireDtAfter(
                lesson.getLessonId(), "APPLIED", "UNPAID", LocalDateTime.now());
        long availableSlotsForRenewal = lesson.getCapacity() - paidEnrollments - unpaidExpiringEnrollments;

        if (availableSlotsForRenewal <= 0) {
            throw new BusinessRuleException(ErrorCode.PAYMENT_PAGE_SLOT_UNAVAILABLE,
                    "재수강 정원이 마감되었습니다. 현재 정원: " + lesson.getCapacity() + ", 결제완료: " + paidEnrollments + ", 결제대기(만료전): "
                            + unpaidExpiringEnrollments);
        }

        Enroll enroll = enrollRepository.findFirstByUserAndLesson(user, lesson)
                .orElseGet(() -> Enroll.builder().user(user).lesson(lesson).build());

        enroll.setStatus("APPLIED");
        enroll.setPayStatus("UNPAID");
        enroll.setExpireDt(LocalDateTime.now().plusMinutes(5));
        enroll.setRenewalFlag(true);
        enroll.setCancelStatus(CancelStatusType.NONE);
        enroll.setUsesLocker(renewalRequestDto.isUsesLocker());
        enroll.setCreatedBy(user.getName());
        enroll.setUpdatedBy(user.getName());
        enroll.setCreatedIp("UNKNOWN_IP_RENEWAL");
        enroll.setUpdatedIp("UNKNOWN_IP_RENEWAL");

        try {
            Enroll savedEnroll = enrollRepository.save(enroll);

            long finalPaidCountAfterRenewal = enrollRepository.countByLessonLessonIdAndPayStatus(lesson.getLessonId(),
                    "PAID");
            long finalUnpaidActiveCountAfterRenewal = enrollRepository
                    .countByLessonLessonIdAndStatusAndPayStatusAndExpireDtAfter(
                            lesson.getLessonId(), "APPLIED", "UNPAID", LocalDateTime.now());
            long finalTotalEnrollmentsAfterRenewal = finalPaidCountAfterRenewal + finalUnpaidActiveCountAfterRenewal;

            return EnrollInitiationResponseDto.builder()
                    .enrollId(savedEnroll.getEnrollId())
                    .lessonId(lesson.getLessonId())
                    .paymentPageUrl("/payment/process?enroll_id=" + savedEnroll.getEnrollId())
                    .paymentExpiresAt(savedEnroll.getExpireDt().atOffset(ZoneOffset.UTC))
                    .build();
        } catch (Exception e) {
            throw new BusinessRuleException("재수강 신청 처리 중 데이터 저장에 실패했습니다.", ErrorCode.INTERNAL_SERVER_ERROR, e);
        }
    }

    @Override
    @Transactional(readOnly = true)
    public Page<EnrollResponseDto> getAllEnrollmentsAdmin(Pageable pageable) {
        Page<Enroll> enrollPage = enrollRepository.findAll(pageable);
        return enrollPage.map(this::convertToSwimmingEnrollResponseDto);
    }

    @Override
    @Transactional(readOnly = true)
    public Page<EnrollResponseDto> getAllEnrollmentsByStatusAdmin(String status, Pageable pageable) {
        Page<Enroll> enrollPage;
        if ("PAID".equalsIgnoreCase(status) || "UNPAID".equalsIgnoreCase(status) || "EXPIRED".equalsIgnoreCase(status)
                || "REFUNDED".equalsIgnoreCase(status) || "PAYMENT_TIMEOUT".equalsIgnoreCase(status)
                || "PARTIAL_REFUNDED".equalsIgnoreCase(status)) {
            enrollPage = enrollRepository.findByPayStatus(status.toUpperCase(), pageable);
        } else {
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
    @Transactional(readOnly = true)
    public CheckEnrollmentEligibilityDto checkEnrollmentEligibility(User user, Long lessonId) {
        Lesson lesson = lessonRepository.findById(lessonId)
                .orElseThrow(() -> new ResourceNotFoundException("강습을 찾을 수 없습니다. ID: " + lessonId,
                        ErrorCode.LESSON_NOT_FOUND));

        // 사용자가 취소 요청하여 처리 대기중인 건이 있는지 확인
        List<Enroll.CancelStatusType> requestedStatus = Arrays.asList(Enroll.CancelStatusType.REQ);
        if (enrollRepository.existsByUserUuidAndCancelStatusIn(user.getUuid(), requestedStatus)) {
            return new CheckEnrollmentEligibilityDto(false, "현재 취소 요청 처리중인 강습이 있어 신규 신청이 불가능합니다.");
        }

        // 관리자가 취소했으나 아직 환불 처리가 완료되지 않은 건이 있는지 확인
        List<String> refundedPayStatuses = Arrays.asList("REFUNDED", "PARTIAL_REFUNDED");
        if (enrollRepository.existsByUserUuidAndCancelStatusAndPayStatusNotIn(user.getUuid(),
                Enroll.CancelStatusType.ADMIN_CANCELED, refundedPayStatuses)) {
            return new CheckEnrollmentEligibilityDto(false, "관리자에 의해 취소된 강습의 환불이 완료되지 않아 신규 신청이 불가능합니다.");
        }

        // 해당 월에 이미 결제 완료한 강습이 있는지 확인
        boolean hasPaidEnrollment = enrollRepository.existsPaidEnrollmentInMonth(user.getUuid(), lesson.getStartDate());

        if (hasPaidEnrollment) {
            return new CheckEnrollmentEligibilityDto(false, "이미 해당 월에 결제 완료한 강습이 있습니다.");
        }

        return new CheckEnrollmentEligibilityDto(true, "수강 신청이 가능합니다.");
    }
}