package cms.auth.service.impl;

import cms.user.dto.CustomUserDetails;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.http.HttpStatus;

import cms.auth.provider.JwtTokenProvider;
import cms.auth.dto.LoginRequest;
import cms.auth.dto.ResetPasswordRequest;
import cms.auth.dto.UserRegistrationRequest;
import cms.auth.service.AuthService;
import cms.user.domain.User;
import cms.user.repository.UserRepository;
import cms.common.dto.ApiResponseSchema;

import org.springframework.mail.MailException;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.mail.javamail.MimeMessageHelper;

import javax.mail.MessagingException;
import javax.mail.internet.MimeMessage;
import javax.servlet.http.HttpServletRequest;
import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

import io.jsonwebtoken.ExpiredJwtException;
import io.jsonwebtoken.JwtException;

@Service
@RequiredArgsConstructor
public class AuthServiceImpl implements AuthService {

    private final AuthenticationManager authenticationManager;
    private final JwtTokenProvider jwtTokenProvider;
    private final PasswordEncoder passwordEncoder;
    private final JavaMailSender mailSender;
    private final UserRepository userRepository;

    @Override
    @Transactional
    public ResponseEntity<ApiResponseSchema<CustomUserDetails>> register(CustomUserDetails userDetails) {
        User user = userDetails.getUser();
        user.setPassword(passwordEncoder.encode(user.getPassword()));
        user = userRepository.save(user);
        return ResponseEntity.ok(ApiResponseSchema.success(new CustomUserDetails(user), "사용자가 성공적으로 등록되었습니다."));
    }

    @Override
    public ResponseEntity<ApiResponseSchema<Map<String, Object>>> login(CustomUserDetails userDetails) {
        Map<String, Object> result = new HashMap<>();
        
        try {
            Authentication authentication = authenticationManager.authenticate(
                new UsernamePasswordAuthenticationToken(userDetails.getUsername(), userDetails.getPassword())
            );
            SecurityContextHolder.getContext().setAuthentication(authentication);

            CustomUserDetails customUserDetails = (CustomUserDetails) authentication.getPrincipal();
            String accessToken = jwtTokenProvider.createAccessToken(customUserDetails.getUser());
            String refreshToken = jwtTokenProvider.createRefreshToken(customUserDetails.getUser());
            
            result.put("accessToken", accessToken);
            result.put("refreshToken", refreshToken);
            result.put("user", customUserDetails);
            result.put("status", "success");
            
            return ResponseEntity.ok(ApiResponseSchema.success(result, "로그인이 성공적으로 완료되었습니다."));
        } catch (Exception e) {
            result.put("status", "fail");
            result.put("message", "아이디 또는 비밀번호가 일치하지 않습니다.");
            return ResponseEntity.ok(ApiResponseSchema.error(result, "로그인에 실패했습니다.", "AUTH_001"));
        }
    }

    @Override
    public ResponseEntity<ApiResponseSchema<Map<String, Object>>> snsLogin(String provider, String token) {
        Map<String, Object> result = new HashMap<>();
        result.put("status", "success");
        return ResponseEntity.ok(ApiResponseSchema.success(result, "SNS 로그인이 성공적으로 완료되었습니다."));
    }

    @Override
    public ResponseEntity<ApiResponseSchema<Void>> logout(HttpServletRequest request) {
        String token = jwtTokenProvider.resolveToken(request);
        if (token != null) {
            // TODO: 토큰 무효화 로직 구현
        }
        SecurityContextHolder.clearContext();
        return ResponseEntity.ok(ApiResponseSchema.success("로그아웃이 완료되었습니다."));
    }

    @Override
    public Authentication verifyToken(String token) {
        if (token == null) {
            throw new IllegalArgumentException("토큰이 없습니다.");
        }

        try {
            if (!jwtTokenProvider.validateToken(token)) {
                throw new IllegalArgumentException("유효하지 않은 토큰입니다.");
            }
            return jwtTokenProvider.getAuthentication(token);
        } catch (ExpiredJwtException e) {
            throw new IllegalArgumentException("토큰이 만료되었습니다.");
        } catch (JwtException e) {
            throw new IllegalArgumentException("토큰 검증에 실패했습니다: " + e.getMessage());
        }
    }

    @Override
    public ResponseEntity<ApiResponseSchema<Map<String, String>>> refreshToken(String refreshToken) {
        Map<String, String> result = new HashMap<>();
        try {
            // TODO: 리프레시 토큰 검증 및 새로운 액세스 토큰 발급 로직 구현
            return ResponseEntity.ok(ApiResponseSchema.success(result, "토큰이 성공적으로 갱신되었습니다."));
        } catch (Exception e) {
            return ResponseEntity.ok(ApiResponseSchema.error("400", "토큰 갱신에 실패했습니다."));
        }
    }

