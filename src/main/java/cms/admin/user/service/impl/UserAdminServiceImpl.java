package cms.admin.user.service.impl;

import cms.admin.user.dto.UserMemoDto;
import cms.admin.user.service.UserAdminService;
import cms.user.domain.User;
import cms.user.repository.UserRepository;
import cms.common.exception.ResourceNotFoundException;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class UserAdminServiceImpl implements UserAdminService {

    private final UserRepository userRepository;

    @Override
    public UserMemoDto getUserMemo(String userUuid) {
        User user = userRepository.findByUuid(userUuid)
                .orElseThrow(() -> new ResourceNotFoundException("User not found with UUID: " + userUuid));
        // Assuming User entity has a getMemo() method or similar
        // return new UserMemoDto(userUuid, user.getMemo(), user.getMemoUpdatedAt(), user.getMemoUpdatedByAdminId());
        // Placeholder:
        return new UserMemoDto(userUuid, "Memo content placeholder", null, null);
    }

    @Override
    @Transactional
    public UserMemoDto updateUserMemo(String userUuid, String memoContent, String adminId) {
        User user = userRepository.findByUuid(userUuid)
                .orElseThrow(() -> new ResourceNotFoundException("User not found with UUID: " + userUuid));
        
        // Assuming User entity has methods like setMemo(), setMemoUpdatedAt(), setMemoUpdatedByAdminId()
        // user.setMemo(memoContent);
        // user.setMemoUpdatedAt(java.time.LocalDateTime.now());
        // user.setMemoUpdatedByAdminId(adminId); // Need to fetch admin user if storing actual admin user object
        // userRepository.save(user);
        
        // Placeholder:
        return new UserMemoDto(userUuid, memoContent, java.time.LocalDateTime.now(), adminId);
    }

    @Override
    @Transactional
    public void deleteUserMemo(String userUuid, String adminId) {
        User user = userRepository.findByUuid(userUuid)
                .orElseThrow(() -> new ResourceNotFoundException("User not found with UUID: " + userUuid));
        
        // Assuming User entity has methods like setMemo(null) or clearMemo()
        // user.setMemo(null);
        // user.setMemoUpdatedAt(java.time.LocalDateTime.now());
        // user.setMemoUpdatedByAdminId(adminId); // Log who deleted it
        // userRepository.save(user);

        // For now, this method does nothing as a placeholder
    }
} 