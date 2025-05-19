package cms.enroll.service;

import cms.mypage.dto.EnrollDto;
import cms.mypage.dto.CheckoutDto;
import cms.mypage.dto.RenewalRequestDto;
import cms.user.domain.User;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import cms.swimming.dto.EnrollResponseDto;

public interface EnrollmentService {

    /**
     * 사용자의 수강 신청 목록을 조회합니다. (Mypage)
     * @param user 현재 사용자
     * @param status 조회할 신청 상태 (옵셔널, pay_status 기준)
     * @param pageable 페이징 정보
     * @return 페이징된 EnrollDto 목록
     */
    Page<EnrollDto> getEnrollments(User user, String status, Pageable pageable);

    /**
     * 특정 수강 신청 상세 정보를 조회합니다. (Mypage)
     * @param user 현재 사용자
     * @param enrollId 신청 ID
     * @return EnrollDto 신청 상세 정보
     */
    EnrollDto getEnrollmentDetails(User user, Long enrollId);

    /**
     * 수강 신청에 대한 결제 준비(Checkout)를 진행합니다. (Mypage 전용)
     * @param user 현재 사용자
     * @param enrollId 신청 ID (모든 UNPAID 신청 건 대상)
     * @return CheckoutDto 결제 준비 정보
     */
    CheckoutDto processCheckout(User user, Long enrollId);

    /**
     * 수강 신청에 대한 결제를 처리합니다. (Mypage 전용)
     * @param user 현재 사용자
     * @param enrollId 신청 ID (모든 UNPAID 신청 건 대상)
     * @param pgToken PG사로부터 받은 토큰
     */
    void processPayment(User user, Long enrollId, String pgToken);

    /**
     * 수강 신청을 취소 요청합니다. (Mypage)
     * @param user 현재 사용자
     * @param enrollId 신청 ID
     * @param reason 취소 사유
     */
    void requestEnrollmentCancellation(User user, Long enrollId, String reason);

    /**
     * 수강 재등록(갱신)을 요청합니다. (Mypage)
     * @param user 현재 사용자
     * @param renewalRequestDto 재등록 요청 정보
     * @return EnrollDto 생성된 재등록 정보 (UNPAID 상태)
     */
    EnrollDto processRenewal(User user, RenewalRequestDto renewalRequestDto);

    /**
     * 신규 수강 신청을 생성합니다. (수영장 페이지 등에서 호출)
     * 결제는 Mypage에서 별도로 진행됩니다.
     * @param user 현재 사용자
     * @param initialEnrollRequest DTO from cms.swimming.dto (lessonId, lockerId 등 포함)
     * @param ipAddress 사용자의 IP 주소
     * @return EnrollResponseDto from cms.swimming.dto (UNPAID 상태, enroll_id, expire_dt 등)
     */
    cms.swimming.dto.EnrollResponseDto createInitialEnrollment(User user, cms.swimming.dto.EnrollRequestDto initialEnrollRequest, String ipAddress);

    // Admin methods
    /**
     * 관리자용: 모든 신청 내역을 페이징하여 조회합니다.
     * @param pageable 페이징 정보
     * @return 페이징된 EnrollResponseDto 목록
     */
    Page<EnrollResponseDto> getAllEnrollmentsAdmin(Pageable pageable);

    /**
     * 관리자용: 특정 상태의 모든 신청 내역을 페이징하여 조회합니다.
     * @param status 신청 상태 (Enroll.Status 또는 PayStatus)
     * @param pageable 페이징 정보
     * @return 페이징된 EnrollResponseDto 목록
     */
    Page<EnrollResponseDto> getAllEnrollmentsByStatusAdmin(String status, Pageable pageable);

    /**
     * 관리자용: 특정 강습의 모든 신청 내역을 페이징하여 조회합니다.
     * @param lessonId 강습 ID
     * @param pageable 페이징 정보
     * @return 페이징된 EnrollResponseDto 목록
     */
    Page<EnrollResponseDto> getAllEnrollmentsByLessonIdAdmin(Long lessonId, Pageable pageable);

    /**
     * 관리자용: 수강 신청 취소 요청을 승인합니다.
     * @param enrollId 신청 ID
     * @param refundPct 환불 비율
     */
    void approveEnrollmentCancellationAdmin(Long enrollId, Integer refundPct);

    /**
     * 관리자용: 수강 신청 취소 요청을 거부합니다.
     * @param enrollId 신청 ID
     * @param comment 거부 사유
     */
    void denyEnrollmentCancellationAdmin(Long enrollId, String comment);

} 