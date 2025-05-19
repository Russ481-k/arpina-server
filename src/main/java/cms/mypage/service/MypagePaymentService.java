package cms.mypage.service;

import cms.mypage.dto.PaymentDto;
import cms.user.domain.User;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

public interface MypagePaymentService {

    /**
     * 현재 사용자의 결제 내역 목록을 조회합니다.
     * @param user 현재 사용자
     * @param pageable 페이징 정보
     * @return 페이징된 PaymentDto 목록
     */
    Page<PaymentDto> getPaymentHistory(User user, Pageable pageable);

    /**
     * 특정 결제에 대한 취소를 요청합니다. (환불 요청)
     * @param user 현재 사용자
     * @param paymentId 결제 ID
     */
    void requestPaymentCancellation(User user, Long paymentId);

} 