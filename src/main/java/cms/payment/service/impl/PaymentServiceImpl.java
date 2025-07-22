package cms.payment.service.impl;

import cms.common.exception.ErrorCode;
import cms.common.exception.ResourceNotFoundException;
import cms.enroll.domain.Enroll;
import cms.enroll.repository.EnrollRepository;
import cms.locker.dto.LockerAvailabilityDto;
import cms.locker.service.LockerService;
import cms.payment.dto.PaymentPageDetailsDto;
import cms.payment.service.PaymentService;
import cms.user.domain.User;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.math.BigDecimal;
import java.time.ZoneOffset;
import cms.common.exception.BusinessRuleException; // 접근 권한 예외를 위해 추가
import java.time.LocalDateTime; // enroll.getExpireDt() 타입 호환용
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import cms.payment.domain.Payment;
import cms.payment.repository.PaymentRepository;
import cms.kispg.service.KispgPaymentService;
import cms.kispg.dto.KispgCancelResponseDto;
import cms.payment.domain.PaymentStatus;
import cms.kispg.dto.KispgNotificationRequest;
import cms.swimming.repository.LessonRepository;
import cms.user.repository.UserRepository;

@Service
@RequiredArgsConstructor
public class PaymentServiceImpl implements PaymentService {

    private final EnrollRepository enrollRepository;
    private final LockerService lockerService; // 새로 만든 LockerService 주입
    private final PaymentRepository paymentRepository;
    private final KispgPaymentService kispgPaymentService;
    private final LessonRepository lessonRepository;
    private final UserRepository userRepository;
    private static final Logger logger = LoggerFactory.getLogger(PaymentServiceImpl.class);

    @Value("${app.locker.fee:5000}") // Default to 5000 if not set
    private int lockerFeeConfig;

    @Override
    @Transactional(readOnly = true)
    public PaymentPageDetailsDto getPaymentPageDetails(Long enrollId, User currentUser) {
        Enroll enroll = enrollRepository.findById(enrollId)
                .orElseThrow(() -> new ResourceNotFoundException("수강 신청 정보를 찾을 수 없습니다 (ID: " + enrollId + ")",
                        ErrorCode.ENROLLMENT_NOT_FOUND));

        // 사용자 권한 검증 (신청자 본인만 접근 가능하도록)
        if (currentUser == null || !enroll.getUser().getUuid().equals(currentUser.getUuid())) {
            throw new BusinessRuleException(ErrorCode.ACCESS_DENIED, "해당 결제 상세 정보에 접근할 권한이 없습니다.");
        }

        // 결제 페이지 접근 유효성 검사 (이미 PAID거나, EXPIRED 된 경우 등)
        if (!"UNPAID".equalsIgnoreCase(enroll.getPayStatus())) {
            throw new BusinessRuleException(ErrorCode.NOT_UNPAID_ENROLLMENT_STATUS,
                    "결제 대기 상태의 수강 신청이 아닙니다. 현재 상태: " + enroll.getPayStatus());
        }
        if (enroll.getExpireDt() == null || enroll.getExpireDt().isBefore(LocalDateTime.now())) { // LocalDateTime으로 변경
            throw new BusinessRuleException(ErrorCode.ENROLLMENT_PAYMENT_EXPIRED, "결제 가능 시간이 만료되었습니다.");
        }

        User user = enroll.getUser();
        String userGender = user.getGender() != null ? user.getGender().toUpperCase() : null;
        BigDecimal lockerFee = BigDecimal.valueOf(lockerFeeConfig);

        PaymentPageDetailsDto.LockerOptionsDto lockerOptionsDto = null;
        if (userGender != null && !userGender.isEmpty()) {
            try {
                LockerAvailabilityDto availability = lockerService.getLockerAvailabilityByGender(userGender);
                lockerOptionsDto = PaymentPageDetailsDto.LockerOptionsDto.builder()
                        .lockerAvailableForUserGender(availability.getAvailableQuantity() > 0)
                        .availableCountForUserGender(availability.getAvailableQuantity())
                        .lockerFee(lockerFee)
                        .build();
            } catch (ResourceNotFoundException e) {
                // 해당 성별의 라커 재고 정보가 없을 수 있으므로, 오류 대신 null 또는 기본값 처리
                lockerOptionsDto = PaymentPageDetailsDto.LockerOptionsDto.builder()
                        .lockerAvailableForUserGender(false)
                        .availableCountForUserGender(0)
                        .lockerFee(lockerFee)
                        .build();
            }
        } else {
            // 사용자의 성별 정보가 없는 경우
            lockerOptionsDto = PaymentPageDetailsDto.LockerOptionsDto.builder()
                    .lockerAvailableForUserGender(false)
                    .availableCountForUserGender(0)
                    .lockerFee(lockerFee)
                    .build();
        }

        // 최종 결제 금액 계산 (기본 강습료 + 사물함 선택 시 요금). 이 로직은 프론트엔드에서도 필요함.
        // 여기서는 기본 강습료만 설정하고, 사물함 선택에 따른 금액 변경은 confirm 시점에 반영하거나, 프론트에서 계산된 값을 받을 수 있음.
        BigDecimal amountToPay = BigDecimal.valueOf(enroll.getLesson().getPrice());

        return PaymentPageDetailsDto.builder()
                .enrollId(enroll.getEnrollId())
                .lessonTitle(enroll.getLesson().getTitle())
                .lessonPrice(BigDecimal.valueOf(enroll.getLesson().getPrice()))
                .userGender(userGender)
                .lockerOptions(lockerOptionsDto)
                .amountToPay(amountToPay) // 기본 강습료
                .paymentDeadline(enroll.getExpireDt().atOffset(ZoneOffset.UTC)) // LocalDateTime to OffsetDateTime
                .build();
    }

