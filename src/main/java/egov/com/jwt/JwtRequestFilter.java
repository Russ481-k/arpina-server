package egov.com.jwt;

import javax.servlet.FilterChain;
import javax.servlet.ServletException;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;
import cms.auth.provider.JwtTokenProvider;
import io.jsonwebtoken.ExpiredJwtException;
import io.jsonwebtoken.JwtException;

import java.io.IOException;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;

import org.springframework.security.core.Authentication;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

@Component
public class JwtRequestFilter extends OncePerRequestFilter {

    private final JwtTokenProvider jwtTokenProvider;
    private static final Logger log = LoggerFactory.getLogger(JwtRequestFilter.class);

    @Autowired
    public JwtRequestFilter(JwtTokenProvider jwtTokenProvider) {
        this.jwtTokenProvider = jwtTokenProvider;
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain chain)
            throws ServletException, IOException {
        
        String requestURI = request.getRequestURI();
        log.debug("=== JwtRequestFilter Start for URI: {} ===", requestURI);
        
        final String requestTokenHeader = request.getHeader("Authorization");
        
        if (requestTokenHeader != null && requestTokenHeader.startsWith("Bearer ")) {
            String token = requestTokenHeader.substring(7).trim();
            log.debug("Extracted token from header");
            
            try {
                log.debug("Validating token...");
                if (jwtTokenProvider.validateToken(token)) {
                    log.debug("Token validation successful, creating authentication...");
                    Authentication authentication = jwtTokenProvider.getAuthentication(token);
                    if (authentication != null) {
                        log.debug("Authentication created successfully for user: {}", authentication.getName());
                        SecurityContextHolder.getContext().setAuthentication(authentication);
                        log.debug("Security context updated with authentication");
                    } else {
                        log.warn("Failed to create authentication object for token");
                    }
                } else {
                    log.warn("Token validation failed for URI: {}", requestURI);
                    SecurityContextHolder.clearContext();
                }
            } catch (ExpiredJwtException e) {
                log.warn("Token expired for URI: {} - Expiration: {}", requestURI, e.getClaims().getExpiration());
                SecurityContextHolder.clearContext();
                sendErrorResponse(response, "만료된 토큰입니다.");
                return;
            } catch (JwtException e) {
                log.warn("Token validation error for URI {}: {}", requestURI, e.getMessage());
                SecurityContextHolder.clearContext();
                sendErrorResponse(response, "토큰 검증에 실패했습니다: " + e.getMessage());
                return;
            }
        } else {
            log.debug("No Bearer token found in Authorization header or header is missing.");
        }
        
        log.debug("Proceeding with filter chain for URI: {}", requestURI);
        chain.doFilter(request, response);
        log.debug("=== JwtRequestFilter End for URI: {} ===", requestURI);
    }

    private void sendErrorResponse(HttpServletResponse response, String message) throws IOException {
        response.setStatus(HttpServletResponse.SC_UNAUTHORIZED);
        response.setContentType("application/json");
        response.setCharacterEncoding("UTF-8");
        
        String errorResponse = String.format(
            "{\"status\":%d,\"message\":\"%s\",\"timestamp\":\"%s\"}",
            HttpServletResponse.SC_UNAUTHORIZED,
            message,
            LocalDateTime.now().format(DateTimeFormatter.ISO_LOCAL_DATE_TIME)
        );
        
        log.debug("Sending error response: {}", errorResponse);
        response.getWriter().write(errorResponse);
    }
} 
 
 
 