package cms.mypage.controller;

import cms.mypage.dto.ProfileDto;
import cms.mypage.dto.TempPasswordRequest;
import cms.mypage.dto.RenewalRequestDto;
import cms.mypage.dto.PaymentDto;
import cms.mypage.dto.PasswordChangeDto;
import cms.mypage.dto.EnrollDto;
import cms.mypage.dto.CheckoutDto;
import cms.mypage.dto.CheckoutRequestDto;
import cms.mypage.dto.EnrollInitiationResponseDto;
import cms.enroll.service.EnrollmentService;
import cms.mypage.service.MypagePaymentService;
import cms.mypage.service.MypageProfileService;
import cms.user.domain.User;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import javax.validation.Valid;
import java.util.Map;

@Tag(name = "Mypage API", description = "APIs for user mypage functionalities")
@RestController
@RequestMapping("/mypage")
@RequiredArgsConstructor
public class MypageController {

    private final MypageProfileService mypageProfileService;
    private final EnrollmentService enrollService;
    private final MypagePaymentService mypagePaymentService;

    @Operation(summary = "Get user profile", description = "현재 로그인된 사용자의 프로필 정보를 조회합니다.")
    @GetMapping("/profile")
    public ResponseEntity<ProfileDto> getProfile(@AuthenticationPrincipal User user) {
        if (user == null) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).build(); 
        }
        ProfileDto profileDto = mypageProfileService.getProfile(user);
        return ResponseEntity.ok(profileDto);
    }

    @Operation(summary = "Update user profile", description = "현재 로그인된 사용자의 프로필 정보를 수정합니다.")
    @PatchMapping("/profile")
    public ResponseEntity<ProfileDto> updateProfile(@AuthenticationPrincipal User user, @Valid @RequestBody ProfileDto profileDto) {
        if (user == null) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).build();
        }
        ProfileDto updatedProfile = mypageProfileService.updateProfile(user, profileDto);
        return ResponseEntity.ok(updatedProfile);
    }

    @Operation(summary = "Change user password", description = "현재 로그인된 사용자의 비밀번호를 변경합니다.")
    @PatchMapping("/password")
    public ResponseEntity<Void> changePassword(@AuthenticationPrincipal User user, @Valid @RequestBody PasswordChangeDto passwordChangeDto) {
        if (user == null) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).build();
        }
        mypageProfileService.changePassword(user, passwordChangeDto);
        return ResponseEntity.ok().build(); 
    }

    @Operation(summary = "Issue temporary password", description = "지정된 사용자 ID에 대해 임시 비밀번호를 발급하고 알림을 보냅니다. USER 권한으로 호출 시 자신의 계정에 대해서만 동작합니다.")
    @PostMapping("/password/temp")
    @PreAuthorize("hasRole('USER') or hasRole('ADMIN')")
    public ResponseEntity<Void> issueTemporaryPassword(
            @Parameter(hidden = true) @AuthenticationPrincipal User authenticatedUser,
            @Valid @RequestBody(required = false) TempPasswordRequest tempPasswordRequest) {

        String targetUserId = null;

        if (authenticatedUser == null) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).build();
        }

        boolean isAdmin = authenticatedUser.getAuthorities().stream()
                .anyMatch(grantedAuthority -> grantedAuthority.getAuthority().equals("ROLE_ADMIN"));

        if (isAdmin) {
            if (tempPasswordRequest == null || tempPasswordRequest.getUserId() == null || tempPasswordRequest.getUserId().trim().isEmpty()) {
                return ResponseEntity.badRequest().body(null);
            }
            targetUserId = tempPasswordRequest.getUserId();
        } else {
            targetUserId = authenticatedUser.getUsername();
        }

        if (targetUserId == null || targetUserId.trim().isEmpty()) {
            return ResponseEntity.badRequest().build();
        }

        mypageProfileService.issueTemporaryPassword(targetUserId);
        return ResponseEntity.ok().build();
    }

    @Operation(summary = "Get enrollments", description = "사용자의 수강 신청 목록을 조회합니다. status 파라미터로 상태 필터링 가능 (예: UNPAID, PAID)")
    @GetMapping("/enroll")
    public ResponseEntity<Page<EnrollDto>> getEnrollments(
            @AuthenticationPrincipal User user,
            @RequestParam(required = false) String status,
            @PageableDefault(size = 10, sort = "createdAt,desc") Pageable pageable) {
        if (user == null) return ResponseEntity.status(HttpStatus.UNAUTHORIZED).build();
        Page<EnrollDto> enrollments = enrollService.getEnrollments(user, status, pageable);
        return ResponseEntity.ok(enrollments);
    }

    @Operation(summary = "Get enrollment details", description = "특정 수강 신청 상세 정보를 조회합니다.")
    @GetMapping("/enroll/{id}")
    public ResponseEntity<EnrollDto> getEnrollmentDetails(
            @AuthenticationPrincipal User user,
            @PathVariable Long id) {
        if (user == null) return ResponseEntity.status(HttpStatus.UNAUTHORIZED).build();
        EnrollDto enrollDto = enrollService.getEnrollmentDetails(user, id);
        return ResponseEntity.ok(enrollDto);
    }

    @Operation(summary = "수강 신청 결제 준비", description = "선택한 수강 신청 건에 대해 결제를 준비합니다. PG사 연동에 필요한 정보를 반환합니다.")
    @PostMapping("/enroll/{enrollId}/checkout")
    public ResponseEntity<CheckoutDto> processCheckout(
            @Parameter(description = "신청 ID") @PathVariable Long enrollId,
            @Parameter(description = "사물함 사용 희망 여부") @Valid @RequestBody CheckoutRequestDto checkoutRequest,
            @AuthenticationPrincipal User user) {
        if (user == null) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).build(); 
        }
        CheckoutDto checkoutDto = enrollService.processCheckout(user, enrollId, checkoutRequest);
        return ResponseEntity.ok(checkoutDto);
    }

    @Operation(summary = "Process payment for enrollment", description = "수강 신청에 대한 결제를 처리합니다. pgToken을 받아 PG 검증 후 처리합니다.")
    @PostMapping("/enroll/{id}/pay")
    public ResponseEntity<Void> processPayment(
            @AuthenticationPrincipal User user,
            @PathVariable Long id,
            @RequestBody Map<String, String> payload) { 
        if (user == null) return ResponseEntity.status(HttpStatus.UNAUTHORIZED).build();
        String pgToken = payload.get("pgToken");
        if (pgToken == null || pgToken.trim().isEmpty()) {
            return ResponseEntity.badRequest().body(null); 
        }
        enrollService.processPayment(user, id, pgToken);
        return ResponseEntity.ok().build();
    }

    @Operation(summary = "Request enrollment cancellation", description = "수강 신청을 취소 요청합니다.")
    @PatchMapping("/enroll/{id}/cancel")
    public ResponseEntity<Void> requestEnrollmentCancellation(
            @AuthenticationPrincipal User user,
            @PathVariable Long id,
            @RequestBody(required = false) Map<String, String> payload) { 
        if (user == null) return ResponseEntity.status(HttpStatus.UNAUTHORIZED).build();
        String reason = (payload != null && payload.containsKey("reason")) ? payload.get("reason") : "";
        enrollService.requestEnrollmentCancellation(user, id, reason);
        return ResponseEntity.ok().build(); 
    }

    @Operation(summary = "Process enrollment renewal", description = "수강 재등록(갱신)을 요청합니다.")
    @PostMapping("/renewal")
    public ResponseEntity<EnrollInitiationResponseDto> processRenewal(
            @AuthenticationPrincipal User user,
            @Valid @RequestBody RenewalRequestDto renewalRequestDto) {
        if (user == null) return ResponseEntity.status(HttpStatus.UNAUTHORIZED).build();
        EnrollInitiationResponseDto response = enrollService.processRenewal(user, renewalRequestDto);
        return ResponseEntity.status(HttpStatus.CREATED).body(response);
    }

    @Operation(summary = "Get payment history", description = "사용자의 결제 내역을 조회합니다.")
    @GetMapping("/payment")
    public ResponseEntity<Page<PaymentDto>> getPaymentHistory(
            @AuthenticationPrincipal User user,
            @PageableDefault(size = 10, sort = "paidAt,desc") Pageable pageable) {
        if (user == null) return ResponseEntity.status(HttpStatus.UNAUTHORIZED).build();
        Page<PaymentDto> paymentHistory = mypagePaymentService.getPaymentHistory(user, pageable);
        return ResponseEntity.ok(paymentHistory);
    }

    @Operation(summary = "Request payment cancellation (refund)", description = "특정 결제에 대한 취소(환불)를 요청합니다.")
    @PostMapping("/payment/{id}/cancel")
    public ResponseEntity<Void> requestPaymentCancellation(
            @AuthenticationPrincipal User user,
            @PathVariable Long id) {
        if (user == null) return ResponseEntity.status(HttpStatus.UNAUTHORIZED).build();
        mypagePaymentService.requestPaymentCancellation(user, id);
        return ResponseEntity.ok().build(); 
    }
} 