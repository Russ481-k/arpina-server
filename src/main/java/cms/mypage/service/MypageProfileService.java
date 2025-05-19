package cms.mypage.service;

import cms.mypage.dto.ProfileDto;
import cms.mypage.dto.PasswordChangeDto;
import cms.user.domain.User;

public interface MypageProfileService {

    /**
     * 현재 로그인된 사용자의 프로필 정보를 조회합니다.
     * @param user 현재 사용자 정보
     * @return ProfileDto 사용자 프로필 정보
     */
    ProfileDto getProfile(User user);

    /**
     * 현재 로그인된 사용자의 프로필 정보를 수정합니다.
     * @param user 현재 사용자 정보
     * @param profileDto 수정할 프로필 정보
     * @return ProfileDto 수정된 프로필 정보
     */
    ProfileDto updateProfile(User user, ProfileDto profileDto);

    /**
     * 현재 로그인된 사용자의 비밀번호를 변경합니다.
     * @param user 현재 사용자 정보
     * @param passwordChangeDto 변경할 비밀번호 정보
     */
    void changePassword(User user, PasswordChangeDto passwordChangeDto);

    /**
     * 사용자 ID를 기반으로 임시 비밀번호를 발급하고 알림을 보냅니다.
     * @param userId 대상 사용자 ID (username)
     */
    void issueTemporaryPassword(String userId);
} 