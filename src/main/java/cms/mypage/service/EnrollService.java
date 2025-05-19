package cms.mypage.service;

import cms.mypage.dto.EnrollDto;
import cms.mypage.dto.CheckoutDto;
import cms.mypage.dto.RenewalRequestDto;
import cms.user.domain.User;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

import java.util.List;

public interface EnrollService {

    /**
     * 사용자의 수강 신청 목록을 조회합니다.
     * @param user 현재 사용자
     * @param status 조회할 신청 상태 (옵셔널)
     * @param pageable 페이징 정보
     * @return 페이징된 EnrollDto 목록
     */
    Page<EnrollDto> getEnrollments(User user, String status, Pageable pageable);

    /**
     * 특정 수강 신청 상세 정보를 조회합니다.
     * @param user 현재 사용자
     * @param enrollId 신청 ID
     * @return EnrollDto 신청 상세 정보
     */
    EnrollDto getEnrollmentDetails(User user, Long enrollId);

    /**
     * 수강 신청에 대한 결제 준비(Checkout)를 진행합니다.
     * @param user 현재 사용자
     * @param enrollId 신청 ID
     * @return CheckoutDto 결제 준비 정보
     */
    CheckoutDto processCheckout(User user, Long enrollId);

    /**
     * 수강 신청에 대한 결제를 처리합니다.
     * @param user 현재 사용자
     * @param enrollId 신청 ID
     * @param pgToken PG사로부터 받은 토큰
     */
    void processPayment(User user, Long enrollId, String pgToken);

    /**
     * 수강 신청을 취소 요청합니다.
     * @param user 현재 사용자
     * @param enrollId 신청 ID
     * @param reason 취소 사유
     */
    void requestEnrollmentCancellation(User user, Long enrollId, String reason);

    /**
     * 수강 재등록(갱신)을 요청합니다.
     * @param user 현재 사용자
     * @param renewalRequestDto 재등록 요청 정보
     * @return EnrollDto 생성된 재등록 정보
     */
    EnrollDto processRenewal(User user, RenewalRequestDto renewalRequestDto);

} 