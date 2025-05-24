package cms.kispg.service.impl;

import cms.common.exception.BusinessRuleException;
import cms.common.exception.ErrorCode;
import cms.common.exception.ResourceNotFoundException;
import cms.enroll.domain.Enroll;
import cms.enroll.repository.EnrollRepository;
import cms.kispg.dto.KispgInitParamsDto;
import cms.kispg.service.KispgPaymentService;
import cms.swimming.domain.Lesson;
import cms.user.domain.User;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.LocalDateTime;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class KispgPaymentServiceImpl implements KispgPaymentService {

    private static final Logger logger = LoggerFactory.getLogger(KispgPaymentServiceImpl.class);
    
    private final EnrollRepository enrollRepository;

    @Value("${kispg.mid:kis000001m}")
    private String kispgMid;

    @Value("${kispg.merchantKey}")
    private String merchantKey;

    @Value("${app.api.base-url}")
    private String baseUrl;

    @Value("${app.locker.fee:5000}")
    private int lockerFee;

    @Override
    @Transactional(readOnly = true)
    public KispgInitParamsDto generateInitParams(Long enrollId, User currentUser) {
        // 1. Enroll 조회 및 권한 확인
        Enroll enroll = enrollRepository.findById(enrollId)
                .orElseThrow(() -> new ResourceNotFoundException("수강 신청 정보를 찾을 수 없습니다: " + enrollId, ErrorCode.ENROLLMENT_NOT_FOUND));

        if (!enroll.getUser().getUuid().equals(currentUser.getUuid())) {
            throw new BusinessRuleException(ErrorCode.ACCESS_DENIED, "해당 수강 신청에 대한 권한이 없습니다.");
        }

        // 2. 결제 가능 상태 확인
        if (!"UNPAID".equalsIgnoreCase(enroll.getPayStatus())) {
            throw new BusinessRuleException(ErrorCode.NOT_UNPAID_ENROLLMENT_STATUS, "결제 대기 상태가 아닙니다: " + enroll.getPayStatus());
        }

        if (enroll.getExpireDt() == null || enroll.getExpireDt().isBefore(LocalDateTime.now())) {
            throw new BusinessRuleException(ErrorCode.ENROLLMENT_PAYMENT_EXPIRED, "결제 가능 시간이 만료되었습니다.");
        }

        // 3. 정원 확인 (최종 안전장치)
        Lesson lesson = enroll.getLesson();
        long paidCount = enrollRepository.countByLessonLessonIdAndPayStatus(lesson.getLessonId(), "PAID");
        long unpaidActiveCount = enrollRepository.countByLessonLessonIdAndStatusAndPayStatusAndExpireDtAfter(
                lesson.getLessonId(), "APPLIED", "UNPAID", LocalDateTime.now());
        
        long availableSlots = lesson.getCapacity() - paidCount - unpaidActiveCount;
        if (availableSlots <= 0) {
            throw new BusinessRuleException(ErrorCode.PAYMENT_PAGE_SLOT_UNAVAILABLE, "현재 해당 강습의 결제 페이지 접근 슬롯이 가득 찼습니다.");
        }

        // 4. KISPG 파라미터 생성
        String moid = generateMoid(enrollId);
        int totalAmount = calculateTotalAmount(enroll);
        String itemName = lesson.getTitle();
        String buyerName = currentUser.getName();
        String buyerTel = currentUser.getPhone();
        String buyerEmail = currentUser.getEmail();
        String returnUrl = baseUrl + "/payment/kispg-return";
        String notifyUrl = baseUrl + "/api/v1/kispg/payment-notification";

        // 5. 해시 생성
        String requestHash = generateRequestHash(kispgMid, moid, String.valueOf(totalAmount));

        logger.info("Generated KISPG init params for enrollId: {}, moid: {}, amount: {}", enrollId, moid, totalAmount);

        return KispgInitParamsDto.builder()
                .mid(kispgMid)
                .moid(moid)
                .amt(String.valueOf(totalAmount))
                .itemName(itemName)
                .buyerName(buyerName)
                .buyerTel(buyerTel)
                .buyerEmail(buyerEmail)
                .returnUrl(returnUrl)
                .notifyUrl(notifyUrl)
                .requestHash(requestHash)
                .build();
    }

    private String generateMoid(Long enrollId) {
        long timestamp = System.currentTimeMillis();
        return String.format("enroll_%d_%d", enrollId, timestamp);
    }

    private int calculateTotalAmount(Enroll enroll) {
        int lessonPrice = enroll.getLesson().getPrice();
        // 사물함 선택 시 추가 요금 (현재 usesLocker 상태 기반)
        int totalAmount = lessonPrice;
        if (enroll.isUsesLocker()) {
            totalAmount += lockerFee;
        }
        return totalAmount;
    }

    private String generateRequestHash(String mid, String moid, String amt) {
        try {
            // KISPG 규격에 따른 해시 생성: mid + moid + amt + merchantKey
            String hashData = mid + moid + amt + merchantKey;
            MessageDigest md = MessageDigest.getInstance("SHA-256");
            byte[] hashBytes = md.digest(hashData.getBytes());
            
            // Hex 문자열로 변환
            StringBuilder sb = new StringBuilder();
            for (byte b : hashBytes) {
                sb.append(String.format("%02x", b));
            }
            return sb.toString();
        } catch (NoSuchAlgorithmException e) {
            logger.error("SHA-256 알고리즘을 찾을 수 없습니다", e);
            throw new RuntimeException("해시 생성 중 오류가 발생했습니다", e);
        }
    }
} 