    @Override
    @Transactional
    public void confirmPayment(Long enrollId, User currentUser, boolean usesLocker, String pgToken) {
        Enroll enroll = enrollRepository.findById(enrollId)
                .orElseThrow(() -> new ResourceNotFoundException("수강 신청 정보를 찾을 수 없습니다 (ID: " + enrollId + ")",
                        ErrorCode.ENROLLMENT_NOT_FOUND));

        if (currentUser == null || !enroll.getUser().getUuid().equals(currentUser.getUuid())) {
            throw new BusinessRuleException(ErrorCode.ACCESS_DENIED, "해당 결제 처리에 대한 권한이 없습니다.");
        }

        // Check if payment window is expired, but only if not already PAID.
        // If it became PAID via webhook just before this call, allow usesLocker to be
        // updated.
        if (!"PAID".equalsIgnoreCase(enroll.getPayStatus()) && enroll.getExpireDt() != null
                && enroll.getExpireDt().isBefore(LocalDateTime.now())) {
            throw new BusinessRuleException(ErrorCode.ENROLLMENT_PAYMENT_EXPIRED, "결제 가능 시간이 만료되어 처리를 완료할 수 없습니다.");
        }

        // enroll.usesLocker is the user's final intention regarding locker usage.
        // This value is received from the UI (via wantsLocker parameter) and set here.
        // The KISPG webhook will later check this flag to perform actual locker
        // allocation/deallocation.
        enroll.setUsesLocker(usesLocker);

        // Note: All locker allocation/deallocation logic (increment/decrement quantity,
        // setting enroll.lockerAllocated, and enroll.lockerPgToken) has been moved to
        // KispgWebhookServiceImpl. This confirmPayment method is now only responsible
        // for capturing the user's final intent (enroll.usesLocker).

        // The pgToken received here can be logged for auditing or debugging if
        // necessary,
        // but it's not used for direct locker allocation in this method anymore.
        if (pgToken != null && !pgToken.trim().isEmpty()) {
            logger.info("ConfirmPayment called for enrollId: {} with pgToken: {}, usesLocker: {}", enrollId, pgToken,
                    usesLocker);
        } else {
            // While not strictly an error for this method's reduced scope,
            // a missing pgToken on return from PG might indicate an issue in the KISPG
            // flow.
            logger.warn("ConfirmPayment called for enrollId: {} with NULL or EMPTY pgToken. usesLocker: {}", enrollId,
                    usesLocker);
        }

        enrollRepository.save(enroll);
    }

