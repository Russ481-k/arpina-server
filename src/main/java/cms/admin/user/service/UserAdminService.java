package cms.admin.user.service;

import cms.admin.user.dto.UserMemoDto;

public interface UserAdminService {
    UserMemoDto getUserMemo(String userUuid);
    UserMemoDto updateUserMemo(String userUuid, String memoContent, String adminId);
    void deleteUserMemo(String userUuid, String adminId); // 선택적으로 삭제 기능도 추가
} 