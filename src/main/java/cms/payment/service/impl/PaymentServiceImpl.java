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

@Service
@RequiredArgsConstructor
public class PaymentServiceImpl implements PaymentService {

    private final EnrollRepository enrollRepository;
    private final LockerService lockerService; // 새로 만든 LockerService 주입
    private static final Logger logger = LoggerFactory.getLogger(PaymentServiceImpl.class);

    @Value("${app.locker.fee:5000}") // Default to 5000 if not set
    private int lockerFeeConfig;

    @Override
    @Transactional(readOnly = true)
    public PaymentPageDetailsDto getPaymentPageDetails(Long enrollId, User currentUser) {
        Enroll enroll = enrollRepository.findById(enrollId)
                .orElseThrow(() -> new ResourceNotFoundException("수강 신청 정보를 찾을 수 없습니다 (ID: " + enrollId + ")", ErrorCode.ENROLLMENT_NOT_FOUND));

        // 사용자 권한 검증 (신청자 본인만 접근 가능하도록)
        if (currentUser == null || !enroll.getUser().getUuid().equals(currentUser.getUuid())) {
            throw new BusinessRuleException(ErrorCode.ACCESS_DENIED, "해당 결제 상세 정보에 접근할 권한이 없습니다.");
        }

        // 결제 페이지 접근 유효성 검사 (이미 PAID거나, EXPIRED 된 경우 등)
        if (!"UNPAID".equalsIgnoreCase(enroll.getPayStatus())) {
            throw new BusinessRuleException(ErrorCode.NOT_UNPAID_ENROLLMENT_STATUS, "결제 대기 상태의 수강 신청이 아닙니다. 현재 상태: " + enroll.getPayStatus());
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
    public void confirmPayment(Long enrollId, User currentUser, boolean wantsLocker, String pgToken) {
        Enroll enroll = enrollRepository.findById(enrollId)
                .orElseThrow(() -> new ResourceNotFoundException("수강 신청 정보를 찾을 수 없습니다 (ID: " + enrollId + ")", ErrorCode.ENROLLMENT_NOT_FOUND));

        if (currentUser == null || !enroll.getUser().getUuid().equals(currentUser.getUuid())) {
            throw new BusinessRuleException(ErrorCode.ACCESS_DENIED, "해당 결제 처리에 대한 권한이 없습니다.");
        }

        // Check if payment window is expired, but only if not already PAID.
        // If it became PAID via webhook just before this call, allow usesLocker to be updated.
        if (!"PAID".equalsIgnoreCase(enroll.getPayStatus()) && enroll.getExpireDt() != null && enroll.getExpireDt().isBefore(LocalDateTime.now())) {
            throw new BusinessRuleException(ErrorCode.ENROLLMENT_PAYMENT_EXPIRED, "결제 가능 시간이 만료되어 처리를 완료할 수 없습니다.");
        }

        // enroll.usesLocker is the user's final intention regarding locker usage.
        // This value is received from the UI (via wantsLocker parameter) and set here.
        // The KISPG webhook will later check this flag to perform actual locker allocation/deallocation.
        enroll.setUsesLocker(wantsLocker);

        // Note: All locker allocation/deallocation logic (increment/decrement quantity,
        // setting enroll.lockerAllocated, and enroll.lockerPgToken) has been moved to
        // KispgWebhookServiceImpl. This confirmPayment method is now only responsible
        // for capturing the user's final intent (enroll.usesLocker).

        // The pgToken received here can be logged for auditing or debugging if necessary,
        // but it's not used for direct locker allocation in this method anymore.
        if (pgToken != null && !pgToken.trim().isEmpty()) {
            logger.info("ConfirmPayment called for enrollId: {} with pgToken: {}, wantsLocker: {}", enrollId, pgToken, wantsLocker);
        } else {
            // While not strictly an error for this method's reduced scope,
            // a missing pgToken on return from PG might indicate an issue in the KISPG flow.
            logger.warn("ConfirmPayment called for enrollId: {} with NULL or EMPTY pgToken. wantsLocker: {}", enrollId, wantsLocker);
        }
        
        enrollRepository.save(enroll);
    }
} 