package egov.com.jwt;

import java.security.Key;
import java.nio.charset.StandardCharsets;
import javax.crypto.spec.SecretKeySpec;
import io.jsonwebtoken.Claims;
import io.jsonwebtoken.ExpiredJwtException;
import io.jsonwebtoken.JwtException;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.MalformedJwtException;
import io.jsonwebtoken.SignatureAlgorithm;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class JwtTokenProvider {

    private static final Logger logger = LoggerFactory.getLogger(JwtTokenProvider.class);

    private final String secretKey;
    private final String TOKEN_TYPE_ACCESS = "ACCESS";
    private final String TOKEN_TYPE_CLAIM = "type";

    public JwtTokenProvider(String secretKey) {
        this.secretKey = secretKey;
    }

    public boolean validateToken(String token) {
        logger.debug("=== JwtTokenProvider.validateToken Start ===");
        logger.debug("Token to validate: {}", token);
        
        if (token == null || token.trim().isEmpty()) {
            logger.error("Token is null or empty");
            throw new JwtException("토큰이 비어있습니다.");
        }

        try {
            Key key = new SecretKeySpec(secretKey.getBytes(StandardCharsets.UTF_8), SignatureAlgorithm.HS256.getJcaName());
            logger.debug("Using secret key for validation");
            
            Claims claims = Jwts.parserBuilder()
                .setSigningKey(key)
                .build()
                .parseClaimsJws(token.trim())
                .getBody();
            
            logger.debug("Token validation successful");
            logger.debug("Subject: {}", claims.getSubject());
            logger.debug("User ID: {}", claims.get("userId"));
            logger.debug("Role: {}", claims.get("role"));
            logger.debug("Token type: {}", claims.get(TOKEN_TYPE_CLAIM));
            logger.debug("Issued at: {}", claims.getIssuedAt());
            logger.debug("Expiration: {}", claims.getExpiration());
            
            // Validate token type (case-insensitive)
            String tokenType = claims.get(TOKEN_TYPE_CLAIM, String.class);
            if (tokenType == null || !TOKEN_TYPE_ACCESS.equalsIgnoreCase(tokenType)) {
                logger.error("Invalid token type: {}", tokenType);
                throw new JwtException("잘못된 토큰 타입입니다.");
            }
            
            // Validate required claims
            if (claims.get("userId") == null || claims.get("role") == null) {
                logger.error("Missing required claims in token");
                throw new JwtException("토큰에 필수 정보가 없습니다.");
            }
            
            logger.debug("=== JwtTokenProvider.validateToken End ===");
            return true;
        } catch (ExpiredJwtException e) {
            logger.error("JWT Token has expired: {}", e.getMessage());
            logger.error("Expiration time: {}", e.getClaims().getExpiration());
            throw new JwtException("토큰이 만료되었습니다.");
        } catch (MalformedJwtException e) {
            logger.error("Invalid JWT token format: {}", e.getMessage());
            throw new JwtException("잘못된 형식의 토큰입니다.");
        } catch (JwtException e) {
            logger.error("JWT signature validation failed: {}", e.getMessage());
            throw new JwtException("토큰 서명이 유효하지 않습니다.");
        } catch (IllegalArgumentException e) {
            logger.error("JWT Token validation failed: {}", e.getMessage());
            throw new JwtException("유효하지 않은 토큰입니다.");
        }
    }
} 