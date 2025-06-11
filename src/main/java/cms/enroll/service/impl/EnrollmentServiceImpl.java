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
import org.springframework.util.StringUtils;

import javax.persistence.EntityNotFoundException;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneOffset; // Import for ZoneOffset
import java.time.temporal.ChronoUnit; // Added for calculating daysBetween
import java.util.Collections;
import java.util.Comparator;
import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;

import cms.common.exception.BusinessRuleException;
import cms.common.exception.ErrorCode;
import cms.common.exception.ResourceNotFoundException;
import org.springframework.http.HttpStatus; // HttpStatus 추가
import org.springframework.beans.factory.annotation.Value; // Added for defaultLockerFee
import java.time.temporal.ChronoUnit; // Added for calculating daysBetween
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
        // 현재는 user 객체가 null이 아니라고 가정하고 진행.
        List<Enroll> enrolls;
        if (StringUtils.hasText(payStatusFilter)) {
            enrolls = enrollRepository.findByUserUuid(user.getUuid()).stream()
                    .filter(e -> payStatusFilter.equalsIgnoreCase(e.getPayStatus()))
                    .sorted(Comparator.comparing(Enroll::getCreatedAt).reversed())
                    .collect(Collectors.toList());
        } else {
            enrolls = enrollRepository.findByUserUuid(user.getUuid());
            // enrolls.sort(Comparator.comparing(Enroll::getCreatedAt).reversed()); //
            // findByUserUuid가 정렬을 보장하지 않으면 필요
        }

        if (user == null || user.getUuid() == null) { // 방어 코드 추가
            // 이 경우는 사실상 Controller 단에서 @AuthenticationPrincipal에 의해 걸러지거나,
            // Spring Security 설정 오류로 인해 발생할 수 있습니다.
            // USER_NOT_FOUND 또는 AUTHENTICATION_FAILED가 적절할 수 있습니다.
            throw new BusinessRuleException(ErrorCode.AUTHENTICATION_FAILED, HttpStatus.UNAUTHORIZED);
        }

        // enrolls 리스트가 비어있을 경우 ResourceNotFoundException을 던질지, 아니면 빈 페이지를 반환할지는 정책에 따라
        // 다름.
        // 현재 코드는 빈 페이지를 반환하므로 그대로 둡니다.
        int start = (int) pageable.getOffset();
        int end = Math.min((start + pageable.getPageSize()), enrolls.size());
        List<EnrollDto> dtoList = enrolls.isEmpty() ? Collections.emptyList()
                : enrolls.subList(start, end).stream()
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
                "PARTIALLY_REFUNDED",
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
        LocalDate today = LocalDate.now();
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
            // 다음 달의 강습: 현월 26일 ~ 말일까지 신규 회원 등록 가능
            if (today.getDayOfMonth() >= 26 && !today.isAfter(currentYearMonth.atEndOfMonth())) {
                registrationAllowed = true;
            }
            registrationPolicyMsg = "다음 달 강습의 신규회원 등록은 현월 26일부터 말일까지 가능합니다.";
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

        int lockerFee = 0;
        if (initialEnrollRequest.getUsesLocker()) {
            lockerFee = defaultLockerFee; // Use configured locker fee
        }
        int finalAmount = priceAfterMembershipDiscount + lockerFee;

        // *** START Real-time locker allocation and inventory increment ***
        boolean lockerSuccessfullyAllocated = false;
        if (initialEnrollRequest.getUsesLocker()) {
            if (user.getGender() == null || user.getGender().trim().isEmpty()) {
                // This case should ideally be prevented by frontend validation or earlier
                // checks
                // but as a safeguard:
                throw new BusinessRuleException(ErrorCode.LOCKER_GENDER_REQUIRED, "사물함을 신청하려면 사용자의 성별 정보가 필요합니다.");
            }

            // Convert user gender ("0" for Female, "1" for Male) to "FEMALE" or "MALE"
            String lockerGender;
            if ("0".equals(user.getGender())) {
                lockerGender = "FEMALE";
            } else if ("1".equals(user.getGender())) {
                lockerGender = "MALE";
            } else {
                // Unknown gender code from user data, this should ideally not happen if data is
                // clean
                logger.warn("Unknown gender code '{}' for user {}. Cannot determine locker gender.", user.getGender(),
                        user.getUuid());
                throw new BusinessRuleException(ErrorCode.INVALID_USER_GENDER,
                        "사용자의 성별 코드가 유효하지 않습니다: " + user.getGender());
            }

            try {
                logger.info(
                        "Attempting to increment locker count for user-gender: {}, mapped-locker-gender: {} (User: {})",
                        user.getGender(), lockerGender, user.getUuid());
                lockerService.incrementUsedQuantity(lockerGender); // Use the converted gender
                lockerSuccessfullyAllocated = true;
                logger.info("Locker count incremented successfully for mapped-locker-gender: {}", lockerGender);
            } catch (BusinessRuleException e) {
                logger.warn(
                        "Failed to allocate locker for user {} (user-gender: {}, mapped-locker-gender: {}) during initial enrollment. Reason: {}",
                        user.getUuid(), user.getGender(), lockerGender, e.getMessage());
                // If lockerService.incrementUsedQuantity throws (e.g., LOCKER_NOT_AVAILABLE),
                // the enrollment should fail if a locker was mandatory or be allowed without a
                // locker.
                // For now, we re-throw, making locker allocation a hard requirement if
                // usesLocker=true.
                // If it's optional even if requested, handle differently (e.g., set
                // usesLocker=false and proceed).
                throw e;
            }
        }
        // *** END Real-time locker allocation and inventory increment ***

        Enroll enroll = Enroll.builder()
                .user(user)
                .lesson(lesson)
                .status("APPLIED")
                .payStatus("UNPAID")
                .expireDt(LocalDateTime.now().plusMinutes(5))
                .usesLocker(initialEnrollRequest.getUsesLocker())
                .lockerAllocated(lockerSuccessfullyAllocated) // Set based on successful increment
                .membershipType(membershipTypeEnum)
                .finalAmount(finalAmount)
                .discountAppliedPercentage(discountPercentage)
                .createdBy(user.getUuid())
                .createdIp(ipAddress)
                .build();

        Enroll savedEnroll = enrollRepository.save(enroll);
        logger.info("Enrollment record created with ID: {} for user: {}, lesson: {}, membership: {}, finalAmount: {}",
                savedEnroll.getEnrollId(), user.getUuid(), lesson.getLessonId(), membershipTypeEnum, finalAmount);

        // 사물함 배정 로직 호출 (필요한 경우)
        // if (savedEnroll.isUsesLocker()) { ... }

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

        // Locker logic starts here -- 이 부분의 레거시 사물함 로직 제거
        // if (Boolean.TRUE.equals(checkoutRequest.getWantsLocker())) { ... } 부분 전체 삭제
        // 최종 사물함 선택은 /api/v1/payment/confirm 에서 처리.

        // BigDecimal finalAmount = BigDecimal.valueOf(lesson.getPrice()); // 기본 강습료
        // // 사물함 요금 로직이 필요하다면 여기서 CheckoutDto에 반영할 수 있으나, 현재 스키마는 그렇지 않음.
        // // if (Boolean.TRUE.equals(checkoutRequest.getWantsLocker()) && 사물함요금 > 0) {
        // // finalAmount = finalAmount.add(BigDecimal.valueOf(사물함요금));
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
                .orElseThrow(() -> new ResourceNotFoundException("수강 신청 정보를 찾을 수 없습니다 (ID: " + enrollId + ")",
                        ErrorCode.ENROLLMENT_NOT_FOUND));

        if (user == null || !enroll.getUser().getUuid().equals(user.getUuid())) {
            throw new BusinessRuleException(ErrorCode.ACCESS_DENIED);
        }
        // Webhook을 통해 주된 상태 변경이 이루어지므로, 이 메소드는 동기적 확인 또는 보조 역할.
        // assignLocker 등의 사물함 상태 변경은 PaymentServiceImpl.confirmPayment에서 담당.
        // 따라서 이 메소드 내에서 lockerService.assignLocker 호출 로직은 완전히 제거.
        // 이전 grep_search 결과에서 라인 358 if (!lockerService.assignLocker(user.getGender()))
        // { ... } 부분에 해당.
        // 이 블록 전체를 삭제합니다.

        // PG 검증 및 Payment 엔티티 생성/저장 로직은 필요 시 여기에 구현.
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
                // If somehow allocated, release locker before deleting.
                if (enroll.isLockerAllocated()) {
                    String userGender = enroll.getUser().getGender();
                    if (userGender != null && !userGender.trim().isEmpty()) {
                        logger.warn(
                                "Locker was allocated for an UNPAID enrollment (enrollId: {}) being deleted. Releasing.",
                                enrollId);
                        String lockerGenderString; // Renamed for clarity
                        if ("0".equals(userGender)) {
                            lockerGenderString = "FEMALE";
                        } else if ("1".equals(userGender)) {
                            lockerGenderString = "MALE";
                        } else {
                            logger.warn(
                                    "Unknown gender code '{}' for user with enrollId {} during UNPAID cancellation. Cannot determine locker gender for decrement.",
                                    userGender, enrollId);
                            lockerGenderString = null;
                        }
                        if (lockerGenderString != null) {
                            try {
                                logger.info(
                                        "Attempting to decrement locker count for UNPAID cancellation. User-gender: {}, mapped-locker-gender: {} (EnrollId: {})",
                                        userGender, lockerGenderString, enrollId);
                                lockerService.decrementUsedQuantity(lockerGenderString);
                                logger.info(
                                        "Locker decremented successfully for UNPAID cancellation. Mapped-locker-gender: {} (EnrollId: {})",
                                        lockerGenderString, enrollId);
                            } catch (Exception e) {
                                logger.error(
                                        "Failed to decrement locker for UNPAID cancellation (EnrollId: {}, mapped-locker-gender: {}). Reason: {}",
                                        enrollId, lockerGenderString, e.getMessage(), e);
                            }
                        } else {
                            logger.warn(
                                    "Skipping locker decrement for UNPAID cancellation (EnrollId: {}) due to unknown gender code: {}",
                                    enrollId, userGender);
                        }
                    } else {
                        logger.warn(
                                "Cannot decrement locker for UNPAID cancellation (EnrollId: {}) due to missing gender info.",
                                enrollId);
                    }
                }
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

        BigDecimal totalPaidAmount = BigDecimal.valueOf(payment.getPaidAmt() != null ? payment.getPaidAmt() : 0);
        BigDecimal paidLessonAmount = BigDecimal
                .valueOf(payment.getLessonAmount() != null ? payment.getLessonAmount() : 0);
        BigDecimal paidLockerAmount = BigDecimal
                .valueOf(payment.getLockerAmount() != null ? payment.getLockerAmount() : 0);
        BigDecimal originalLessonPrice = BigDecimal.valueOf(lesson.getPrice());

        int systemCalculatedUsedDays = 0;
        if (lesson.getStartDate() != null && lesson.getStartDate().isBefore(calculationDate.plusDays(1))) {
            systemCalculatedUsedDays = (int) ChronoUnit.DAYS.between(lesson.getStartDate(), calculationDate) + 1;
        }

        int effectiveUsedDays = manualUsedDaysOverride != null ? manualUsedDaysOverride : systemCalculatedUsedDays;
        if (effectiveUsedDays < 0) {
            effectiveUsedDays = 0;
        }

        long totalLessonDays = ChronoUnit.DAYS.between(lesson.getStartDate(), lesson.getEndDate()) + 1;
        if (totalLessonDays <= 0) {
            totalLessonDays = 30; // Prevent division by zero
        }

        BigDecimal lessonUsageDeduction = BigDecimal.ZERO;
        if (effectiveUsedDays > 0) {
            lessonUsageDeduction = paidLessonAmount
                    .multiply(BigDecimal.valueOf(effectiveUsedDays))
                    .divide(BigDecimal.valueOf(totalLessonDays), 0, RoundingMode.DOWN);
        }

        BigDecimal finalRefundAmount = totalPaidAmount.subtract(lessonUsageDeduction);
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
                .finalRefundAmount(finalRefundAmount)
                .isFullRefund(isFullRefund)
                .build();
    }

    @Override
    @Transactional
    public void approveEnrollmentCancellationAdmin(Long enrollId, AdminCancelRequestDto cancelRequestDto) {
        Enroll enroll = enrollRepository.findById(enrollId)
                .orElseThrow(() -> new ResourceNotFoundException("Enrollment not found with ID: " + enrollId,
                        ErrorCode.ENROLLMENT_NOT_FOUND));

        // 1. 환불 중복 처리 방지: 이미 환불 절차가 시작되었거나 완료된 건인지 payStatus로 확인
        List<String> nonRefundablePayStatuses = Arrays.asList("REFUNDED", "PARTIALLY_REFUNDED");
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
            enroll.setPayStatus("PARTIALLY_REFUNDED");
        }

        // [수정] 환불(전액/부분) 성공 시, 수강 상태를 'CANCELED'로 명확히 설정
        enroll.setStatus("CANCELED");
        enroll.setCancelStatus(CancelStatusType.APPROVED);
        enroll.setCancelApprovedAt(LocalDateTime.now());
        enroll.setUpdatedBy("ADMIN"); // 또는 현재 관리자 ID

        // === 사물함 반납 처리 로직 추가 ===
        if (enroll.isLockerAllocated()) {
            User user = enroll.getUser();
            if (user != null && user.getGender() != null && !user.getGender().isEmpty()) {
                String lockerGender;
                if ("0".equals(user.getGender())) {
                    lockerGender = "FEMALE";
                } else if ("1".equals(user.getGender())) {
                    lockerGender = "MALE";
                } else {
                    lockerGender = null;
                    logger.warn("환불 처리(enrollId: {})에 따른 사물함 반납 실패: 사용자의 성별 코드가 유효하지 않음 (gender: {})", enrollId,
                            user.getGender());
                }

                if (lockerGender != null) {
                    try {
                        lockerService.decrementUsedQuantity(lockerGender);
                        enroll.setLockerAllocated(false);
                        enroll.setUsesLocker(false);
                        logger.info("환불 처리(enrollId: {})에 따라 {} 사물함이 성공적으로 반납되었습니다.", enrollId, lockerGender);
                    } catch (Exception e) {
                        // 사물함 반납에 실패하더라도 환불 절차는 계속 진행되어야 하므로, 에러 로깅만 하고 예외를 다시 던지지는 않음
                        logger.error("환불 처리(enrollId: {}) 중 사물함 반납에 실패했습니다. (gender: {}): {}", enrollId, lockerGender,
                                e.getMessage(), e);
                    }
                }
            } else {
                logger.warn("환불 처리(enrollId: {})에 따른 사물함 반납 실패: 사용자 정보 또는 성별이 없습니다.", enrollId);
            }
        }
        // === 로직 추가 완료 ===

        enrollRepository.save(enroll);
        logger.info("PAID/Other enrollment ID: {} cancellation processed by admin.", enrollId);
    }

    @Override
    @Transactional(readOnly = true) // DB 변경 없음
    public CalculatedRefundDetailsDto getRefundPreview(Long enrollId, Integer manualUsedDaysPreview) {
        Enroll enroll = enrollRepository.findById(enrollId)
                .orElseThrow(() -> new ResourceNotFoundException("신청 정보를 찾을 수 없습니다. (ID: " + enrollId + ")",
                        ErrorCode.ENROLLMENT_NOT_FOUND));

        // 환불 계산이 불가능한 상태(UNPAID, FAILED, CANCELED 등)에 대한 사전 예외 처리
        if (enroll.getPayStatus() == null ||
                Arrays.asList("UNPAID", "FAILED", "CANCELED", "REFUNDED")
                        .contains(enroll.getPayStatus().toUpperCase())) {
            throw new BusinessRuleException(
                    "현재 상태에서는 환불 금액을 계산할 수 없습니다. (상태: " + enroll.getPayStatus() + ")",
                    ErrorCode.CANNOT_CALCULATE_REFUND,
                    HttpStatus.BAD_REQUEST);
        }

        // 환불의 기준이 되는 원본 결제(PAID 또는 PARTIAL_REFUNDED) 내역을 찾습니다.
        Payment payment = paymentRepository.findByEnrollOrderByCreatedAtDesc(enroll)
                .stream()
                .filter(p -> p.getStatus() == PaymentStatus.PAID || p.getStatus() == PaymentStatus.PARTIAL_REFUNDED)
                .findFirst()
                .orElseThrow(() -> new ResourceNotFoundException(
                        "환불 처리에 필요한 결제 원본 내역을 찾을 수 없습니다. 데이터 확인이 필요합니다. (신청 ID: " + enrollId + ")",
                        ErrorCode.PAYMENT_INFO_NOT_FOUND));

        // 이 payment 객체를 사용하여 환불액 계산 로직을 수행합니다.
        return calculateRefundInternal(enroll, payment, manualUsedDaysPreview, LocalDate.now());
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

        if (enroll.getCancelStatus() != Enroll.CancelStatusType.REQ) {
            throw new BusinessRuleException(ErrorCode.ENROLLMENT_CANCELLATION_NOT_ALLOWED,
                    "취소 요청 상태가 아니거나 이미 처리된 건입니다. 현재 상태: " + enroll.getCancelStatus());
        }

        enroll.setCancelStatus(Enroll.CancelStatusType.DENIED);
        enroll.setCancelReason(comment);
        enroll.setUpdatedBy("ADMIN"); // 또는 현재 로그인한 관리자 ID
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
        EnrollDto.LessonDetails lessonDetails = null;

        if (lesson != null) {
            String periodString = null;
            if (lesson.getStartDate() != null && lesson.getEndDate() != null) {
                periodString = lesson.getStartDate().toString() + " ~ " + lesson.getEndDate().toString();
            }

            Integer remainingSpots = null;
            if (lesson.getCapacity() != null) {
                long paidEnrollments = enrollRepository.countByLessonLessonIdAndPayStatus(lesson.getLessonId(), "PAID");
                long unpaidActiveEnrollments = enrollRepository
                        .countByLessonLessonIdAndStatusAndPayStatusAndExpireDtAfter(lesson.getLessonId(), "APPLIED",
                                "UNPAID", LocalDateTime.now());
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
                } else {
                    if (lessonTimeString.matches("^\\d{1,2}:\\d{2}\\s*[~-]\\s*\\d{1,2}:\\d{2}$")) {
                        timeSlot = lessonTimeString.trim();
                    } else {
                        logger.warn(
                                "LessonTime '{}' did not match expected patterns. Full string stored in time field.",
                                lessonTimeString);
                    }
                }
            }

            // Assumes Lesson.java now has getDisplayName() returning specific display name
            // like "힐링수영반"
            String displayName = (lesson.getDisplayName() != null && !lesson.getDisplayName().isEmpty())
                    ? lesson.getDisplayName()
                    : lesson.getTitle(); // Fallback to title if getDisplayName is null/empty

            // Assumes Lesson.java has getInstructorName() or similar for "성인(온라인)" like
            // data.
            // If "성인(온라인)" is more of a target audience, a different field in Lesson.java
            // might be appropriate.
            String instructorDisplay = lesson.getInstructorName();

            // Assumes Lesson.java now has getRegistrationStartDateTime() of type
            // LocalDateTime.
            String reservationIdString = null;
            if (lesson.getRegistrationStartDateTime() != null) {
                reservationIdString = lesson.getRegistrationStartDateTime()
                        .format(DateTimeFormatter.ofPattern("yyyy.MM.dd HH:mm:ss")) + " 부터";
            }

            // Assumes Lesson.java now has getRegistrationEndDateTime() of type
            // LocalDateTime.
            String receiptIdString = null;
            if (lesson.getRegistrationEndDateTime() != null) {
                receiptIdString = lesson.getRegistrationEndDateTime()
                        .format(DateTimeFormatter.ofPattern("yyyy.MM.dd HH:mm:ss")) + " 까지";
            }

            // Assumes Lesson.java now has getLocationName().
            String locationNameValue = lesson.getLocationName();

            lessonDetails = EnrollDto.LessonDetails.builder()
                    .lessonId(lesson.getLessonId())
                    .title(lesson.getTitle())
                    .name(displayName) // From Lesson.getDisplayName() or fallback
                    .period(periodString)
                    .startDate(lesson.getStartDate() != null ? lesson.getStartDate().toString() : null)
                    .endDate(lesson.getEndDate() != null ? lesson.getEndDate().toString() : null)
                    .time(lesson.getLessonTime()) // Full original time string
                    .days(days) // Parsed
                    .timePrefix(timePrefix) // Parsed
                    .timeSlot(timeSlot) // Parsed
                    .capacity(lesson.getCapacity())
                    .remaining(remainingSpots) // Calculated
                    .price(lesson.getPrice() != null ? new BigDecimal(lesson.getPrice()) : null)
                    .instructor(instructorDisplay) // From Lesson.getInstructorName()
                    .location(locationNameValue) // From Lesson.getLocationName()
                    .reservationId(reservationIdString) // From Lesson.getRegistrationStartDateTime()
                    .receiptId(receiptIdString) // From Lesson.getRegistrationEndDateTime()
                    .build();
        } else {
            logger.error("Enrollment with ID {} has a null lesson associated.", enroll.getEnrollId());
            lessonDetails = EnrollDto.LessonDetails.builder().build();
        }

        // Renewal window logic (remains as previously corrected)
        EnrollDto.RenewalWindow renewalWindowDto = null;
        LocalDate today = LocalDate.now();
        if (lesson != null && lesson.getStartDate() != null) {
            LocalDate lessonStartDate = lesson.getStartDate();
            YearMonth currentMonth = YearMonth.from(today);
            YearMonth lessonStartMonth = YearMonth.from(lessonStartDate);

            if (lessonStartMonth.equals(currentMonth.plusMonths(1))) {
                LocalDate renewalStart = LocalDate.of(today.getYear(), today.getMonth(), 20);
                LocalDate renewalEnd = LocalDate.of(today.getYear(), today.getMonth(), 25);

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

        // Check registration window for renewal: 20th-25th of current month for next
        // month's lesson
        LocalDate today = LocalDate.now();
        YearMonth currentMonth = YearMonth.from(today);
        YearMonth lessonStartMonth = YearMonth.from(lesson.getStartDate());

        boolean isLessonForNextMonth = lessonStartMonth.equals(currentMonth.plusMonths(1));
        boolean isRenewalWindowActive = today.getDayOfMonth() >= 20 && today.getDayOfMonth() <= 25;

        if (!isLessonForNextMonth || !isRenewalWindowActive) {
            throw new BusinessRuleException(ErrorCode.RENEWAL_PERIOD_INVALID,
                    "재수강 신청 기간이 아닙니다. (다음 달 강습: 현월 20~25일)");
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
        enroll.setUsesLocker(renewalRequestDto.isWantsLocker());
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
                || "PARTIALLY_REFUNDED".equalsIgnoreCase(status)) {
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
}