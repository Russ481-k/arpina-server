package cms.kispg.service;

import cms.kispg.dto.KispgInitParamsDto;
import cms.user.domain.User;
import cms.swimming.dto.EnrollRequestDto;
import cms.mypage.dto.EnrollDto;
import cms.kispg.dto.PaymentApprovalRequestDto;
import cms.kispg.dto.KispgCancelResponseDto;
import cms.admin.payment.dto.KispgQueryRequestDto;

import java.util.Map;

public interface KispgPaymentService {
    /**
     * KISPG 결제창 호출에 필요한 초기화 파라미터를 생성합니다.
     * 
     * @param enrollId    수강 신청 ID
     * @param currentUser 현재 사용자
     * @param userIp      사용자 IP 주소
     * @return KISPG 초기화 파라미터
     */
    KispgInitParamsDto generateInitParams(Long enrollId, User currentUser, String userIp);

    KispgInitParamsDto preparePaymentWithoutEnroll(EnrollRequestDto enrollRequest, User currentUser, String userIp);

    /**
     * MOID로 결제 상태를 검증하고 생성된 수강신청 정보를 조회합니다.
     * 
     * @param moid        결제 주문번호 (KISPG에서 사용된 moid)
     * @param currentUser 현재 사용자
     * @return 생성된 수강신청 정보 (결제 성공 시)
     */
    EnrollDto verifyAndGetEnrollment(String moid, User currentUser);

    /**
     * KISPG 승인 API 호출 및 결제 처리
     */
    EnrollDto approvePaymentAndCreateEnrollment(PaymentApprovalRequestDto approvalRequest, User currentUser,
            String userIp);

    KispgCancelResponseDto cancelPayment(String tid, String moid, String payMethod, int cancelAmount, String reason,
            boolean isPartial);

    Map<String, Object> queryTransactionAtPg(KispgQueryRequestDto requestDto);
}