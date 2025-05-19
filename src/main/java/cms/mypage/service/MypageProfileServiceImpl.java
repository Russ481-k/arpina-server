package cms.mypage.service;

import cms.mypage.dto.ProfileDto;
import cms.mypage.dto.PasswordChangeDto;
import cms.user.domain.User;
import cms.user.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.security.authentication.BadCredentialsException; // 현재 비밀번호 검증 실패 시 사용

import java.util.UUID;

@Service
@RequiredArgsConstructor
@Transactional
public class MypageProfileServiceImpl implements MypageProfileService {

    private static final Logger logger = LoggerFactory.getLogger(MypageProfileServiceImpl.class);
    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;

    @Override
    @Transactional(readOnly = true)
    public ProfileDto getProfile(User user) {
        // User 엔티티에서 직접 정보를 가져오므로, DB에서 다시 로드할 필요는 없음 (user 객체가 이미 최신 상태라고 가정)
        // 만약 user 객체가 detached 상태이거나 최신 정보 보장이 필요하면 userRepository.findById(user.getUuid()) 등으로 조회
        ProfileDto profileDto = new ProfileDto();
        profileDto.setName(user.getName());
        profileDto.setUserId(user.getUsername()); // user.md 에는 userId로 되어있으나, User 엔티티에는 username 사용
        profileDto.setPhone(user.getPhone()); // phone 필드 사용
        profileDto.setAddress(user.getAddress()); // address 필드 사용
        profileDto.setEmail(user.getEmail());
        profileDto.setCarNo(user.getCarNo());
        return profileDto;
    }

    @Override
    public ProfileDto updateProfile(User authenticatedUser, ProfileDto profileDto) {
        User user = userRepository.findById(authenticatedUser.getUuid())
                .orElseThrow(() -> new RuntimeException("User not found with uuid: " + authenticatedUser.getUuid()));

        // DTO에 있는 정보로 User 엔티티 업데이트
        user.setName(profileDto.getName());
        user.setEmail(profileDto.getEmail());
        user.setCarNo(profileDto.getCarNo());
        user.setPhone(profileDto.getPhone()); // phone 필드 업데이트
        user.setAddress(profileDto.getAddress()); // address 필드 업데이트

        User updatedUser = userRepository.save(user);
        return getProfile(updatedUser); // 업데이트된 정보로 다시 DTO 생성하여 반환
    }

    @Override
    public void changePassword(User authenticatedUser, PasswordChangeDto passwordChangeDto) {
        User user = userRepository.findById(authenticatedUser.getUuid())
                .orElseThrow(() -> new RuntimeException("User not found with uuid: " + authenticatedUser.getUuid()));

        // 현재 비밀번호 확인
        if (!passwordEncoder.matches(passwordChangeDto.getCurrentPw(), user.getPassword())) {
            throw new BadCredentialsException("현재 비밀번호가 일치하지 않습니다.");
        }

        // 새 비밀번호 암호화 및 저장
        user.setPassword(passwordEncoder.encode(passwordChangeDto.getNewPw()));
        // user.md 에 따르면 temp_pw_flag 를 해제해야 할 수 있음
        if (user.isTempPwFlag()) {
            user.setTempPwFlag(false);
        }
        userRepository.save(user);
    }

    @Override
    public void issueTemporaryPassword(String userId) {
        User user = userRepository.findByUsername(userId)
                .orElseThrow(() -> new RuntimeException("User not found with username: " + userId));

        String temporaryPassword = UUID.randomUUID().toString().substring(0, 8); // 예: 8자리 UUID
        user.setPassword(passwordEncoder.encode(temporaryPassword));
        user.setTempPwFlag(true); // 임시 비밀번호 플래그 설정
        userRepository.save(user);

        // TODO: 실제 이메일 발송 로직 또는 다른 알림 방식 구현
        logger.info("Temporary password issued for user: {}. Email: {}. Temporary Password: {}", 
                    user.getUsername(), user.getEmail(), temporaryPassword);
    }
} 