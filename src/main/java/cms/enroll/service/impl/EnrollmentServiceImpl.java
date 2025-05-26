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
    // private static final BigDecimal LOCKER_DAILY_RATE = new BigDecimal("170"); // 사물함 일일 요금 주석 처리
    // private static final BigDecimal PENALTY_RATE = new BigDecimal("0.10"); // 위약금 비율 주석 처리

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

    /**
     * 환불액 계산을 위한 내부 헬퍼 메소드.
     *
     * @param enroll 환불 대상 수강 정보
     * @param payment 해당 수강에 대한 결제 정보
     * @param manualUsedDaysOverride 관리자가 입력한 사용일수 (우선 적용). null이면 시스템 자동 계산.
     * @param calculationDate 계산 기준일 (일반적으로 관리자 승인일 또는 현재일)
     * @return 계산된 환불 상세 내역 DTO
     */
    private CalculatedRefundDetailsDto calculateRefundInternal(Enroll enroll, Payment payment, Integer manualUsedDaysOverride, LocalDate calculationDate) {
        Lesson lesson = enroll.getLesson();
        if (lesson == null) {
            throw new ResourceNotFoundException("Lesson not found for enrollment ID: " + enroll.getEnrollId(), ErrorCode.LESSON_NOT_FOUND);
        }
        if (payment == null) {
            throw new ResourceNotFoundException("Payment record not found for enrollment ID: " + enroll.getEnrollId(), ErrorCode.PAYMENT_INFO_NOT_FOUND);
        }
        if (lesson.getStartDate() == null) {
             throw new BusinessRuleException(ErrorCode.LESSON_NOT_FOUND, "강습 시작일 정보가 없습니다 (강습 ID: " + lesson.getLessonId() + ")");
        }

        // 1. 결제된 강습료 확정
        int paidLessonAmount = Optional.ofNullable(payment.getLessonAmount()).orElse(lesson.getPrice());
        // 사물함 결제액은 환불 계산에 사용되지 않음
        int paidLockerAmount = 0;
        if (payment.getLessonAmount() == null || payment.getLockerAmount() == null) {
            if (enroll.isUsesLocker() && payment.getPaidAmt() != null && payment.getPaidAmt() > lesson.getPrice()) {
                // paidLessonAmount = lesson.getPrice(); // This was a simplification.
                                                     // If discounts apply, paidLessonAmount should be the discounted lesson fee.
                // For simplicity, assume payment.getLessonAmount() if available is the discounted one.
                // If not, use lesson.getPrice() as non-discounted, or ensure payment.getPaidAmt() reflects total paid.
            } else {
                paidLessonAmount = Optional.ofNullable(payment.getPaidAmt()).orElse(0);
            }
        }
        // Ensure paidLessonAmount reflects the actual amount paid for the lesson part.
        // If payment.getLessonAmount() is not null, it's assumed to be the accurate figure.
        // Otherwise, if only payment.getPaidAmt() is available, and locker was used,
        // paidLessonAmount might need to be deduced if original locker fee was fixed.
        // For new policy, we only care about what was paid for the lesson itself.
        if (payment.getLessonAmount() != null) {
            paidLessonAmount = payment.getLessonAmount();
        } else { // Fallback if lessonAmount is not in payment, assume paidAmt is only for lesson if no locker, or deduce
            if (enroll.isUsesLocker() && payment.getPaidAmt() != null) {
                 // This part is tricky without knowing the exact locker fee at time of payment or if it was fixed.
                 // For the new policy "locker fee not refundable", we only need the part paid for the lesson.
                 // Best if payment.lessonAmount clearly stores this. If not, use full price as a fallback for lesson portion.
                 // Or, if payment.getPaidAmt() includes locker, and we need lesson portion only:
                 // paidLessonAmount = payment.getPaidAmt() - (original_fixed_locker_fee_if_known);
                 // For now, if payment.lessonAmount is null, we'll rely on lesson.getPrice() or payment.getPaidAmt() if no locker.
                if (payment.getPaidAmt() > lesson.getPrice() && defaultLockerFee > 0) { // Assuming defaultLockerFee was the one used
                    paidLessonAmount = payment.getPaidAmt() - defaultLockerFee;
                    if (paidLessonAmount < 0) paidLessonAmount = 0; // Should not happen if paidAmt was correct
        } else {
                    paidLessonAmount = payment.getPaidAmt(); // Assume paidAmt was just for lesson
                }
            } else if (payment.getPaidAmt() != null) {
                paidLessonAmount = payment.getPaidAmt();
            } else {
                paidLessonAmount = 0;
            }
        }


        // 2. 사용일수 계산
        LocalDate lessonStartDate = lesson.getStartDate();
        long systemCalculatedDaysUsed = ChronoUnit.DAYS.between(lessonStartDate, calculationDate) + 1;
        if (systemCalculatedDaysUsed < 0) systemCalculatedDaysUsed = 0; // 시작일 전이면 0일

        int effectiveDaysUsed;
        if (manualUsedDaysOverride != null && manualUsedDaysOverride >= 0) {
            effectiveDaysUsed = manualUsedDaysOverride;
        } else {
            effectiveDaysUsed = (int) systemCalculatedDaysUsed;
        }

        // 3. 강습료 관련 계산
        BigDecimal paidLessonAmountDecimal = BigDecimal.valueOf(paidLessonAmount);     // 실제 결제된 강습료

        BigDecimal lessonUsageDeduction = LESSON_DAILY_RATE.multiply(BigDecimal.valueOf(effectiveDaysUsed));
        // BigDecimal lessonPenalty = BigDecimal.ZERO; // 위약금 없음

        BigDecimal lessonRefundable = paidLessonAmountDecimal.subtract(lessonUsageDeduction);
        if (lessonRefundable.compareTo(BigDecimal.ZERO) < 0) {
            lessonRefundable = BigDecimal.ZERO;
        }

        // 4. 사물함료 관련 계산 - 환불 없음
        // BigDecimal lockerUsageDeduction = BigDecimal.ZERO;
        // BigDecimal lockerPenalty = BigDecimal.ZERO;
        // BigDecimal lockerRefundable = BigDecimal.ZERO;


        // 5. 최종 환불액 (강습료 환불액만 해당)
        BigDecimal finalRefundAmountBigDecimal = lessonRefundable;
        int totalPaidForLesson = paidLessonAmount; // Compare against what was paid for the lesson

        if (finalRefundAmountBigDecimal.compareTo(BigDecimal.valueOf(totalPaidForLesson)) > 0) { //Should not exceed what was paid for the lesson
            finalRefundAmountBigDecimal = BigDecimal.valueOf(totalPaidForLesson);
        }
        if (finalRefundAmountBigDecimal.compareTo(BigDecimal.ZERO) < 0) {
            finalRefundAmountBigDecimal = BigDecimal.ZERO;
        }
        
        return CalculatedRefundDetailsDto.builder()
                .systemCalculatedUsedDays((int) systemCalculatedDaysUsed)
                .manualUsedDays(manualUsedDaysOverride)
                .effectiveUsedDays(effectiveDaysUsed)
                .originalLessonPrice(BigDecimal.valueOf(lesson.getPrice())) // DTO에 원래 강습 정가는 유지
                .paidLessonAmount(paidLessonAmountDecimal)
                .paidLockerAmount(BigDecimal.valueOf(payment.getLockerAmount() != null ? payment.getLockerAmount() : 0)) // DTO에는 원래 사물함 결제액 표시
                .lessonUsageDeduction(lessonUsageDeduction)
    
                .finalRefundAmount(finalRefundAmountBigDecimal)
                .build();
    }

    @Override
    @Transactional
    public void approveEnrollmentCancellationAdmin(Long enrollId, Integer manualUsedDaysFromRequest) {
        Enroll enroll = enrollRepository.findById(enrollId)
            .orElseThrow(() -> new ResourceNotFoundException("Enrollment not found with ID: " + enrollId, ErrorCode.ENROLLMENT_NOT_FOUND));

        if (enroll.getCancelStatus() != Enroll.CancelStatusType.REQ && enroll.getCancelStatus() != Enroll.CancelStatusType.PENDING) {
             throw new BusinessRuleException(ErrorCode.ENROLLMENT_CANCELLATION_NOT_ALLOWED,
                "취소 요청 상태가 아니거나 이미 처리된 건입니다. 현재 상태: " + enroll.getCancelStatus());
        }

        Payment payment = paymentRepository.findByEnroll_EnrollId(enrollId)
            .orElseThrow(() -> new ResourceNotFoundException("Payment record not found for enrollment ID: " + enrollId, ErrorCode.PAYMENT_INFO_NOT_FOUND));

        CalculatedRefundDetailsDto refundDetails = calculateRefundInternal(enroll, payment, manualUsedDaysFromRequest, LocalDate.now());

        enroll.setDaysUsedForRefund(refundDetails.getEffectiveUsedDays());
        enroll.setRefundAmount(refundDetails.getFinalRefundAmount().intValue()); // 최종 환불액 저장 (setFinalRefundAmount -> setRefundAmount)

        // PG사 환불 연동 로직 (기존과 유사하게 진행, refundDetails.getFinalRefundAmount() 사용)
        int finalRefundAmountForPg = refundDetails.getFinalRefundAmount().intValue();
        int totalPaidAmount = payment.getPaidAmt() != null ? payment.getPaidAmt() : 0;

        if (finalRefundAmountForPg > 0) {
            String kispgTid = payment.getTid();
            if (kispgTid == null || kispgTid.trim().isEmpty()) {
                logger.error("KISPG TID가 없어 자동 환불 불가 (enrollId: {}). 관리자 확인 필요.", enrollId);
                enroll.setCancelStatus(CancelStatusType.PENDING);
                enroll.setCancelReason((enroll.getCancelReason() == null ? "" : enroll.getCancelReason()) + " [시스템: PG TID 없음]");
            } else {
                // --- 임시 KISPG 연동 성공 처리 ---
                logger.info("KISPG 부분 환불 성공 (임시) (enrollId: {}, tid: {}, amount: {})", enrollId, kispgTid, finalRefundAmountForPg);
                payment.setRefundedAmt((payment.getRefundedAmt() == null ? 0 : payment.getRefundedAmt()) + finalRefundAmountForPg);
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
            enroll.setPayStatus("REFUNDED"); 
            enroll.setCancelStatus(CancelStatusType.APPROVED);
            if (payment != null) payment.setStatus("CANCELED");
        }

        enroll.setCancelApprovedAt(LocalDateTime.now());
        enroll.setUpdatedBy("ADMIN"); // 또는 현재 로그인한 관리자 ID

        boolean lockerWasAllocated = enroll.isLockerAllocated(); // 취소 승인 시점에 실제 할당되어 있었다면 회수
        if (lockerWasAllocated) {
             String userGender = enroll.getUser().getGender();
            if (userGender != null && !userGender.trim().isEmpty()) {
                lockerService.decrementUsedQuantity(userGender.toUpperCase());
            }
        }
        enroll.setUsesLocker(false);
        enroll.setLockerAllocated(false);
        enroll.setLockerPgToken(null);

        enrollRepository.save(enroll);
        if (payment != null) {
            paymentRepository.save(payment);
        }
    }

    @Override
    @Transactional(readOnly = true) // DB 변경 없음
    public CalculatedRefundDetailsDto getRefundPreview(Long enrollId, Integer manualUsedDaysPreview) {
        Enroll enroll = enrollRepository.findById(enrollId)
            .orElseThrow(() -> new ResourceNotFoundException("Enrollment not found with ID: " + enrollId, ErrorCode.ENROLLMENT_NOT_FOUND));
        Payment payment = paymentRepository.findByEnroll_EnrollId(enrollId)
            .orElseThrow(() -> new ResourceNotFoundException("Payment record not found for enrollment ID: " + enrollId, ErrorCode.PAYMENT_INFO_NOT_FOUND));

        // 헬퍼 메서드를 사용하여 환불 상세 내역 계산 (계산 기준일: 오늘)
        // manualUsedDaysPreview는 관리자가 화면에서 입력해본 값
        return calculateRefundInternal(enroll, payment, manualUsedDaysPreview, LocalDate.now());
    }

    @Override
    @Transactional(readOnly = true)
    public BigDecimal calculateDisplayRefundAmount(Long enrollId) {
        // enroll.getDaysUsedForRefund()는 DB에 저장된 관리자 최종 입력일 수 있음.
        // 또는 null이면 시스템 계산일 사용
        Enroll enroll = enrollRepository.findById(enrollId)
            .orElseThrow(() -> new ResourceNotFoundException("Enrollment not found with ID: " + enrollId, ErrorCode.ENROLLMENT_NOT_FOUND));
        
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

        // Calculate renewal window
        EnrollDto.RenewalWindow renewalWindow = null;
        LocalDate today = LocalDate.now();

        // Renewal is for lessons starting next month, window is 20th-25th of current month.
        if (lesson != null && lesson.getStartDate() != null) {
            LocalDate lessonStartDate = lesson.getStartDate();
            YearMonth currentMonth = YearMonth.from(today);
            YearMonth lessonStartMonth = YearMonth.from(lessonStartDate);

            // Check if the lesson is for next month
            if (lessonStartMonth.equals(currentMonth.plusMonths(1))) {
                LocalDate renewalStart = LocalDate.of(today.getYear(), today.getMonth(), 20);
                LocalDate renewalEnd = LocalDate.of(today.getYear(), today.getMonth(), 25);
                
                boolean isRenewalOpen = !today.isBefore(renewalStart) && !today.isAfter(renewalEnd);
                
                renewalWindow = EnrollDto.RenewalWindow.builder()
                        .isOpen(isRenewalOpen)
                        .open(renewalStart.atStartOfDay().atOffset(ZoneOffset.UTC))
                        .close(renewalEnd.atTime(23, 59, 59).atOffset(ZoneOffset.UTC))
                        .build();
            }
        }
        // If it's not a lesson for next month, renewalWindow remains null (or an empty/closed window can be set if preferred)

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
        
        // Check registration window for renewal: 20th-25th of current month for next month's lesson
        LocalDate today = LocalDate.now();
        YearMonth currentMonth = YearMonth.from(today);
        YearMonth lessonStartMonth = YearMonth.from(lesson.getStartDate());

        boolean isLessonForNextMonth = lessonStartMonth.equals(currentMonth.plusMonths(1));
        boolean isRenewalWindowActive = today.getDayOfMonth() >= 20 && today.getDayOfMonth() <= 25;

        if (!isLessonForNextMonth || !isRenewalWindowActive) {
            throw new BusinessRuleException(ErrorCode.RENEWAL_PERIOD_INVALID, 
                "재수강 신청 기간이 아닙니다. (다음 달 강습: 현월 20~25일)");
        }

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