package egov.com.jwt;

import com.fasterxml.jackson.databind.ObjectMapper;
import cms.common.dto.ApiResponseSchema;
import cms.common.exception.ErrorCode;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.MediaType;
import org.springframework.security.core.AuthenticationException;
import org.springframework.security.web.AuthenticationEntryPoint;
import org.springframework.stereotype.Component;

import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.nio.charset.StandardCharsets;

/**
 * JWT 인증 실패 처리를 담당하는 클래스
 * 인증되지 않은 사용자가 보호된 리소스에 접근할 때 호출됩니다.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class JwtAuthenticationEntryPoint implements AuthenticationEntryPoint {

    private final ObjectMapper objectMapper;

    @Override
    public void commence(HttpServletRequest request, HttpServletResponse response,
            AuthenticationException authException) throws IOException {

        String requestUri = request.getRequestURI();
        log.error("인증되지 않은 접근이 감지되었습니다. URI: {}, 에러: {}", requestUri, authException.getMessage());

        response.setContentType(MediaType.APPLICATION_JSON_VALUE);
        response.setCharacterEncoding(StandardCharsets.UTF_8.name());
        response.setStatus(HttpServletResponse.SC_UNAUTHORIZED);

        // 세션 만료와 인증 실패를 구분하여 처리
        String errorMessage;
        String errorCode;

        if (authException.getMessage() != null && authException.getMessage().contains("expired")) {
            errorMessage = "로그인 세션이 만료되었습니다. 다시 로그인해 주세요.";
            errorCode = ErrorCode.SESSION_EXPIRED.getCode();
        } else {
            errorMessage = "로그인이 필요한 서비스입니다. 로그인 후 이용해 주세요.";
            errorCode = ErrorCode.AUTHENTICATION_FAILED.getCode();
        }

        ApiResponseSchema<Void> apiResponse = ApiResponseSchema.error(
                errorCode,
                errorMessage);

        response.getWriter().write(objectMapper.writeValueAsString(apiResponse));
    }
}