    @Override
    @Transactional
    public KispgCancelResponseDto requestCancelPayment(Long paymentId, int cancelAmount, String reason,
            boolean isPartial) {
        logger.info("결제 취소 요청 처리 시작 (Payment ID: {}, 금액: {}, 부분환불: {})", paymentId, cancelAmount, isPartial);

        Payment payment = paymentRepository.findById(paymentId)
                .orElseThrow(() -> new ResourceNotFoundException("취소할 결제 정보를 찾을 수 없습니다: " + paymentId,
                        ErrorCode.PAYMENT_INFO_NOT_FOUND));

        if (payment.getTid() == null || payment.getTid().trim().isEmpty()) {
            throw new BusinessRuleException(ErrorCode.PAYMENT_CANCEL_NOT_ALLOWED, "PG사 거래 ID(TID)가 없어 취소할 수 없습니다.");
        }

        String moid = payment.getMoid();
        if (moid == null || moid.trim().isEmpty()) {
            throw new BusinessRuleException(ErrorCode.PAYMENT_CANCEL_NOT_ALLOWED, "주문번호(MOID)가 없어 취소할 수 없습니다.");
        }

        String payMethod = payment.getPayMethod();
        if (payMethod == null || payMethod.trim().isEmpty() || "UNKNOWN".equalsIgnoreCase(payMethod)) {
            throw new BusinessRuleException(ErrorCode.PAYMENT_CANCEL_NOT_ALLOWED, "결제수단(payMethod) 정보가 없어 취소할 수 없습니다.");
        }

        // 1. PG사 취소 API 호출
        KispgCancelResponseDto cancelResponse = kispgPaymentService.cancelPayment(payment.getTid(), moid,
                payMethod.toLowerCase(), cancelAmount,
                reason, isPartial);

        // 2. PG사 취소 성공 시, Payment 상태 업데이트
        if ("2001".equals(cancelResponse.getResultCd())) {
            logger.info("PG사 환불 성공. Payment 상태 업데이트 (Payment ID: {}, 금액: {}, 부분환불: {})", paymentId, cancelAmount,
                    isPartial);
            int totalPaidAmount = payment.getPaidAmt() != null ? payment.getPaidAmt() : 0;
            int newRefundedAmount = (payment.getRefundedAmt() == null ? 0 : payment.getRefundedAmt()) + cancelAmount;

            payment.setRefundedAmt(newRefundedAmount);
            payment.setRefundDt(LocalDateTime.now());
            payment.setPgResultCode(cancelResponse.getResultCd());
            payment.setPgResultMsg(cancelResponse.getResultMsg());

            // 전액 환불되었는지, 부분 환불인지에 따라 상태 변경
            if (newRefundedAmount >= totalPaidAmount) {
                payment.setStatus(PaymentStatus.CANCELED);
            } else {
                payment.setStatus(PaymentStatus.PARTIAL_REFUNDED);
            }
            paymentRepository.save(payment);
        } else {
            // PG사 취소 실패 시 BusinessRuleException 발생
            logger.error("PG사 결제 취소 실패: [Code: {}, Msg: {}]", cancelResponse.getResultCd(),
                    cancelResponse.getResultMsg());
            throw new BusinessRuleException(ErrorCode.PAYMENT_CANCEL_FAILED,
                    "PG사 결제 취소 실패: " + cancelResponse.getResultMsg());
        }

        return cancelResponse;
    }

