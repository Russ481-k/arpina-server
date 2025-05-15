package cms.user.service;

import cms.user.dto.UserRoleDto;
import java.util.List;

public interface UserRoleService {
    UserRoleDto createRole(UserRoleDto roleDto);
    UserRoleDto updateRole(Long roleId, UserRoleDto roleDto);
    void deleteRole(Long roleId);
    UserRoleDto getRole(Long roleId);
    List<UserRoleDto> getAllRoles();
    void assignRoleToUser(Long roleId, String userId);
    void removeRoleFromUser(Long roleId, String userId);
} 