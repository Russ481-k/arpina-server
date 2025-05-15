package cms.user.service.impl;

import cms.user.domain.UserRole;
import cms.user.dto.UserRoleDto;
import cms.user.repository.UserRoleRepository;
import cms.user.service.UserRoleService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class UserRoleServiceImpl implements UserRoleService {

    private final UserRoleRepository userRoleRepository;

    @Override
    @Transactional
    public UserRoleDto createRole(UserRoleDto roleDto) {
        UserRole role = UserRole.builder()
                .roleName(roleDto.getRoleName())
                .roleType(roleDto.getRoleType())
                .description(roleDto.getDescription())
                .isActive(roleDto.isActive())
                .build();
        
        UserRole savedRole = userRoleRepository.save(role);
        return convertToDto(savedRole);
    }

    @Override
    @Transactional
    public UserRoleDto updateRole(Long roleId, UserRoleDto roleDto) {
        UserRole role = userRoleRepository.findById(roleId)
                .orElseThrow(() -> new RuntimeException("Role not found"));
        
        role.update(roleDto.getRoleName(), roleDto.getRoleType(), roleDto.getDescription(), roleDto.isActive());
        return convertToDto(userRoleRepository.save(role));
    }

    @Override
    @Transactional
    public void deleteRole(Long roleId) {
        userRoleRepository.deleteById(roleId);
    }

    @Override
    @Transactional(readOnly = true)
    public UserRoleDto getRole(Long roleId) {
        return userRoleRepository.findById(roleId)
                .map(this::convertToDto)
                .orElseThrow(() -> new RuntimeException("Role not found"));
    }

    @Override
    @Transactional(readOnly = true)
    public List<UserRoleDto> getAllRoles() {
        return userRoleRepository.findAll().stream()
                .map(this::convertToDto)
                .collect(Collectors.toList());
    }

    @Override
    @Transactional
    public void assignRoleToUser(Long roleId, String userId) {
        // TODO: Implement role assignment logic
    }

    @Override
    @Transactional
    public void removeRoleFromUser(Long roleId, String userId) {
        // TODO: Implement role removal logic
    }

    private UserRoleDto convertToDto(UserRole role) {
        return UserRoleDto.builder()
                .id(role.getId())
                .roleName(role.getRoleName())
                .roleType(role.getRoleType())
                .description(role.getDescription())
                .isActive(role.isActive())
                .createdBy(role.getCreatedBy())
                .updatedBy(role.getUpdatedBy())
                .createdAt(role.getCreatedAt())
                .updatedAt(role.getUpdatedAt())
                .build();
    }
} 