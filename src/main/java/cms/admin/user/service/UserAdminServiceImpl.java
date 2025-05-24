package cms.admin.user.service;

import cms.admin.user.dto.UserMemoDto;
import cms.user.domain.User;
import cms.user.domain.UserMemo;
import cms.user.repository.UserMemoRepository;
import cms.user.repository.UserRepository; // UserRepository 주입
import cms.common.exception.ResourceNotFoundException;
import cms.common.exception.ErrorCode;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.time.LocalDateTime;

@Service
@RequiredArgsConstructor
@Transactional
public class UserAdminServiceImpl implements UserAdminService {

    private final UserMemoRepository userMemoRepository;
    private final UserRepository userRepository; // User 존재 확인용

    private UserMemoDto convertToDto(UserMemo userMemo) {
        return UserMemoDto.builder()
                .userUuid(userMemo.getUserUuid())
                .memoContent(userMemo.getMemoContent())
                .updatedAt(userMemo.getUpdatedAt())
                .updatedByAdminId(userMemo.getUpdatedByAdminId())
                .build();
    }

    @Override
    @Transactional(readOnly = true)
    public UserMemoDto getUserMemo(String userUuid) {
        User user = userRepository.findByUuid(userUuid)
            .orElseThrow(() -> new ResourceNotFoundException("User not found with UUID: " + userUuid, ErrorCode.USER_NOT_FOUND));
        return userMemoRepository.findByUserUuid(userUuid)
                .map(this::convertToDto)
                .orElse(UserMemoDto.builder().userUuid(userUuid).memoContent("").build()); // 메모가 없으면 빈 DTO 반환
    }

    @Override
    public UserMemoDto updateUserMemo(String userUuid, String memoContent, String adminId) {
        User user = userRepository.findByUuid(userUuid)
            .orElseThrow(() -> new ResourceNotFoundException("User not found with UUID: " + userUuid, ErrorCode.USER_NOT_FOUND));

        UserMemo userMemo = userMemoRepository.findByUserUuid(userUuid)
                .orElseGet(() -> UserMemo.builder()
                                        .userUuid(userUuid)
                                        .user(user) // 연관관계 설정
                                        .build());
        
        userMemo.setMemoContent(memoContent);
        userMemo.setUpdatedByAdminId(adminId);
        // PreUpdate 어노테이션에 의해 updatedAt은 자동 설정됨
        return convertToDto(userMemoRepository.save(userMemo));
    }

    @Override
    public void deleteUserMemo(String userUuid, String adminId) {
        User user = userRepository.findByUuid(userUuid)
            .orElseThrow(() -> new ResourceNotFoundException("User not found with UUID: " + userUuid, ErrorCode.USER_NOT_FOUND));
        UserMemo userMemo = userMemoRepository.findByUserUuid(userUuid)
                .orElseThrow(() -> new ResourceNotFoundException("Memo not found for user UUID: " + userUuid, ErrorCode.RESOURCE_NOT_FOUND));
        
        // (선택적) 관리자 ID 검증 로직 추가 가능
        // if (!userMemo.getUpdatedByAdminId().equals(adminId) && !"SYSTEM_ADMIN_ROLE_CHECK") {
        //    throw new AccessDeniedException("메모 삭제 권한이 없습니다.");
        // }
        userMemoRepository.delete(userMemo);
    }
} 