    @Override
    @Transactional
    public Payment createPaymentFromWebhook(KispgNotificationRequest notification) {
        // 이 메소드는 KispgWebhookServiceImpl의 핵심 로직(Payment 생성/업데이트)을 수행합니다.
        // Webhook 서비스에서는 이 메소드를 호출하여 결과를 받아 처리합니다.

        // 1. moid 파싱 (기존 Webhook 서비스 로직과 동일)
        String moid = notification.getMoid();
        if (moid == null || moid.trim().isEmpty()) {
            throw new BusinessRuleException(ErrorCode.PAYMENT_WEBHOOK_INVALID_REQUEST, "MOID가 누락되었습니다.");
        }

        Enroll enroll;
        if (moid.startsWith("temp_")) {
            // temp_ moid의 경우, 결제 성공 시에만 수강신청(Enroll)을 생성해야 함.
            // 이 로직은 KispgWebhookServiceImpl에 남아있는 것이 더 적절할 수 있음.
            // 왜냐하면 PaymentService가 Enroll 생성 책임을 갖는 것은 역할 범위를 넘어서기 때문.
            // 하지만 중앙화 관점에서 여기에 두되, 명확히 분리.
            // 여기서는 Enroll을 찾는 로직만 우선 구현. 실제 생성은 Webhook 서비스에서 처리 후,
            // 다시 이 메소드를 호출하거나, Enroll 객체를 파라미터로 받아야 함.
            // --> 현재 구조에서는 Webhook 서비스에서 Enroll을 생성/조회하고,
            // 그 결과를 바탕으로 Payment를 생성하는 것이 더 나은 설계임.
            // 하지만 "상태 변경 중앙화"에 초점을 맞춰, Payment 생성 로직을 여기로 가져옴.
            // Enroll을 여기서 직접 생성하지 않고, enrollId를 기반으로 찾거나 없으면 예외 발생.
            throw new BusinessRuleException(ErrorCode.INTERNAL_SERVER_ERROR, "temp_ moid 처리는 현재 이 메소드에서 지원되지 않습니다.");

        } else if (moid.startsWith("enroll_")) {
            try {
                String enrollIdStr = moid.substring("enroll_".length()).split("_")[0];
                Long enrollId = Long.parseLong(enrollIdStr);
                enroll = enrollRepository.findById(enrollId)
                        .orElseThrow(() -> new ResourceNotFoundException(
                                "웹훅 알림에 해당하는 수강신청 정보를 찾을 수 없습니다. moid: " + moid, ErrorCode.ENROLLMENT_NOT_FOUND));
            } catch (Exception e) {
                throw new BusinessRuleException(ErrorCode.PAYMENT_WEBHOOK_INVALID_REQUEST, "잘못된 형식의 MOID 입니다: " + moid);
            }
        } else {
            throw new BusinessRuleException(ErrorCode.PAYMENT_WEBHOOK_INVALID_REQUEST, "알 수 없는 형식의 MOID 입니다: " + moid);
        }

        // 2. 결제 성공/실패에 따른 Payment 객체 생성 및 상태 설정
        Payment payment;
        final String KISPG_SUCCESS_CODE = "0000";
        if (KISPG_SUCCESS_CODE.equals(notification.getResultCode())) {
            // 결제 성공
            payment = buildPayment(enroll, notification, PaymentStatus.PAID);
            // Enroll의 상태도 변경
            enrollRepository.updatePayStatus(enroll.getEnrollId(), "PAID");
        } else {
            // 결제 실패
            payment = buildPayment(enroll, notification, PaymentStatus.FAILED);
            // Enroll의 상태도 변경 (예: FAILED)
            enrollRepository.updatePayStatus(enroll.getEnrollId(), "FAILED");
        }

        return paymentRepository.save(payment);
    }

    private Payment buildPayment(Enroll enroll, KispgNotificationRequest notification, PaymentStatus status) {
        int totalAmount = Integer.parseInt(notification.getAmt());
        int lessonAmountValue;
        int lockerAmountValue;

        // 사물함 사용 여부에 따라 금액 분리
        if (enroll.isUsesLocker()) {
            lockerAmountValue = lockerFeeConfig;
            lessonAmountValue = totalAmount - lockerAmountValue;
        } else {
            lockerAmountValue = 0;
            lessonAmountValue = totalAmount;
        }

        return Payment.builder()
                .enroll(enroll)
                .status(status)
                .paidAt(status == PaymentStatus.PAID ? LocalDateTime.now() : null)
                .moid(notification.getMoid())
                .tid(notification.getTid())
                .paidAmt(totalAmount)
                .lessonAmount(lessonAmountValue)
                .lockerAmount(lockerAmountValue)
                .payMethod(notification.getPayMethod())
                .pgResultCode(notification.getResultCode())
                .pgResultMsg(notification.getResultMsg())
                .createdBy(enroll.getUser().getUuid())
                .updatedBy(enroll.getUser().getUuid())
                .build();
    }
}