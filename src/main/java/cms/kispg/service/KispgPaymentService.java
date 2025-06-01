package cms.kispg.service;

import cms.kispg.dto.KispgInitParamsDto;
import cms.user.domain.User;
import cms.swimming.dto.EnrollRequestDto;

public interface KispgPaymentService {
    /**
     * KISPG 결제창 호출에 필요한 초기화 파라미터를 생성합니다.
     * @param enrollId 수강 신청 ID
     * @param currentUser 현재 사용자
     * @param userIp 사용자 IP 주소
     * @return KISPG 초기화 파라미터
     */
    KispgInitParamsDto generateInitParams(Long enrollId, User currentUser, String userIp);

    KispgInitParamsDto preparePaymentWithoutEnroll(EnrollRequestDto enrollRequest, User currentUser, String userIp);
} 