    @Override
    public ResponseEntity<ApiResponseSchema<Void>> requestPasswordReset(String email) {
        User user = userRepository.findByEmail(email)
                .orElseThrow(() -> new IllegalArgumentException("해당 이메일로 등록된 사용자가 없습니다."));

        if (!user.getStatus().equals("ACTIVE")) {
            throw new IllegalArgumentException("비활성화된 계정입니다.");
        }

        String resetToken = UUID.randomUUID().toString();
        user.setResetToken(resetToken);
        user.setResetTokenExpiry(LocalDateTime.now().plusHours(24));
        userRepository.save(user);

        try {
            sendResetEmail(user.getEmail(), resetToken);
        } catch (MailException e) {
            throw new IllegalArgumentException("이메일 전송에 실패했습니다.", e);
        }
        
        return ResponseEntity.ok(ApiResponseSchema.success("비밀번호 재설정 이메일이 전송되었습니다."));
    }

    private void sendResetEmail(String email, String resetToken) {
        MimeMessage message = mailSender.createMimeMessage();
        try {
            MimeMessageHelper helper = new MimeMessageHelper(message, true);
            helper.setTo(email);
            helper.setSubject("비밀번호 재설정");
            helper.setText("비밀번호 재설정을 위해 다음 링크를 클릭하세요: " + resetToken);
            mailSender.send(message);
        } catch (MessagingException e) {
            throw new IllegalArgumentException("이메일 전송에 실패했습니다.", e);
        }
    }

    @Override
    public ResponseEntity<ApiResponseSchema<Void>> resetPassword(ResetPasswordRequest request, UserDetails userDetails) {
        User user = userRepository.findByUsername(userDetails.getUsername())
                .orElseThrow(() -> new IllegalArgumentException("사용자를 찾을 수 없습니다."));

        if (!passwordEncoder.matches(request.getCurrentPassword(), user.getPassword())) {
            throw new IllegalArgumentException("현재 비밀번호가 일치하지 않습니다.");
        }

        if (!request.getNewPassword().equals(request.getConfirmPassword())) {
            throw new IllegalArgumentException("새 비밀번호와 확인 비밀번호가 일치하지 않습니다.");
        }

        user.setPassword(passwordEncoder.encode(request.getNewPassword()));
        userRepository.save(user);

        return ResponseEntity.ok(ApiResponseSchema.success("비밀번호가 성공적으로 변경되었습니다."));
    }

    @Override
    public ResponseEntity<ApiResponseSchema<Void>> changePassword(Map<String, String> passwordMap, CustomUserDetails user) {
        User existingUser = userRepository.findByUsername(user.getUsername())
                .orElseThrow(() -> new IllegalArgumentException("사용자를 찾을 수 없습니다."));

        if (!passwordEncoder.matches(passwordMap.get("currentPassword"), existingUser.getPassword())) {
            throw new IllegalArgumentException("현재 비밀번호가 일치하지 않습니다.");
        }

        existingUser.setPassword(passwordEncoder.encode(passwordMap.get("newPassword")));
        userRepository.save(existingUser);

        return ResponseEntity.ok(ApiResponseSchema.success("비밀번호가 성공적으로 변경되었습니다."));
    }

    @Override
    public ResponseEntity<ApiResponseSchema<Map<String, Object>>> loginUser(LoginRequest request) {
        try {
            // 사용자 인증
            Authentication authentication = authenticationManager.authenticate(
                new UsernamePasswordAuthenticationToken(request.getUsername(), request.getPassword())
            );
            SecurityContextHolder.getContext().setAuthentication(authentication);

            // 사용자 정보 조회
            User user = userRepository.findByUsername(request.getUsername())
                .orElseThrow(() -> new IllegalArgumentException("사용자를 찾을 수 없습니다."));

            // JWT 토큰 생성
            String accessToken = jwtTokenProvider.createAccessToken(user);
            String refreshToken = jwtTokenProvider.createRefreshToken(user);

            // 마지막 로그인 시간 업데이트
            user.setUpdatedAt(LocalDateTime.now());
            userRepository.save(user);

            // 응답 데이터 구성
            Map<String, Object> result = new HashMap<>();
            result.put("accessToken", accessToken);
            result.put("refreshToken", refreshToken);
            result.put("tokenType", "Bearer");
            result.put("user", new HashMap<String, Object>() {{
                put("uuid", user.getUuid());
                put("username", user.getUsername());
                put("role", user.getRole());
            }});

            return ResponseEntity.ok(ApiResponseSchema.success(result, "로그인이 성공적으로 완료되었습니다."));
        } catch (BadCredentialsException e) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
                .body(ApiResponseSchema.error("401", "아이디 또는 비밀번호가 일치하지 않습니다."));
        } catch (Exception e) {
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                .body(ApiResponseSchema.error("500", "로그인 처리 중 오류가 발생했습니다."));
        }
    }

    @Override
    public ResponseEntity<ApiResponseSchema<Void>> registerUser(UserRegistrationRequest request) {
        User user = User.builder()
                .username(request.getUsername())
                .password(passwordEncoder.encode(request.getPassword()))
                .email(request.getEmail())
                .name(request.getName())
                .role(request.getRole())
                .status("ACTIVE")
                .build();

        userRepository.save(user);
        return ResponseEntity.ok(ApiResponseSchema.success("사용자가 성공적으로 등록되었습니다."));
    }

    @Override
    public ResponseEntity<ApiResponseSchema<Void>> logoutUser(HttpServletRequest request) {
        SecurityContextHolder.clearContext();
        return ResponseEntity.ok(ApiResponseSchema.success("로그아웃이 완료되었습니다."));
    }
} 
 
 
 