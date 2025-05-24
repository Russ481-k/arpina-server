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
import cms.mypage.dto.EnrollInitiationResponseDto;
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
import org.springframework.transaction.annotation.Isolation;
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
    // private final KispgService kispgService; // KISPG 서비스 주입 (실제 구현 시 필요)

    @Value("${app.default-locker-fee:5000}") // Default to 5000 if not set in properties
    private int defaultLockerFee;

    @Value("${app.enrollment.retry-attempts:3}")
    private int retryAttempts;

    @Value("${app.enrollment.retry-delay:1000}")
    private long retryDelay;

    private static final Logger logger = LoggerFactory.getLogger(EnrollmentServiceImpl.class);
    private static final BigDecimal LESSON_DAILY_RATE = new BigDecimal("3500");
    private static final BigDecimal LOCKER_DAILY_RATE = new BigDecimal("170");
    private static final BigDecimal PENALTY_RATE = new BigDecimal("0.10");

    public EnrollmentServiceImpl(EnrollRepository enrollRepository,
                                 PaymentRepository paymentRepository,
                                 @Qualifier("swimmingLessonServiceImpl") LessonService lessonService,
                                 @Qualifier("lockerServiceImpl") LockerService lockerService,
                                 UserRepository userRepository,
                                 LessonRepository lessonRepository,
                                 LessonCapacityWebSocketHandler webSocketHandler
                                 /*, KispgService kispgService */) { // 주입
        this.enrollRepository = enrollRepository;
        this.paymentRepository = paymentRepository;
        this.lessonService = lessonService;
        this.lockerService = lockerService;
        this.userRepository = userRepository;
        this.lessonRepository = lessonRepository;
        this.webSocketHandler = webSocketHandler;
        // this.kispgService = kispgService;
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

    /**
     * *** 동시성 제어 및 재시도 로직이 적용된 신규 수강 신청 ***
     * 
     * @Retryable: 교착상태 및 잠금 실패 시 자동 재시도
     * - DeadlockLoserDataAccessException: 교착상태 감지 시 재시도
     * - CannotAcquireLockException: 잠금 획득 실패 시 재시도  
     * - JpaOptimisticLockingFailureException: 낙관적 잠금 실패 시 재시도
     */
    @Override
    @Transactional(isolation = Isolation.SERIALIZABLE)
    @Retryable(
        value = {
            DeadlockLoserDataAccessException.class,
            CannotAcquireLockException.class, 
            JpaOptimisticLockingFailureException.class
        },
        maxAttempts = 3,
        backoff = @Backoff(delay = 1000, multiplier = 1.5)
    )
    public EnrollResponseDto createInitialEnrollment(User user, EnrollRequestDto initialEnrollRequest, String ipAddress) {
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
     */
    private EnrollResponseDto createInitialEnrollmentInternal(User user, EnrollRequestDto initialEnrollRequest, String ipAddress) {
        // *** 비관적 잠금으로 동시성 문제 해결 ***
        // 동시에 여러 사용자가 같은 강좌에 신청하는 것을 방지
        Lesson lesson = lessonRepository.findByIdWithLock(initialEnrollRequest.getLessonId())
                .orElseThrow(() -> new EntityNotFoundException("강습을 찾을 수 없습니다. ID: " + initialEnrollRequest.getLessonId()));

        if (lesson.getStatus() != Lesson.LessonStatus.OPEN) {
            throw new BusinessRuleException(ErrorCode.LESSON_NOT_OPEN_FOR_ENROLLMENT, "신청 가능한 강습이 아닙니다. 현재 상태: " + lesson.getStatus());
        }

        // *** 잠금 상태에서 정원 체크 (동시성 안전) ***
        long paidEnrollments = enrollRepository.countByLessonLessonIdAndPayStatus(lesson.getLessonId(), "PAID");
        long unpaidExpiringEnrollments = enrollRepository.countByLessonLessonIdAndStatusAndPayStatusAndExpireDtAfter(lesson.getLessonId(), "APPLIED", "UNPAID", LocalDateTime.now());
        long availableSlots = lesson.getCapacity() - paidEnrollments - unpaidExpiringEnrollments;

        if (availableSlots <= 0) {
            // 잠금 해제 후 예외 발생 (다른 사용자들이 대기 중)
            throw new BusinessRuleException(ErrorCode.PAYMENT_PAGE_SLOT_UNAVAILABLE, 
                "정원이 마감되었습니다. 현재 정원: " + lesson.getCapacity() + ", 결제완료: " + paidEnrollments + ", 결제대기: " + unpaidExpiringEnrollments);
        }

        // *** 기존 신청 체크 (중복 방지) ***
        Optional<Enroll> existingEnrollOpt = enrollRepository.findByUserUuidAndLessonLessonIdAndStatus(
                user.getUuid(), initialEnrollRequest.getLessonId(), "APPLIED");
        if (existingEnrollOpt.isPresent()) {
            Enroll exEnroll = existingEnrollOpt.get();
            if ("PAID".equals(exEnroll.getPayStatus())) {
                throw new BusinessRuleException(ErrorCode.DUPLICATE_ENROLLMENT_ATTEMPT, "이미 해당 강습에 대해 결제 완료된 신청 내역이 존재합니다.");
            }
            if ("UNPAID".equals(exEnroll.getPayStatus()) && exEnroll.getExpireDt().isAfter(LocalDateTime.now())) {
                 throw new BusinessRuleException(ErrorCode.DUPLICATE_ENROLLMENT_ATTEMPT, "이미 신청한 강습의 결제가능 시간이 남아있습니다. 재수강 버튼을 통해 결제를 진행해주세요. 만료시간: " + exEnroll.getExpireDt());
            }
        }

        // *** 월별 신청 제한 체크 ***
        long monthlyEnrollments = enrollRepository.countUserEnrollmentsInMonth(user.getUuid(), lesson.getStartDate());
        if (monthlyEnrollments > 0) {
            throw new BusinessRuleException(ErrorCode.MONTHLY_ENROLLMENT_LIMIT_EXCEEDED, "같은 달에 이미 다른 강습을 신청하셨습니다. 한 달에 한 개의 강습만 신청 가능합니다.");
        }

        // *** 잠금 상태에서 Enroll 생성 (원자적 연산) ***
        Enroll enroll = Enroll.builder()
                .user(user)
                .lesson(lesson)
                .usesLocker(false)
                .status("APPLIED")
                .payStatus("UNPAID")
                .expireDt(LocalDateTime.now().plusMinutes(5))
                .renewalFlag(false)
                .createdBy(user.getName())
                .createdIp(ipAddress)
                .updatedBy(user.getName())
                .updatedIp(ipAddress)
                .build();
        Enroll savedEnroll = enrollRepository.save(enroll);

        // *** 정원 달성 시 강좌 상태 변경 ***
        // 재계산하여 정확한 수량 확인
        long finalPaidCount = enrollRepository.countByLessonLessonIdAndPayStatus(lesson.getLessonId(), "PAID");
        long finalUnpaidCount = enrollRepository.countByLessonLessonIdAndStatusAndPayStatus(lesson.getLessonId(), "APPLIED", "UNPAID");
        if ((finalPaidCount + finalUnpaidCount) >= lesson.getCapacity() && lesson.getStatus() == Lesson.LessonStatus.OPEN) {
            lesson.updateStatus(Lesson.LessonStatus.CLOSED);
            lessonRepository.save(lesson);
        }

        // *** 실시간 정원 정보 업데이트 브로드캐스트 ***
        try {
            webSocketHandler.broadcastLessonCapacityUpdate(
                lesson.getLessonId(),
                lesson.getCapacity(),
                (int) finalPaidCount,
                (int) finalUnpaidCount
            );
            logger.debug("[WebSocket] Broadcasted capacity update for lesson {}", lesson.getLessonId());
        } catch (Exception e) {
            logger.warn("[WebSocket] Failed to broadcast capacity update for lesson {}: {}", 
                       lesson.getLessonId(), e.getMessage());
        }

        // *** 응답 반환 ***
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

        Lesson lesson = enroll.getLesson();
        if (lesson == null) {
            throw new ResourceNotFoundException("Lesson not found for enrollment ID: " + enrollId, ErrorCode.LESSON_NOT_FOUND);
        }
        
        LocalDate today = LocalDate.now();
        enroll.setCancelRequestedAt(LocalDateTime.now()); // 취소 요청 시각 기록

        if (today.isBefore(lesson.getStartDate())) {
            // 수강 시작일 전: 위약금 없이 전액 환불 요청
            logger.info("수강 시작일 전 취소 요청 (enrollId: {}). 전액 환불 처리 시도.", enrollId);
            enroll.setOriginalPayStatusBeforeCancel(enroll.getPayStatus()); // 현재 결제 상태 저장
            enroll.setStatus("CANCELED"); 
            enroll.setPayStatus("REFUND_REQUESTED"); // PG사와 연동하여 실제 환불 처리 필요
            enroll.setCancelStatus(Enroll.CancelStatusType.REQ); // 관리자 승인 없이 바로 REQ (또는 자동 APPROVED)
            enroll.setCancelReason(reason);
            enroll.setRefundAmount(enroll.getLesson().getPrice()); // 단순 예시, 실제로는 Payment 테이블 금액 참조
                                                                // 사물함 금액도 고려해야 함

            // KISPG 전액 환불 로직 호출 (실제 구현 필요)
            // Payment payment = paymentRepository.findByEnroll_EnrollId(enrollId).orElse(null);
            // if (payment != null && payment.getTid() != null) {
            //     boolean refundSuccess = kispgService.requestFullRefund(payment.getTid(), "사용자 강습 시작 전 취소");
            //     if (refundSuccess) {
            //         enroll.setPayStatus("REFUNDED");
            //         enroll.setCancelStatus(Enroll.CancelStatusType.APPROVED); // 자동 승인 간주
            //         payment.setStatus("CANCELED"); // 또는 REFUNDED
            //         payment.setRefundedAmt(payment.getPaidAmt());
            //         payment.setRefundDt(LocalDateTime.now());
            //         paymentRepository.save(payment);
            //     } else {
            //          logger.error("KISPG 전액 환불 실패 (enrollId: {}, tid: {})", enrollId, payment.getTid());
            //          enroll.setCancelStatus(Enroll.CancelStatusType.PENDING); // 실패 시 관리자 확인 필요
            //     }
            // } else {
            //      logger.warn("결제 정보 또는 TID가 없어 PG 환불 불가 (enrollId: {})", enrollId);
            //      enroll.setCancelStatus(Enroll.CancelStatusType.PENDING); // PG 연동 불가 시 관리자 확인
            // }

            if (enroll.isLockerAllocated()) {
                String userGender = enroll.getUser().getGender();
                if (userGender != null && !userGender.trim().isEmpty()) {
                    lockerService.decrementUsedQuantity(userGender.toUpperCase());
                    enroll.setLockerAllocated(false);
                }
            }
            enroll.setUsesLocker(false);
            enroll.setLockerPgToken(null);

        } else {
            // 수강 시작일 후: 관리자 승인 필요한 취소 요청
            logger.info("수강 시작일 후 취소 요청 (enrollId: {}). 관리자 승인 대기.", enrollId);
            enroll.setOriginalPayStatusBeforeCancel(enroll.getPayStatus());
            enroll.setStatus("CANCELED_REQ"); // 상태 변경 (예시)
            enroll.setPayStatus("REFUND_REQUESTED"); // 환불 요청 상태로 변경
            enroll.setCancelStatus(Enroll.CancelStatusType.REQ);
            enroll.setCancelReason(reason);
            // 환불액 계산 등은 관리자 승인 시점으로 이동
        }
        enrollRepository.save(enroll);
    }

    @Override
    @Transactional
    public void approveEnrollmentCancellationAdmin(Long enrollId, Integer manualUsedDays) { // refundPct 파라미터 대신 manualUsedDays 사용
        Enroll enroll = enrollRepository.findById(enrollId)
            .orElseThrow(() -> new ResourceNotFoundException("Enrollment not found with ID: " + enrollId, ErrorCode.ENROLLMENT_NOT_FOUND));

        // 관리자만 이 로직을 호출한다고 가정, 또는 역할 검증 필요
        // if (!"ADMIN_ROLE_CHECK") { throw new BusinessRuleException(ErrorCode.ACCESS_DENIED); }

        if (enroll.getCancelStatus() != Enroll.CancelStatusType.REQ && enroll.getCancelStatus() != Enroll.CancelStatusType.PENDING) {
             throw new BusinessRuleException(ErrorCode.ENROLLMENT_CANCELLATION_NOT_ALLOWED, 
                "취소 요청 상태가 아니거나 이미 처리된 건입니다. 현재 상태: " + enroll.getCancelStatus());
        }
        
        Lesson lesson = enroll.getLesson();
        Payment payment = paymentRepository.findByEnroll_EnrollId(enrollId)
            .orElseThrow(() -> new ResourceNotFoundException("Payment record not found for enrollment ID: " + enrollId, ErrorCode.PAYMENT_INFO_NOT_FOUND));

        // 결제 시 강습료와 사물함 요금을 Payment 엔티티에 분리 저장했다고 가정 (payment.getLessonAmount(), payment.getLockerAmount())
        // 이것이 안되어 있다면, enroll.getLesson().getPrice() 와 payment.getPaidAmt()를 통해 역산 필요
        int paidLessonAmount = Optional.ofNullable(payment.getLessonAmount()).orElse(lesson.getPrice()); // 예시
        int paidLockerAmount = Optional.ofNullable(payment.getLockerAmount()).orElse(0); // 예시: payment에 lockerAmount 필드 없으면 0
        if (payment.getLessonAmount() == null || payment.getLockerAmount() == null) {
             // 실제로는 payment.getPaidAmt()와 lesson.getPrice(), 그리고 enroll.usesLocker()로 분리해야 함
            if (enroll.isUsesLocker() && payment.getPaidAmt() != null && payment.getPaidAmt() > lesson.getPrice()) {
                 paidLessonAmount = lesson.getPrice();
                 paidLockerAmount = payment.getPaidAmt() - lesson.getPrice();
            } else {
                 paidLessonAmount = Optional.ofNullable(payment.getPaidAmt()).orElse(0);
                 paidLockerAmount = 0;
            }
        }


        boolean lockerWasUsed = enroll.isUsesLocker() || enroll.isLockerAllocated(); // 결제 시점의 usesLocker와 실제 할당 여부 모두 고려
        
        BigDecimal finalRefundAmountBigDecimal;
        LocalDate today = LocalDate.now(); // 관리자 승인일 기준
        LocalDate lessonStartDate = lesson.getStartDate();

        if (lessonStartDate == null) {
            throw new BusinessRuleException(ErrorCode.LESSON_NOT_FOUND, "강습 시작일 정보가 없습니다.");
        }

        // 사용일수 계산: 관리자가 입력한 manualUsedDays 우선, 없으면 자동 계산
        long daysUsed;
        if (manualUsedDays != null && manualUsedDays >= 0) {
            daysUsed = manualUsedDays;
            enroll.setDaysUsedForRefund(manualUsedDays);
        } else {
            // 자동계산: 취소 요청일 또는 오늘까지 사용으로 간주 (정책에 따라 기준일 선택)
            // 여기서는 관리자 승인일(today)까지 사용으로 간주
            daysUsed = ChronoUnit.DAYS.between(lessonStartDate, today) + 1;
            if (daysUsed < 0) daysUsed = 0; // 강습 시작 전인데 이 로직을 타는 경우 방지 (실제로는 request 단계에서 분기)
            enroll.setDaysUsedForRefund((int)daysUsed);
        }
        
        // 수강료 환불분 계산
        BigDecimal lessonPaidDecimal = BigDecimal.valueOf(paidLessonAmount);
        BigDecimal lessonUsageDeduction = LESSON_DAILY_RATE.multiply(BigDecimal.valueOf(daysUsed));
        BigDecimal lessonPenalty = lessonPaidDecimal.multiply(PENALTY_RATE);
        enroll.setPenaltyAmountLesson(lessonPenalty.intValue());
        BigDecimal lessonRefundable = lessonPaidDecimal.subtract(lessonUsageDeduction).subtract(lessonPenalty);
        if (lessonRefundable.compareTo(BigDecimal.ZERO) < 0) {
            lessonRefundable = BigDecimal.ZERO;
        }

        finalRefundAmountBigDecimal = lessonRefundable;

        // 사물함료 환불분 계산 (사물함을 사용했고, 사물함 요금이 있었던 경우)
        if (lockerWasUsed && paidLockerAmount > 0) {
            BigDecimal lockerPaidDecimal = BigDecimal.valueOf(paidLockerAmount);
            BigDecimal lockerUsageDeduction = LOCKER_DAILY_RATE.multiply(BigDecimal.valueOf(daysUsed));
            BigDecimal lockerPenalty = lockerPaidDecimal.multiply(PENALTY_RATE);
            enroll.setPenaltyAmountLocker(lockerPenalty.intValue());
            BigDecimal lockerRefundable = lockerPaidDecimal.subtract(lockerUsageDeduction).subtract(lockerPenalty);
            if (lockerRefundable.compareTo(BigDecimal.ZERO) < 0) {
                lockerRefundable = BigDecimal.ZERO;
            }
            finalRefundAmountBigDecimal = finalRefundAmountBigDecimal.add(lockerRefundable);
        } else {
            enroll.setPenaltyAmountLocker(0);
        }
        
        enroll.setCalculatedRefundAmount(finalRefundAmountBigDecimal.intValue()); // 관리자 수정 전 시스템 계산 환불액 (또는 최종 환불액으로 사용)

        int finalRefundAmount = finalRefundAmountBigDecimal.intValue();
        int totalPaidAmount = payment.getPaidAmt() != null ? payment.getPaidAmt() : 0;

        if (finalRefundAmount > totalPaidAmount) {
            finalRefundAmount = totalPaidAmount;
        }
        if (finalRefundAmount < 0) {
            finalRefundAmount = 0;
        }
        enroll.setRefundAmount(finalRefundAmount); // 최종 환불액 설정

        // PG사 환불 연동
        if (finalRefundAmount > 0) {
            String kispgTid = payment.getTid();
            if (kispgTid == null || kispgTid.trim().isEmpty()) {
                // TID 없으면 PG 환불 불가, 관리자에게 알리고 수동 처리 유도 또는 다른 정책
                logger.error("KISPG TID가 없어 자동 환불 불가 (enrollId: {}). 관리자 확인 필요.", enrollId);
                enroll.setCancelStatus(CancelStatusType.PENDING); // PG 환불 실패 시 PENDING으로 두고 관리자 개입 유도
                enroll.setCancelReason((enroll.getCancelReason() == null ? "" : enroll.getCancelReason()) + " [시스템: PG TID 없음]");
                // throw new BusinessRuleException(ErrorCode.PAYMENT_INFO_NOT_FOUND, "KISPG 거래 ID(tid)가 없어 환불을 진행할 수 없습니다.");
            } else {
                // boolean pgRefundSuccess = kispgService.requestPartialRefund(kispgTid, finalRefundAmount, "관리자 승인 취소");
                // if (!pgRefundSuccess) {
                //     logger.error("KISPG 부분 환불 실패 (enrollId: {}, tid: {}, amount: {})", enrollId, kispgTid, finalRefundAmount);
                //     enroll.setCancelStatus(CancelStatusType.PENDING); // PG 환불 실패 시 PENDING
                //     enroll.setCancelReason((enroll.getCancelReason() == null ? "" : enroll.getCancelReason()) + " [시스템: PG 환불 실패]");
                //     // throw new BusinessRuleException(ErrorCode.PAYMENT_REFUND_FAILED, "KISPG 환불 처리 중 오류가 발생했습니다.");
                // } else {
                //     logger.info("KISPG 부분 환불 성공 (enrollId: {}, tid: {}, amount: {})", enrollId, kispgTid, finalRefundAmount);
                //     payment.setRefundedAmt((payment.getRefundedAmt() == null ? 0 : payment.getRefundedAmt()) + finalRefundAmount);
                //     payment.setRefundDt(LocalDateTime.now());
                //     if (payment.getRefundedAmt() >= totalPaidAmount) {
                //         payment.setStatus("CANCELED"); 
                //         enroll.setPayStatus("REFUNDED");
                //     } else if (payment.getRefundedAmt() > 0) { 
                //         payment.setStatus("PARTIAL_REFUNDED");
                //         enroll.setPayStatus("PARTIALLY_REFUNDED");
                //     }
                //     enroll.setCancelStatus(CancelStatusType.APPROVED);
                // }
                // --- 임시 KISPG 연동 성공 처리 ---
                logger.info("KISPG 부분 환불 성공 (임시) (enrollId: {}, tid: {}, amount: {})", enrollId, kispgTid, finalRefundAmount);
                payment.setRefundedAmt((payment.getRefundedAmt() == null ? 0 : payment.getRefundedAmt()) + finalRefundAmount);
                payment.setRefundDt(LocalDateTime.now());
                if (payment.getRefundedAmt() >= totalPaidAmount) {
                    payment.setStatus("CANCELED"); 
                    enroll.setPayStatus("REFUNDED");
                } else if (payment.getRefundedAmt() > 0) { 
                    payment.setStatus("PARTIAL_REFUNDED");
                    enroll.setPayStatus("PARTIALLY_REFUNDED");
                }
                enroll.setCancelStatus(CancelStatusType.APPROVED);
                // --- 임시 KISPG 연동 성공 처리 끝 ---
            }
        } else { // 환불 금액이 0원인 경우
            enroll.setPayStatus("REFUNDED"); // 또는 CANCELED_NO_REFUND 등 상태 정의 필요
            enroll.setCancelStatus(CancelStatusType.APPROVED);
            payment.setStatus("CANCELED"); // 환불액이 0이어도 결제 자체는 취소된 것으로 볼 수 있음
        }
        
        enroll.setCancelApprovedAt(LocalDateTime.now()); // 취소 처리(승인) 시각
        enroll.setUpdatedBy("ADMIN"); // 또는 현재 로그인한 관리자 ID

        if (lockerWasUsed && enroll.isLockerAllocated()) { // 취소 승인 시점에 실제 할당되어 있었다면 회수
             String userGender = enroll.getUser().getGender();
            if (userGender != null && !userGender.trim().isEmpty()) {
                lockerService.decrementUsedQuantity(userGender.toUpperCase());
            }
        }
        enroll.setUsesLocker(false); 
        enroll.setLockerAllocated(false); 
        enroll.setLockerPgToken(null);
        
        enrollRepository.save(enroll);
        if (payment != null) { // payment가 null일 가능성은 적지만 방어 코드
            paymentRepository.save(payment);
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

    @Override
    @Transactional // 이 메소드는 DB 상태를 변경하지 않지만, 연관된 엔티티들을 읽어야 하므로 트랜잭션 컨텍스트가 필요할 수 있습니다.
    public BigDecimal calculateDisplayRefundAmount(Long enrollId) {
        Enroll enroll = enrollRepository.findById(enrollId)
            .orElseThrow(() -> new ResourceNotFoundException("Enrollment not found with ID: " + enrollId, ErrorCode.ENROLLMENT_NOT_FOUND));
        
        Lesson lesson = enroll.getLesson();
        if (lesson == null) {
             throw new ResourceNotFoundException("Lesson not found for enrollment ID: " + enrollId, ErrorCode.LESSON_NOT_FOUND);
        }
        Payment payment = paymentRepository.findByEnroll_EnrollId(enrollId)
            .orElseThrow(() -> new ResourceNotFoundException("Payment record not found for enrollment ID: " + enrollId, ErrorCode.PAYMENT_INFO_NOT_FOUND));

        // 결제 시 강습료와 사물함 요금 분리 (approve 메소드와 동일한 로직 사용)
        int paidLessonAmount = Optional.ofNullable(payment.getLessonAmount()).orElse(lesson.getPrice());
        int paidLockerAmount = 0;
        if (payment.getLessonAmount() == null || payment.getLockerAmount() == null) {
            if (enroll.isUsesLocker() && payment.getPaidAmt() != null && payment.getPaidAmt() > lesson.getPrice()) {
                 paidLessonAmount = lesson.getPrice();
                 paidLockerAmount = payment.getPaidAmt() - lesson.getPrice();
            } else {
                 paidLessonAmount = Optional.ofNullable(payment.getPaidAmt()).orElse(0);
                 // paidLockerAmount는 0으로 유지
            }
        } else {
            paidLockerAmount = Optional.ofNullable(payment.getLockerAmount()).orElse(0);
        }
        
        boolean lockerWasUsed = enroll.isUsesLocker() || enroll.isLockerAllocated();
        
        BigDecimal finalRefundAmountBigDecimal;
        LocalDate today = LocalDate.now(); // 항상 현재 시점 기준
        LocalDate lessonStartDate = lesson.getStartDate();

        if (lessonStartDate == null) {
            return BigDecimal.ZERO; 
        }

        // 수강 시작일 전이면 총 결제액 반환 (PG 수수료 등은 별도 정책)
        if (today.isBefore(lessonStartDate)) {
            return BigDecimal.valueOf(Optional.ofNullable(payment.getPaidAmt()).orElse(0));
        }
        
        // 수강 시작일 후: 사용일수 자동 계산 (approve와 동일하게 관리자 승인일 대신 현재일 사용)
        long daysUsed = ChronoUnit.DAYS.between(lessonStartDate, today) + 1;
        if (daysUsed < 0) daysUsed = 0; 

        // 수강료 환불분 계산
        BigDecimal lessonPaidDecimal = BigDecimal.valueOf(paidLessonAmount);
        BigDecimal lessonUsageDeduction = LESSON_DAILY_RATE.multiply(BigDecimal.valueOf(daysUsed));
        BigDecimal lessonPenalty = lessonPaidDecimal.multiply(PENALTY_RATE);
        BigDecimal lessonRefundable = lessonPaidDecimal.subtract(lessonUsageDeduction).subtract(lessonPenalty);
        if (lessonRefundable.compareTo(BigDecimal.ZERO) < 0) {
            lessonRefundable = BigDecimal.ZERO;
        }
        finalRefundAmountBigDecimal = lessonRefundable;

        // 사물함료 환불분 계산
        if (lockerWasUsed && paidLockerAmount > 0) {
            BigDecimal lockerPaidDecimal = BigDecimal.valueOf(paidLockerAmount);
            BigDecimal lockerUsageDeduction = LOCKER_DAILY_RATE.multiply(BigDecimal.valueOf(daysUsed));
            BigDecimal lockerPenalty = lockerPaidDecimal.multiply(PENALTY_RATE);
            BigDecimal lockerRefundable = lockerPaidDecimal.subtract(lockerUsageDeduction).subtract(lockerPenalty);
            if (lockerRefundable.compareTo(BigDecimal.ZERO) < 0) {
                lockerRefundable = BigDecimal.ZERO;
            }
            finalRefundAmountBigDecimal = finalRefundAmountBigDecimal.add(lockerRefundable);
        }

        int finalRefundAmount = finalRefundAmountBigDecimal.intValue();
        int totalPaidAmount = payment.getPaidAmt() != null ? payment.getPaidAmt() : 0;

        if (finalRefundAmount > totalPaidAmount) {
            finalRefundAmount = totalPaidAmount;
        }
        if (finalRefundAmount < 0) {
            finalRefundAmount = 0;
        }
        return BigDecimal.valueOf(finalRefundAmount);
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
            // Format period as "YYYY-MM-DD ~ YYYY-MM-DD"
            String period = null;
            if (lesson.getStartDate() != null && lesson.getEndDate() != null) {
                period = lesson.getStartDate().toString() + " ~ " + lesson.getEndDate().toString();
            }

            lessonDetails = EnrollDto.LessonDetails.builder()
                    .title(lesson.getTitle())
                    .period(period)
                    .time(lesson.getLessonTime()) // e.g., "(월,화,수,목,금) 오전 07:00 ~ 07:50"
                    .price(BigDecimal.valueOf(lesson.getPrice()))
                    .build();
        }

        // Calculate renewal window: 18th-22nd of each month
        EnrollDto.RenewalWindow renewalWindow = null;
        if (lesson != null && lesson.getEndDate() != null) {
            // For a lesson ending in month X, renewal window is 18th-22nd of month X
            LocalDate lessonEndDate = lesson.getEndDate();
            int year = lessonEndDate.getYear();
            int month = lessonEndDate.getMonthValue();
            
            LocalDate renewalStart = LocalDate.of(year, month, 18);
            LocalDate renewalEnd = LocalDate.of(year, month, 22);
            LocalDate now = LocalDate.now();
            
            boolean isRenewalOpen = !now.isBefore(renewalStart) && !now.isAfter(renewalEnd);
            
            renewalWindow = EnrollDto.RenewalWindow.builder()
                    .isOpen(isRenewalOpen)
                    .open(renewalStart.atStartOfDay().atOffset(ZoneOffset.UTC))
                    .close(renewalEnd.atTime(23, 59, 59).atOffset(ZoneOffset.UTC))
                    .build();
        }

        // Calculate canAttemptPayment: UNPAID status and not expired
        // 마이페이지에서는 직접 결제 불가 - 재수강 버튼을 통해서만 결제 페이지 이동
        boolean canAttemptPayment = false; // 항상 false로 설정
        
        // Set paymentPageUrl if payment can be attempted
        // 마이페이지에서는 결제 URL 제공하지 않음
        String paymentPageUrl = null; // 항상 null로 설정

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
            .orElseThrow(() -> new ResourceNotFoundException("재수강 대상 강좌를 찾을 수 없습니다 (ID: " + renewalRequestDto.getLessonId() + ")", ErrorCode.LESSON_NOT_FOUND));
        
        if (lesson.getStatus() != Lesson.LessonStatus.OPEN) {
            throw new BusinessRuleException(ErrorCode.LESSON_NOT_OPEN_FOR_ENROLLMENT, "재수강 가능한 강습이 아닙니다. 현재 상태: " + lesson.getStatus());
        }

        long paidEnrollments = enrollRepository.countByLessonLessonIdAndPayStatus(lesson.getLessonId(), "PAID");
        long unpaidExpiringEnrollments = enrollRepository.countByLessonLessonIdAndStatusAndPayStatusAndExpireDtAfter(lesson.getLessonId(), "APPLIED", "UNPAID", LocalDateTime.now());
        long availableSlots = lesson.getCapacity() - paidEnrollments - unpaidExpiringEnrollments;

        if (availableSlots <= 0) {
            throw new BusinessRuleException(ErrorCode.PAYMENT_PAGE_SLOT_UNAVAILABLE, 
                "재수강 정원이 마감되었습니다. 현재 정원: " + lesson.getCapacity() + ", 결제완료: " + paidEnrollments + ", 결제대기: " + unpaidExpiringEnrollments);
        }

        Enroll.EnrollBuilder newEnrollBuilder = Enroll.builder()
            .user(user)
            .lesson(lesson)
            .status("APPLIED").payStatus("UNPAID").expireDt(LocalDateTime.now().plusMinutes(5))
            .renewalFlag(true).cancelStatus(CancelStatusType.NONE)
            .usesLocker(renewalRequestDto.isWantsLocker()) 
            .createdBy(user.getName()) 
            .updatedBy(user.getName()) 
            .createdIp("UNKNOWN_IP_RENEWAL") 
            .updatedIp("UNKNOWN_IP_RENEWAL");

        Enroll newEnroll = newEnrollBuilder.build();
        try {
            enrollRepository.save(newEnroll);
            
            long finalPaidCount = enrollRepository.countByLessonLessonIdAndPayStatus(lesson.getLessonId(), "PAID");
            long finalUnpaidCount = enrollRepository.countByLessonLessonIdAndStatusAndPayStatus(lesson.getLessonId(), "APPLIED", "UNPAID");
            if ((finalPaidCount + finalUnpaidCount) >= lesson.getCapacity() && lesson.getStatus() == Lesson.LessonStatus.OPEN) {
                lesson.updateStatus(Lesson.LessonStatus.CLOSED);
                lessonRepository.save(lesson);
            }
            
            return EnrollInitiationResponseDto.builder()
                    .enrollId(newEnroll.getEnrollId())
                    .lessonId(lesson.getLessonId())
                    .paymentPageUrl("/payment/process?enroll_id=" + newEnroll.getEnrollId())
                    .paymentExpiresAt(newEnroll.getExpireDt().atOffset(ZoneOffset.UTC))
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
        if ("PAID".equalsIgnoreCase(status) || "UNPAID".equalsIgnoreCase(status) || "EXPIRED".equalsIgnoreCase(status) || "REFUNDED".equalsIgnoreCase(status) || "PAYMENT_TIMEOUT".equalsIgnoreCase(status) || "PARTIALLY_REFUNDED".equalsIgnoreCase(status)) {
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