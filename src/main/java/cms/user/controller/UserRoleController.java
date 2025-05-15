package cms.user.controller;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import cms.user.dto.UserRoleDto;
import cms.user.service.UserRoleService;
import cms.common.dto.ApiResponseSchema;

import java.util.List;

@Tag(name = "cms_05_User", description = "사용자 역할 API")
@RestController
@RequestMapping("/cms/user-role")
@RequiredArgsConstructor
public class UserRoleController {

    private final UserRoleService userRoleService;

    @Operation(summary = "Create a new role")
    @PostMapping
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<ApiResponseSchema<UserRoleDto>> createRole(
            @Parameter(description = "Role information") @RequestBody UserRoleDto roleDto) {
        UserRoleDto createdRole = userRoleService.createRole(roleDto);
        return ResponseEntity.ok(ApiResponseSchema.success(createdRole, "역할이 성공적으로 생성되었습니다."));
    }

    @Operation(summary = "Update a role")
    @PutMapping("/{roleId}")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<ApiResponseSchema<UserRoleDto>> updateRole(
            @Parameter(description = "Role ID") @PathVariable Long roleId,
            @Parameter(description = "Role information") @RequestBody UserRoleDto roleDto) {
        UserRoleDto updatedRole = userRoleService.updateRole(roleId, roleDto);
        return ResponseEntity.ok(ApiResponseSchema.success(updatedRole, "역할이 성공적으로 수정되었습니다."));
    }

    @Operation(summary = "Delete a role")
    @DeleteMapping("/{roleId}")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<ApiResponseSchema<Void>> deleteRole(
            @Parameter(description = "Role ID") @PathVariable Long roleId) {
        userRoleService.deleteRole(roleId);
        return ResponseEntity.ok(ApiResponseSchema.success("역할이 성공적으로 삭제되었습니다."));
    }

    @Operation(summary = "Get all roles")
    @GetMapping
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<ApiResponseSchema<List<UserRoleDto>>> getAllRoles() {
        List<UserRoleDto> roles = userRoleService.getAllRoles();
        return ResponseEntity.ok(ApiResponseSchema.success(roles));
    }

    @Operation(summary = "Get role by ID")
    @GetMapping("/{roleId}")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<ApiResponseSchema<UserRoleDto>> getRole(
            @Parameter(description = "Role ID") @PathVariable Long roleId) {
        UserRoleDto role = userRoleService.getRole(roleId);
        return ResponseEntity.ok(ApiResponseSchema.success(role));
    }

    @Operation(summary = "Assign role to user")
    @PostMapping("/{roleId}/user/{userId}")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<ApiResponseSchema<Void>> assignRoleToUser(
            @Parameter(description = "Role ID") @PathVariable Long roleId,
            @Parameter(description = "User ID") @PathVariable String userId) {
        userRoleService.assignRoleToUser(roleId, userId);
        return ResponseEntity.ok(ApiResponseSchema.success("역할이 성공적으로 할당되었습니다."));
    }

    @Operation(summary = "Remove role from user")
    @DeleteMapping("/{roleId}/user/{userId}")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<ApiResponseSchema<Void>> removeRoleFromUser(
            @Parameter(description = "Role ID") @PathVariable Long roleId,
            @Parameter(description = "User ID") @PathVariable String userId) {
        userRoleService.removeRoleFromUser(roleId, userId);
        return ResponseEntity.ok(ApiResponseSchema.success("역할이 성공적으로 제거되었습니다."));
    }
} 