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
import org.springframework.security.web.util.matcher.RequestMatcher;

@Component
public class JwtRequestFilter extends OncePerRequestFilter {

    private final JwtTokenProvider jwtTokenProvider;
    private final RequestMatcher permitAllRequestMatcher;
    private static final Logger log = LoggerFactory.getLogger(JwtRequestFilter.class);

    @Autowired
    public JwtRequestFilter(JwtTokenProvider jwtTokenProvider, RequestMatcher permitAllRequestMatcher) {
        this.jwtTokenProvider = jwtTokenProvider;
        this.permitAllRequestMatcher = permitAllRequestMatcher;
    }

    @Override
    protected boolean shouldNotFilter(HttpServletRequest request) throws ServletException {
        String requestURI = request.getRequestURI();
        boolean shouldNotFilter = permitAllRequestMatcher.matches(request);
        if (shouldNotFilter) {
            log.info("[JwtRequestFilter] Skipping filter for permitAll path: {} (matches() returned true)", requestURI);
        } else {
            log.info("[JwtRequestFilter] NOT skipping filter for path: {} (matches() returned false)", requestURI);
        }
        return shouldNotFilter;
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain chain)
            throws ServletException, IOException {
        
        String requestURI = request.getRequestURI();
        log.info("[JwtRequestFilter] ACTUALLY FILTERING URI: {} (shouldNotFilter must have returned false)", requestURI);
        
        final String requestTokenHeader = request.getHeader("Authorization");
        
        if (requestTokenHeader != null && requestTokenHeader.startsWith("Bearer ")) {
            String token = requestTokenHeader.substring(7).trim();
            log.debug("Extracted token from header for URI: {}", requestURI);
            
            try {
                log.debug("Validating token for URI: {}...", requestURI);
                if (jwtTokenProvider.validateToken(token)) {
                    log.debug("Token validation successful for URI: {}, creating authentication...", requestURI);
                    Authentication authentication = jwtTokenProvider.getAuthentication(token);
                    if (authentication != null) {
                        log.debug("Authentication created successfully for user: {} on URI: {}", authentication.getName(), requestURI);
                        SecurityContextHolder.getContext().setAuthentication(authentication);
                        log.debug("Security context updated with authentication for URI: {}", requestURI);
                    } else {
                        log.warn("Failed to create authentication object for token on URI: {}", requestURI);
                        SecurityContextHolder.clearContext(); 
                    }
                } else {
                    log.warn("Token validation failed (validateToken returned false) for URI: {}", requestURI);
                    SecurityContextHolder.clearContext();
                    sendErrorResponse(response, "유효하지 않은 토큰입니다.");
                    return;
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
            log.debug("No Bearer token found for (presumably) authenticated path URI: {}. Passing to next filter.", requestURI);
        }
        
        log.debug("Proceeding with filter chain for URI: {}", requestURI);
        chain.doFilter(request, response);
        log.debug("=== JwtRequestFilter End for URI: {} ===", requestURI);
    }

    private void sendErrorResponse(HttpServletResponse response, String message) throws IOException {
        response.setStatus(HttpServletResponse.SC_UNAUTHORIZED);
        response.setContentType("application/json");
        response.setCharacterEncoding("UTF-8");
        
        String timestamp = java.time.LocalDateTime.now().format(java.time.format.DateTimeFormatter.ISO_LOCAL_DATE_TIME);
        
        String errorResponse = String.format(
            "{\"status\":%d,\"message\":\"%s\",\"timestamp\":\"%s\"}",
            HttpServletResponse.SC_UNAUTHORIZED,
            message,
            timestamp
        );
        
        log.debug("Sending error response: {}", errorResponse);
        response.getWriter().write(errorResponse);
    }
} 
 
 
 