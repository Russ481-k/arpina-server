package cms.admin.user.controller;

import cms.admin.user.dto.UserMemoDto;
import cms.admin.user.service.UserAdminService;
import cms.common.dto.ApiResponseSchema;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
// import org.springframework.security.core.annotation.AuthenticationPrincipal; // 관리자 정보 가져올 때 필요
// import cms.user.domain.User; // 관리자 User 엔티티
import org.springframework.web.bind.annotation.*;

import javax.validation.Valid;

@Tag(name = "CMS - User Admin", description = "사용자 관련 관리 API (메모 등)")
@RestController
@RequestMapping("/cms/users")
@RequiredArgsConstructor
@PreAuthorize("hasAnyRole('ADMIN', 'SYSTEM_ADMIN')")
public class UserAdminController {

    private final UserAdminService userAdminService;

    @Operation(summary = "사용자 메모 조회", description = "특정 사용자에 대한 관리자 메모를 조회합니다.")
    @GetMapping("/{userUuid}/memo")
    public ResponseEntity<ApiResponseSchema<UserMemoDto>> getUserMemo(
            @Parameter(description = "사용자 UUID", required = true) @PathVariable String userUuid) {
        UserMemoDto memoDto = userAdminService.getUserMemo(userUuid);
        return ResponseEntity.ok(ApiResponseSchema.success(memoDto, "사용자 메모 조회 성공"));
    }

    @Operation(summary = "사용자 메모 작성/수정", description = "특정 사용자에 대한 관리자 메모를 작성하거나 수정합니다.")
    @PostMapping("/{userUuid}/memo") // POST는 생성, PUT은 전체 업데이트, PATCH는 부분 업데이트에 적합
    public ResponseEntity<ApiResponseSchema<UserMemoDto>> createOrUpdateUserMemo(
            @Parameter(description = "사용자 UUID", required = true) @PathVariable String userUuid,
            @Valid @RequestBody UserMemoDto memoDto,
            /* @AuthenticationPrincipal User adminUser */ String adminId) { // 실제로는 인증된 관리자 ID 사용
        // 임시로 adminId를 파라미터로 받지만, 실제로는 SecurityContext에서 가져와야 함
        UserMemoDto updatedMemo = userAdminService.updateUserMemo(userUuid, memoDto.getMemo(), adminId /*
                                                                                                        * 실제로는
                                                                                                        * adminUser.
                                                                                                        * getUsername()
                                                                                                        * 또는 adminUser.
                                                                                                        * getUuid()
                                                                                                        */);
        return ResponseEntity.status(HttpStatus.OK).body(ApiResponseSchema.success(updatedMemo, "사용자 메모 업데이트 성공"));
    }

    @Operation(summary = "사용자 메모 삭제", description = "특정 사용자에 대한 관리자 메모를 삭제합니다.")
    @DeleteMapping("/{userUuid}/memo")
    public ResponseEntity<ApiResponseSchema<Void>> deleteUserMemo(
            @Parameter(description = "사용자 UUID", required = true) @PathVariable String userUuid,
            /* @AuthenticationPrincipal User adminUser */ String adminId) { // 실제로는 인증된 관리자 ID 사용
        userAdminService.deleteUserMemo(userUuid, adminId /* 실제로는 adminUser.getUsername() 또는 adminUser.getUuid() */);
        return ResponseEntity.ok(ApiResponseSchema.success(null, "사용자 메모 삭제 성공"));
    }
}