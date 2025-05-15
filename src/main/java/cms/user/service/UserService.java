package cms.user.service;

import cms.user.dto.SiteInfo;
import cms.user.dto.SiteManagerRegisterRequest;
import cms.user.dto.UserDto;
import cms.user.dto.UserRegisterRequest;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import java.time.LocalDateTime;

public interface UserService {
    UserDto createUser(UserDto userDto, String createdBy, String createdIp);
    void updateUser(UserDto userDto, String updatedBy, String updatedIp);
    void deleteUser(String uuid);
    UserDto getUser(String uuid);
    Page<UserDto> getUsers(Pageable pageable);
    void changePassword(String uuid, String newPassword, String updatedBy, String updatedIp);
    UserDto updateStatus(String uuid, String status, String updatedBy, String updatedIp);
    UserDto registerUser(UserRegisterRequest request, String createdBy, String createdIp);
    UserDto registerSiteManager(SiteManagerRegisterRequest request, String createdBy, String createdIp);
    SiteInfo getSiteInfo();
    UserDto updateResetToken(String email, String resetToken, LocalDateTime resetTokenExpiry);
    UserDto updatePasswordWithResetToken(String resetToken, String newPassword);
} 