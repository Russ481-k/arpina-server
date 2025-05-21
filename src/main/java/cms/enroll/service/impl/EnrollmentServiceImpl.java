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

        // Locker logic starts here
        if (Boolean.TRUE.equals(checkoutRequest.getWantsLocker())) {
            if (user.getGender() == null || user.getGender().trim().isEmpty()) {
                throw new BusinessRuleException(ErrorCode.USER_GENDER_REQUIRED_FOR_LOCKER, "라커를 신청하려면 사용자의 성별 정보가 필요합니다.");
            }
            String userGender = user.getGender().toUpperCase();
            long lessonLockerCapacityForGender;
            if ("MALE".equals(userGender)) {
                lessonLockerCapacityForGender = lesson.getMaleLockerCap();
            } else if ("FEMALE".equals(userGender)) {
                lessonLockerCapacityForGender = lesson.getFemaleLockerCap();
            } else {
                throw new BusinessRuleException(ErrorCode.INVALID_USER_GENDER, "알 수 없는 사용자 성별입니다: " + user.getGender());
            }

            long currentlyUsedLockers = enrollRepository.countByLessonLessonIdAndUserGenderAndUsesLockerTrueAndPayStatusInAndExpireDtAfter(
                lesson.getLessonId(), userGender, List.of("UNPAID"), LocalDateTime.now()
            ) + enrollRepository.countByLessonLessonIdAndUserGenderAndUsesLockerTrueAndPayStatusIn(
                lesson.getLessonId(), userGender, List.of("PAID")
            );

            if (currentlyUsedLockers >= lessonLockerCapacityForGender) {
                throw new BusinessRuleException(ErrorCode.LESSON_LOCKER_CAPACITY_EXCEEDED_FOR_GENDER, user.getGender() + " 성별의 강습 사물함이 모두 사용 중입니다. 다른 강습을 이용하시거나 라커 없이 신청해주세요.");
            }
            enroll.setUsesLocker(true);
        } else {
            enroll.setUsesLocker(false);
        }
        enrollRepository.save(enroll); // Save changes to enroll.usesLocker
        // Locker logic ends here

        // TODO: Adjust amount if locker has a fee
        // BigDecimal finalAmount = BigDecimal.valueOf(lesson.getPrice());
        // if (enroll.isUsesLocker() && lesson.getLockerFee() > 0) { // Assuming lesson might have a lockerFee property
        //    finalAmount = finalAmount.add(BigDecimal.valueOf(lesson.getLockerFee()));
        // }

        CheckoutDto checkoutDto = new CheckoutDto();
        checkoutDto.setMerchantUid("enroll_" + enroll.getEnrollId() + "_" + System.currentTimeMillis());
        // checkoutDto.setAmount(finalAmount); // Use finalAmount after considering locker fee
        checkoutDto.setAmount(BigDecimal.valueOf(lesson.getPrice())); // Placeholder: use lesson price for now
        checkoutDto.setLessonTitle(lesson.getTitle());
        checkoutDto.setUserName(user.getName());
        checkoutDto.setPgProvider("html5_inicis");
        return checkoutDto;
    }

    @Override
    @Transactional
    public void processPayment(User user, Long enrollId, String pgToken) {
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

        // TODO: PG 실제 검증 로직 필요. 검증 후 actualPaidAmount 설정.
        // 현재는 PG 검증 로직이 없으므로, lesson.getPrice()를 그대로 사용한다고 가정합니다.
        // 실제 PG 연동 시에는 PG로부터 받은 결제 금액 정보를 사용해야 합니다.
        Integer expectedAmount = lesson.getPrice();
        Integer actualPaidAmount = lesson.getPrice(); // 임시로 강좌 가격을 실제 결제 금액으로 설정. PG 검증 후 이 값을 채워야 함.

        // PG 검증 서비스 호출 및 금액 확인 (예시)
        // boolean isPaymentVerified = paymentGatewayService.verifyPayment(pgToken, expectedAmount);
        // if (!isPaymentVerified) {
        //     throw new BusinessRuleException("PG사를 통한 결제 검증에 실패했습니다.", ErrorCode.PAYMENT_PROCESSING_FAILED);
        // }
        // 실제 결제된 금액을 PG로부터 받아와야 한다면:
        // actualPaidAmount = paymentGatewayService.getPaidAmount(pgToken);

        if (!expectedAmount.equals(actualPaidAmount)) {
            // PG 검증 후 금액이 다르다면, 여기서 처리
            // (예: 부분 결제, 할인 적용 등으로 다를 수 있으나, 여기서는 일치해야 한다고 가정)
            throw new BusinessRuleException("결제 요청 금액과 실제 결제 금액이 일치하지 않습니다. 예상: " + expectedAmount + ", 실제: " + actualPaidAmount, ErrorCode.PAYMENT_AMOUNT_MISMATCH);
        }
        
        try {
            enroll.setPayStatus("PAID");
            if (!"APPLIED".equals(enroll.getStatus())) { 
                 enroll.setStatus("APPLIED");
            }
            enrollRepository.save(enroll);

            Payment payment = Payment.builder()
                    .enroll(enroll)
                    .amount(actualPaidAmount)
                    .paidAt(LocalDateTime.now())
                    .status("SUCCESS") // PG 검증 성공 후 상태
                    .pgProvider("html5_inicis") // Example
                    .pgToken(pgToken)
                    .merchantUid("enroll_" + enroll.getEnrollId() + "_" + System.currentTimeMillis()) // 실제로는 checkout시 생성된 merchantUid 사용 고려
                    .build();
            paymentRepository.save(payment);
        } catch (Exception e) {
            // 데이터베이스 저장 실패 등 로깅 및 예외 처리
            // logger.error("Payment processing failed after PG verification for enrollId {}: {}", enrollId, e.getMessage(), e);
            // 이 경우 PG에 결제 취소 요청을 보내야 할 수도 있습니다 (롤백 로직).
            throw new BusinessRuleException("결제 정보 저장 중 오류가 발생했습니다.", ErrorCode.PAYMENT_PROCESSING_FAILED);
        }
    }

    @Override
    @Transactional
    public void requestEnrollmentCancellation(User user, Long enrollId, String reason) {
        if (user == null || user.getUuid() == null) {
             throw new BusinessRuleException(ErrorCode.AUTHENTICATION_FAILED, HttpStatus.UNAUTHORIZED);
        }
        Enroll enroll = enrollRepository.findById(enrollId)
            .orElseThrow(() -> new ResourceNotFoundException("수강 신청 정보를 찾을 수 없습니다 (ID: " + enrollId + ")", ErrorCode.ENROLLMENT_NOT_FOUND));
        
        if (!enroll.getUser().getUuid().equals(user.getUuid())) {
            throw new BusinessRuleException(ErrorCode.ACCESS_DENIED, HttpStatus.FORBIDDEN);
        }
        if (enroll.getCancelStatus() != CancelStatusType.NONE) {
            throw new BusinessRuleException("이미 취소 절차가 진행 중이거나 완료/거절된 수강 신청입니다. 현재 취소 상태: " + enroll.getCancelStatus(), ErrorCode.ALREADY_CANCELLED_ENROLLMENT);
        }

        if (enroll.isUsesLocker()) {
            if (user.getGender() == null || user.getGender().trim().isEmpty()) {
                 throw new BusinessRuleException(ErrorCode.USER_GENDER_REQUIRED_FOR_LOCKER);
            } else {
                 try {
                    lockerService.releaseLocker(user.getGender()); 
                 } catch (Exception e) {
                    // logger.warn("Locker release failed for user {} gender {} during enrollment cancellation of enrollId {}: {}", user.getUuid(), user.getGender(), enrollId, e.getMessage());
                    // 라커 반납 실패가 전체 취소 로직을 중단시켜야 하는지에 따라 처리 결정.
                    // 여기서는 로깅 후 계속 진행하거나, 혹은 BusinessRuleException(ErrorCode.LOCKER_RELEASE_FAILED)를 던질 수 있음.
                    // 우선은 로깅만 하고 넘어가는 것으로 가정 (만약 lockerService가 예외를 던지지 않는다면)
                    // 실제 lockerService.releaseLocker가 실패 시 예외를 던진다면 이 try-catch로 잡아서 처리.
                    throw new BusinessRuleException("라커 반납 처리 중 오류가 발생했습니다. 관리자에게 문의해주세요.", ErrorCode.LOCKER_RELEASE_FAILED, e);
                 }
            }
            enroll.setUsesLocker(false); 
        }

        Lesson lesson = enroll.getLesson();
        if (lesson == null) { // 방어 코드
            throw new ResourceNotFoundException("연결된 강좌 정보를 찾을 수 없습니다 (수강신청 ID: " + enrollId + ")", ErrorCode.LESSON_NOT_FOUND);
        }

        boolean lessonStarted = lesson.getStartDate().isBefore(LocalDate.now());
        
        enroll.setCancelReason(reason); // 사유는 공통적으로 먼저 설정

        if (lessonStarted) {
            enroll.setCancelStatus(CancelStatusType.REQ);
            enroll.setCancelReason(reason + " (수업 시작 후 취소 요청)"); // 사유에 정보 추가
            // 수업 시작 후 취소 정책에 따른 추가 로직 (예: 환불 불가, 특정 상태로 변경 등)
            // enroll.setPayStatus("REFUND_PENDING_REVIEW"); // 예시 상태
        } else {
            enroll.setCancelStatus(CancelStatusType.REQ);
            if ("UNPAID".equalsIgnoreCase(enroll.getPayStatus())) {
                enroll.setPayStatus("CANCELED_UNPAID");
                enroll.setStatus("CANCELED"); // 미결제 건은 바로 취소 상태로 변경 가능
            } else if ("PAID".equalsIgnoreCase(enroll.getPayStatus())){
                // 결제된 건은 취소 요청 상태로 두고 관리자 승인 대기
                // enroll.setPayStatus("REFUND_REQUESTED"); // 또는 현재 상태 유지
            } else {
                // 기타 상태 (EXPIRED 등) - 이 경우 취소 요청이 가능한지 정책 검토 필요
                 // throw new BusinessRuleException("현재 결제 상태에서는 취소 요청을 할 수 없습니다: " + enroll.getPayStatus(), ErrorCode.ENROLLMENT_CANCELLATION_NOT_ALLOWED);
            }
        }
        
        try {
            enrollRepository.save(enroll);
        } catch (Exception e) {
            // logger.error("Failed to save enrollment during cancellation request for enrollId {}: {}", enrollId, e.getMessage(), e);
            throw new BusinessRuleException("수강 신청 취소 요청 처리 중 오류가 발생했습니다.", ErrorCode.INTERNAL_SERVER_ERROR); // 더 구체적인 코드가 있다면 사용
        }
    }

    @Override
    @Transactional
    public EnrollDto processRenewal(User user, RenewalRequestDto renewalRequestDto) {
        if (user == null || user.getUuid() == null) {
             throw new BusinessRuleException(ErrorCode.AUTHENTICATION_FAILED, HttpStatus.UNAUTHORIZED);
        }
        Lesson lesson = lessonRepository.findById(renewalRequestDto.getLessonId())
            .orElseThrow(() -> new ResourceNotFoundException("재수강 대상 강좌를 찾을 수 없습니다 (ID: " + renewalRequestDto.getLessonId() + ")", ErrorCode.LESSON_NOT_FOUND));
        
        // TODO: 여기에 추가적인 재수강 자격 검증 로직이 필요할 수 있습니다.
        // 예: 이전 수강 완료 여부, 재수강 가능 기간 등
        // if (!isEligibleForRenewal(user, lesson)) {
        //     throw new BusinessRuleException(ErrorCode.ENROLLMENT_RENEWAL_NOT_ALLOWED);
        // }

        boolean useLockerForRenewal = false;
        if (renewalRequestDto.isWantsLocker()) {
            if (user.getGender() == null || user.getGender().trim().isEmpty()) {
                throw new BusinessRuleException(ErrorCode.USER_GENDER_REQUIRED_FOR_LOCKER);
            }
            try {
                // lockerService.assignLocker가 boolean을 반환하고, 실패 시 false를 반환한다고 가정합니다.
                // 만약 assignLocker가 실패 시 직접 예외를 던진다면, 아래 if문은 필요 없고 catch에서 처리합니다.
                if (!lockerService.assignLocker(user.getGender())) {
                    throw new BusinessRuleException(user.getGender() + " 성별의 사용 가능한 라커가 없습니다 (재등록 시도).", ErrorCode.LOCKER_NOT_AVAILABLE);
                }
                useLockerForRenewal = true;
            } catch (BusinessRuleException bre) { // lockerService.assignLocker가 BusinessRuleException을 던질 경우
                throw bre; // 그대로 다시 던지거나, 필요시 메시지 가공
            } catch (Exception e) { // 그 외 lockerService 내부 예외 (예: DB 오류)
                // logger.error("Error assigning locker during renewal for user {}: {}", user.getUuid(), e.getMessage(), e);
                throw new BusinessRuleException("재수강 시 라커 배정 중 오류가 발생했습니다.", ErrorCode.LOCKER_ASSIGNMENT_FAILED, e);
            }
        }

        Enroll.EnrollBuilder newEnrollBuilder = Enroll.builder()
            .user(user)
            .lesson(lesson)
            .status("APPLIED").payStatus("UNPAID").expireDt(LocalDateTime.now().plusHours(24)) // 결제 만료 시간은 정책에 따라 조절
            .renewalFlag(true).cancelStatus(CancelStatusType.NONE)
            .usesLocker(useLockerForRenewal)
            .createdBy(user.getName()) // 또는 시스템 ID "SYSTEM_RENEWAL"
            .updatedBy(user.getName()) // 또는 시스템 ID
            .createdIp("UNKNOWN_IP_RENEWAL") // DTO에 IP 필드가 있다면 사용
            .updatedIp("UNKNOWN_IP_RENEWAL");

        try {
            Enroll newEnroll = newEnrollBuilder.build();
            enrollRepository.save(newEnroll);
            return convertToMypageEnrollDto(newEnroll);
        } catch (Exception e) {
            // logger.error("Error saving renewed enrollment for user {}: {}", user.getUuid(), e.getMessage(), e);
            // 만약 위에서 라커를 배정했다면, 여기서 롤백(라커 반납) 로직이 필요합니다.
            if (useLockerForRenewal) {
                try {
                    lockerService.releaseLocker(user.getGender()); // GENDER는 user 객체에서 가져와야 함
                } catch (Exception releaseEx) {
                    // logger.error("Failed to release locker during renewal rollback for user {}: {}. Original save error: {}", user.getUuid(), releaseEx.getMessage(), e.getMessage(), releaseEx);
                    // 롤백 실패는 심각한 상황일 수 있으므로, 추가적인 로깅 또는 알림 처리 필요.
                }
            }
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