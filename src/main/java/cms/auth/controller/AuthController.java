package cms.auth.controller;

import cms.user.dto.CustomUserDetails;
import cms.user.domain.User;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.web.bind.annotation.*;

import cms.auth.dto.LoginRequest;
import cms.auth.dto.ResetPasswordRequest;
import cms.auth.dto.SignupRequest;
import cms.auth.dto.UserRegistrationRequest;
import cms.auth.service.AuthService;
import cms.common.dto.ApiResponseSchema;
import cms.auth.dto.SendEmailVerificationRequestDto;
import cms.auth.dto.VerifyEmailRequestDto;
import cms.auth.service.VerificationCodeService;
import cms.common.service.EmailService;
import cms.user.repository.UserRepository;

import javax.servlet.http.HttpServletRequest;
import javax.validation.Valid;
import java.util.Map;
import java.util.HashMap;
import org.springframework.security.core.Authentication;
import org.springframework.http.HttpStatus;

@Slf4j
@RestController
@RequestMapping("/auth")
@RequiredArgsConstructor
@Tag(name = "cmm_00_Auth", description = "인증 관련 API")
public class AuthController {

    private final AuthService authService;
    private final UserRepository userRepository;
    private final EmailService emailService;
    private final VerificationCodeService verificationCodeService;

