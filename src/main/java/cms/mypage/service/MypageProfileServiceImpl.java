package cms.mypage.service;

import cms.mypage.dto.ProfileDto;
import cms.mypage.dto.PasswordChangeDto;
import cms.user.domain.User;
import cms.user.repository.UserRepository;
import cms.common.exception.BusinessRuleException;
import cms.common.exception.ErrorCode;
import cms.common.exception.ResourceNotFoundException;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
// import org.springframework.security.authentication.BadCredentialsException; // No longer directly used, replaced by BusinessRuleException

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
        User freshUser = userRepository.findById(user.getUuid())
            .orElseThrow(() -> new ResourceNotFoundException(ErrorCode.USER_NOT_FOUND));
        
        ProfileDto profileDto = new ProfileDto();
        profileDto.setName(freshUser.getName());
        profileDto.setUserId(freshUser.getUsername());
        profileDto.setPhone(freshUser.getPhone());
        profileDto.setAddress(freshUser.getAddress());
        profileDto.setEmail(freshUser.getEmail());
        profileDto.setCarNo(freshUser.getCarNo());
        profileDto.setGender(freshUser.getGender());
        return profileDto;
    }

    @Override
    public ProfileDto updateProfile(User authenticatedUser, ProfileDto profileDto) {
        User user = userRepository.findById(authenticatedUser.getUuid())
                .orElseThrow(() -> new ResourceNotFoundException(ErrorCode.USER_NOT_FOUND));

        try {
            user.setName(profileDto.getName());
            user.setEmail(profileDto.getEmail());
            user.setCarNo(profileDto.getCarNo());
            user.setPhone(profileDto.getPhone());
            user.setAddress(profileDto.getAddress());
            User updatedUser = userRepository.save(user);
            return getProfile(updatedUser); // Use the existing getProfile which also fetches fresh data
        } catch (Exception e) {
            logger.error("Error updating profile for user {}: {}", authenticatedUser.getUuid(), e.getMessage(), e);
            throw new BusinessRuleException("프로필 업데이트 중 오류가 발생했습니다. 잠시 후 다시 시도해주세요.", ErrorCode.PROFILE_UPDATE_FAILED);
        }
    }

    @Override
    public void changePassword(User authenticatedUser, PasswordChangeDto passwordChangeDto) {
        User user = userRepository.findById(authenticatedUser.getUuid())
                .orElseThrow(() -> new ResourceNotFoundException(ErrorCode.USER_NOT_FOUND));

        if (!passwordEncoder.matches(passwordChangeDto.getCurrentPw(), user.getPassword())) {
            throw new BusinessRuleException(ErrorCode.INVALID_CURRENT_PASSWORD);
        }
        // Optional: Validate new password policy here if any
        // if (!isPasswordPolicyCompliant(passwordChangeDto.getNewPw())) {
        //     throw new BusinessRuleException(ErrorCode.PASSWORD_POLICY_VIOLATION);
        // }


        user.setPassword(passwordEncoder.encode(passwordChangeDto.getNewPw()));
        if (user.isTempPwFlag()) {
            user.setTempPwFlag(false);
        }
        userRepository.save(user);
    }

    @Override
    public void issueTemporaryPassword(String userId) {
        User user = userRepository.findByUsername(userId)
                .orElseThrow(() -> new ResourceNotFoundException("해당 아이디의 사용자를 찾을 수 없습니다.", ErrorCode.USER_NOT_FOUND));

        try {
            String temporaryPassword = UUID.randomUUID().toString().substring(0, 8);
            user.setPassword(passwordEncoder.encode(temporaryPassword));
            user.setTempPwFlag(true);
            userRepository.save(user);

            logger.info("Temporary password issued for user: {}. Email: {}. Temporary Password: {}", 
                        user.getUsername(), user.getEmail(), temporaryPassword);
            // TODO: 실제 이메일 발송 로직 또는 다른 알림 방식 구현
        } catch (Exception e) {
            logger.error("Error issuing temporary password for user {}: {}", userId, e.getMessage(), e);
            throw new BusinessRuleException(ErrorCode.TEMP_PASSWORD_ISSUE_FAILED);
        }
    }
} 