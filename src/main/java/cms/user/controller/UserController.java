package cms.user.controller;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;

import cms.user.dto.PasswordChangeDto;
import cms.user.dto.SiteInfo;
import cms.user.dto.SiteManagerRegisterRequest;
import cms.user.dto.UserDto;
import cms.user.dto.UserEnrollmentHistoryDto;
import cms.user.dto.UserRegisterRequest;
import cms.user.service.UserService;
import cms.common.dto.ApiResponseSchema;

@Tag(name = "cms_05_User", description = "사용자 관리 API")
@RestController
@RequestMapping("/cms/user")
@RequiredArgsConstructor
public class UserController {

    private final UserService userService;

    @Operation(summary = "Create a new user")
    @PostMapping
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<ApiResponseSchema<UserDto>> createUser(
            @Validated @RequestBody UserDto userDto,
            @RequestHeader("X-Forwarded-For") String clientIp) {
        String currentUserId = SecurityContextHolder.getContext().getAuthentication().getName();
        UserDto createdUser = userService.createUser(userDto, currentUserId, clientIp);
        return ResponseEntity.ok(ApiResponseSchema.success(createdUser, "사용자가 성공적으로 생성되었습니다."));
    }

    @Operation(summary = "Update an existing user")
    @PutMapping("/{userId}")
    @PreAuthorize("hasRole('ADMIN') or #userId == authentication.principal.userId")
    public ResponseEntity<ApiResponseSchema<Void>> updateUser(
            @Parameter(description = "User ID") @PathVariable String userId,
            @Validated @RequestBody UserDto userDto) {
        userDto.setUuid(userId);
        userService.updateUser(userDto, SecurityContextHolder.getContext().getAuthentication().getName(), null);
        return ResponseEntity.ok(ApiResponseSchema.success("사용자 정보가 성공적으로 수정되었습니다."));
    }

    @Operation(summary = "Delete a user")
    @DeleteMapping("/{userId}")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<ApiResponseSchema<Void>> deleteUser(
            @Parameter(description = "User ID") @PathVariable String userId) {
        userService.deleteUser(userId);
        return ResponseEntity.ok(ApiResponseSchema.success("사용자가 성공적으로 삭제되었습니다."));
    }

    @Operation(summary = "Get a user by ID")
    @GetMapping("/{userId}")
    @PreAuthorize("hasRole('ADMIN') or #userId == authentication.principal.userId")
    public ResponseEntity<ApiResponseSchema<UserDto>> getUser(@PathVariable String userId) {
        UserDto user = userService.getUser(userId);
        return ResponseEntity.ok(ApiResponseSchema.success(user));
    }

    @Operation(summary = "Get all users with enrollment history")
    @GetMapping
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<ApiResponseSchema<Page<UserEnrollmentHistoryDto>>> getUsers(
            @RequestParam(required = false) String username,
            @RequestParam(required = false) String name,
            @RequestParam(required = false) String phone,
            @RequestParam(required = false) String lessonTime,
            @RequestParam(required = false) String payStatus,
            @RequestParam(required = false) String searchKeyword,
            Pageable pageable) {
        Page<UserEnrollmentHistoryDto> users = userService.getUsers(username, name, phone, lessonTime, payStatus,
                searchKeyword,
                pageable);
        return ResponseEntity.ok(ApiResponseSchema.success(users));
    }

    @Operation(summary = "Change user password")
    @PostMapping("/{userId}/change-password")
    @PreAuthorize("hasRole('ADMIN') or #userId == authentication.principal.userId")
    public ResponseEntity<ApiResponseSchema<Void>> changePassword(
            @Parameter(description = "User ID") @PathVariable String userId,
            @Validated @RequestBody PasswordChangeDto passwordChangeDto) {
        userService.changePassword(userId, passwordChangeDto.getNewPassword(),
                SecurityContextHolder.getContext().getAuthentication().getName(), null);
        return ResponseEntity.ok(ApiResponseSchema.success("비밀번호가 성공적으로 변경되었습니다."));
    }

    @Operation(summary = "Update user status")
    @PutMapping("/{userId}/status")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<ApiResponseSchema<UserDto>> updateStatus(
            @Parameter(description = "User ID") @PathVariable String userId,
            @RequestParam String status) {
        UserDto updatedUser = userService.updateStatus(userId, status,
                SecurityContextHolder.getContext().getAuthentication().getName(), null);
        return ResponseEntity.ok(ApiResponseSchema.success(updatedUser, "사용자 상태가 성공적으로 변경되었습니다."));
    }

    @Operation(summary = "Register a new user")
    @PostMapping("/register")
    public ResponseEntity<ApiResponseSchema<UserDto>> register(@Validated @RequestBody UserRegisterRequest request) {
        UserDto registeredUser = userService.registerUser(request,
                SecurityContextHolder.getContext().getAuthentication().getName(), null);
        return ResponseEntity.ok(ApiResponseSchema.success(registeredUser, "사용자가 성공적으로 등록되었습니다."));
    }

    @Operation(summary = "Register a site manager")
    @PostMapping("/site-managers")
    public ResponseEntity<ApiResponseSchema<UserDto>> registerSiteManager(
            @Validated @RequestBody SiteManagerRegisterRequest request) {
        UserDto registeredManager = userService.registerSiteManager(request,
                SecurityContextHolder.getContext().getAuthentication().getName(), null);
        return ResponseEntity.ok(ApiResponseSchema.success(registeredManager, "사이트 관리자가 성공적으로 등록되었습니다."));
    }

    @Operation(summary = "Get site information")
    @GetMapping("/site-info")
    public ResponseEntity<ApiResponseSchema<SiteInfo>> getSiteInfo() {
        SiteInfo siteInfo = userService.getSiteInfo();
        return ResponseEntity.ok(ApiResponseSchema.success(siteInfo));
    }
}