    @PostMapping("/signup")
    @Operation(summary = "일반 사용자 회원가입", description = "새로운 일반 사용자를 등록합니다.")
    @ApiResponses(value = {
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "200", description = "회원가입 성공"),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "400", description = "잘못된 요청"),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "409", description = "이미 존재하는 사용자")
    })
    public ResponseEntity<ApiResponseSchema<Void>> signup(@Valid @RequestBody SignupRequest request) {
        authService.signup(request);
        return ResponseEntity.ok(ApiResponseSchema.success("회원가입이 성공적으로 완료되었습니다."));
    }

    @PostMapping("/register")
    @Operation(summary = "사용자 등록", description = "새로운 사용자를 등록합니다.")
    @ApiResponses(value = {
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "200", description = "등록 성공"),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "400", description = "잘못된 요청"),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "409", description = "이미 존재하는 사용자")
    })
    public ResponseEntity<ApiResponseSchema<Void>> registerUser(@Valid @RequestBody UserRegistrationRequest request) {
        authService.registerUser(request);
        return ResponseEntity.ok(ApiResponseSchema.success("사용자가 성공적으로 등록되었습니다."));
    }

    @PostMapping("/login")
    @Operation(summary = "사용자 로그인", description = "사용자 로그인을 처리하고 JWT 토큰을 반환합니다.")
    @ApiResponses(value = {
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "200", description = "로그인 성공"),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "401", description = "인증 실패")
    })
    public ResponseEntity<ApiResponseSchema<Map<String, Object>>> login(@Valid @RequestBody LoginRequest request) {
        return authService.loginUser(request);
    }

    @PostMapping("/logout")
    @Operation(summary = "사용자 로그아웃", description = "사용자를 로그아웃 처리합니다.")
    @SecurityRequirement(name = "bearerAuth")
    @ApiResponses(value = {
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "200", description = "로그아웃 성공"),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "401", description = "인증 실패")
    })
    public ResponseEntity<ApiResponseSchema<Void>> logout(HttpServletRequest request) {
        authService.logout(request);
        return ResponseEntity.ok(ApiResponseSchema.success("로그아웃 성공"));
    }

    @GetMapping("/verify")
    @Operation(summary = "토큰 검증", description = "JWT 토큰의 유효성을 검증합니다.")
    @ApiResponses(value = {
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "200", description = "유효한 토큰"),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "401", description = "유효하지 않은 토큰")
    })
    public ResponseEntity<ApiResponseSchema<Map<String, Object>>> verifyToken(HttpServletRequest request) {
        String authHeader = request.getHeader("Authorization");
        Map<String, Object> response = new HashMap<>();

        if (authHeader == null || !authHeader.startsWith("Bearer ")) {
            response.put("valid", false);
            response.put("error", "인증 토큰이 필요합니다.");
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
                    .body(ApiResponseSchema.error(response, "인증 토큰이 필요합니다."));
        }

        String token = authHeader.substring(7).trim();

        try {
            Authentication auth = authService.verifyToken(token);
            UserDetails userDetails = (UserDetails) auth.getPrincipal();
            if (userDetails instanceof User) {
                User user = (User) userDetails;
                response.put("uuid", user.getUuid());
            }
            response.put("valid", true);
            response.put("username", auth.getName());
            response.put("authorities", auth.getAuthorities());
            return ResponseEntity.ok(ApiResponseSchema.success(response));
        } catch (IllegalArgumentException e) {
            response.put("valid", false);
            response.put("error", e.getMessage());
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
                    .body(ApiResponseSchema.error(response, e.getMessage()));
        }
    }

    @PostMapping("/reset-password")
    @Operation(summary = "비밀번호 재설정", description = "사용자의 비밀번호를 재설정합니다.")
    @SecurityRequirement(name = "bearerAuth")
    @ApiResponses(value = {
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "200", description = "비밀번호 재설정 성공"),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "401", description = "인증 실패")
    })
    public ResponseEntity<ApiResponseSchema<Void>> resetPassword(
            @Valid @RequestBody ResetPasswordRequest request,
            @AuthenticationPrincipal UserDetails userDetails) {
        try {
            authService.resetPassword(request, userDetails);
            return ResponseEntity.ok(ApiResponseSchema.success("비밀번호가 성공적으로 재설정되었습니다."));
        } catch (Exception e) {
            return ResponseEntity.badRequest().body(ApiResponseSchema.error("400", e.getMessage()));
        }
    }

    @Operation(summary = "SNS 로그인", description = "SNS 로그인 처리")
    @ApiResponses(value = {
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "200", description = "로그인 성공"),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "400", description = "잘못된 요청"),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "401", description = "인증 실패")
    })
    @PostMapping("/sns/{provider}")
    public ResponseEntity<ApiResponseSchema<Map<String, Object>>> snsLogin(
            @PathVariable String provider,
            @RequestBody Map<String, String> token) {
        return authService.snsLogin(provider, token.get("token"));
    }

    @PostMapping("/refresh-token")
    @Operation(summary = "토큰 갱신", description = "리프레시 토큰을 사용하여 새로운 액세스 토큰을 발급받습니다.")
    @ApiResponses(value = {
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "200", description = "토큰 갱신 성공"),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "400", description = "잘못된 요청"),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "401", description = "유효하지 않은 리프레시 토큰")
    })
    public ResponseEntity<ApiResponseSchema<Map<String, String>>> refreshToken(HttpServletRequest request) {
        String refreshToken = request.getHeader("Authorization");
        return authService.refreshToken(refreshToken);
    }

    @Operation(summary = "비밀번호 재설정 요청", description = "비밀번호 재설정 이메일 발송")
    @ApiResponses(value = {
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "200", description = "이메일 발송 성공"),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "400", description = "잘못된 요청"),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "404", description = "사용자를 찾을 수 없음")
    })
    @PostMapping("/password/reset")
    public ResponseEntity<ApiResponseSchema<Void>> requestPasswordReset(@RequestBody Map<String, String> request) {
        authService.requestPasswordReset(request.get("email"));
        return ResponseEntity.ok(ApiResponseSchema.success("비밀번호 재설정 이메일이 성공적으로 발송되었습니다."));
    }

    @Operation(summary = "비밀번호 변경", description = "사용자 비밀번호 변경")
    @ApiResponses(value = {
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "200", description = "변경 성공"),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "400", description = "잘못된 요청"),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "401", description = "인증 실패")
    })
    @SecurityRequirement(name = "Authorization")
    @PostMapping("/password/change")
    public ResponseEntity<ApiResponseSchema<Void>> changePassword(
            @RequestBody Map<String, String> passwordMap,
            @Parameter(hidden = true) @AuthenticationPrincipal CustomUserDetails user) {
        authService.changePassword(passwordMap, user);
        return ResponseEntity.ok(ApiResponseSchema.success("비밀번호가 성공적으로 변경되었습니다."));
    }

    @Operation(summary = "사용자 ID 중복 체크", description = "회원가입 시 사용자 ID 사용 가능 여부를 확인합니다.")
    @ApiResponses(value = {
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "200", description = "확인 성공 (available: true/false)")
    })
    @GetMapping("/check-username/{username}")
    public ResponseEntity<ApiResponseSchema<Map<String, Object>>> checkUsernameAvailability(
            @PathVariable String username) {
        return authService.checkUsernameAvailability(username);
    }

    @PostMapping("/send-verification-email")
    @Operation(summary = "회원가입 이메일 인증번호 발송", description = "이메일로 6자리 인증번호를 발송하고 3분간 유효합니다.")
    @ApiResponses(value = {
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "200", description = "인증 코드 발송 성공"),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "400", description = "잘못된 이메일 형식"),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "409", description = "이미 가입된 이메일"),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "500", description = "이메일 발송 시스템 오류")
    })
    public ResponseEntity<ApiResponseSchema<Void>> sendVerificationEmail(
            @Valid @RequestBody SendEmailVerificationRequestDto requestDto) {
        if (userRepository.existsByEmail(requestDto.getEmail())) {
            return ResponseEntity.status(HttpStatus.CONFLICT)
                    .body(ApiResponseSchema.error("409", "이미 가입된 이메일입니다."));
        }

        try {
            String code = verificationCodeService.generateAndStoreCode(requestDto.getEmail());
            emailService.sendVerificationEmail(requestDto.getEmail(), code);
            return ResponseEntity.ok(ApiResponseSchema.success("인증 코드가 이메일로 발송되었습니다."));
        } catch (Exception e) {
            log.error("Email sending failed for {}: {}", requestDto.getEmail(), e.getMessage());
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(ApiResponseSchema.error("500", "이메일 발송 중 오류가 발생했습니다."));
        }
    }

    @PostMapping("/verify-email-code")
    @Operation(summary = "이메일 인증번호 확인", description = "사용자가 입력한 인증번호를 검증합니다.")
    @ApiResponses(value = {
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "200", description = "이메일 인증 성공"),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "400", description = "인증번호 불일치"),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "410", description = "인증번호 만료")
    })
    public ResponseEntity<ApiResponseSchema<Void>> verifyEmailCode(
            @Valid @RequestBody VerifyEmailRequestDto requestDto) {
        boolean isValid = verificationCodeService.verifyCode(requestDto.getEmail(), requestDto.getCode());

        if (isValid) {
            return ResponseEntity.ok(ApiResponseSchema.success("이메일 인증이 완료되었습니다."));
        } else {
            return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                    .body(ApiResponseSchema.error("400", "인증번호가 일치하지 않거나 만료되었습니다."));
        }
    }
}
