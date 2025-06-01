package cms.auth.provider;

import cms.auth.security.JwtAuthenticationToken;
import cms.user.domain.User;
import cms.user.domain.UserRoleType;
import io.jsonwebtoken.*;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.stereotype.Component;

import javax.servlet.http.HttpServletRequest;
import java.security.Key;
import java.util.Collection;
import java.util.Collections;
import java.util.Date;
import javax.crypto.spec.SecretKeySpec;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import java.nio.charset.StandardCharsets;

@Component
@RequiredArgsConstructor
public class JwtTokenProvider {

    private static final Logger logger = LoggerFactory.getLogger(JwtTokenProvider.class);

    @Value("${jwt.secret}")
    private String secretKey;

    @Value("${jwt.access-token.validity-in-milliseconds:3600000}")
    private long accessTokenValidityInMilliseconds;

    @Value("${jwt.refresh-token.validity-in-milliseconds:2592000000}")
    private long refreshTokenValidityInMilliseconds;

    private static final String AUTHORIZATION_HEADER = "Authorization";
    private static final String BEARER_PREFIX = "Bearer ";
    private static final String TOKEN_TYPE_CLAIM = "type";
    private static final String TOKEN_TYPE_ACCESS = "ACCESS";
    private static final String TOKEN_TYPE_REFRESH = "REFRESH";

    public String createAccessToken(User user) {
        Claims claims = Jwts.claims().setSubject(user.getUsername());
        claims.put("userId", user.getUuid());
        claims.put("role", "ROLE_" + user.getRole().name());
        claims.put("gender", user.getGender());
        claims.put("name", user.getName());
        claims.put("email", user.getEmail());
        claims.put("phone", user.getPhone());
        claims.put(TOKEN_TYPE_CLAIM, TOKEN_TYPE_ACCESS);

        Date now = new Date();
        Date validity = new Date(now.getTime() + accessTokenValidityInMilliseconds);

        return createToken(claims, now, validity);
    }

    public String createRefreshToken(User user) {
        Claims claims = Jwts.claims().setSubject(user.getUsername());
        claims.put("userId", user.getUuid());
        claims.put(TOKEN_TYPE_CLAIM, TOKEN_TYPE_REFRESH);

        Date now = new Date();
        Date validity = new Date(now.getTime() + refreshTokenValidityInMilliseconds);

        return createToken(claims, now, validity);
    }

    private String createToken(Claims claims, Date now, Date validity) {
        Key key = new SecretKeySpec(secretKey.getBytes(StandardCharsets.UTF_8), SignatureAlgorithm.HS256.getJcaName());
        return Jwts.builder()
                .setClaims(claims)
                .setIssuedAt(now)
                .setExpiration(validity)
                .signWith(key, SignatureAlgorithm.HS256)
                .compact();
    }

    public Authentication getAuthentication(String token) {
        logger.debug("=== JwtTokenProvider.getAuthentication Start ===");
        logger.debug("Token to authenticate: {}", token);
        
        Key key = new SecretKeySpec(secretKey.getBytes(StandardCharsets.UTF_8), SignatureAlgorithm.HS256.getJcaName());
     
        Claims claims = Jwts.parserBuilder()
                .setSigningKey(key)
                .build()
                .parseClaimsJws(token)
                .getBody();

        logger.debug("Token claims parsed successfully");
        logger.debug("Subject: {}", claims.getSubject());
        logger.debug("User ID: {}", claims.get("userId"));
        logger.debug("Gender from token: {}", claims.get("gender", String.class));
        logger.debug("Name: {}", claims.get("name"));
        logger.debug("Email: {}", claims.get("email"));
        logger.debug("Phone: {}", claims.get("phone"));
        
        String roleStr = claims.get("role", String.class);
        logger.info("[JwtTokenProvider] Original role string from token: '{}'", roleStr);
        
        Collection<? extends GrantedAuthority> authorities = Collections.singletonList(new SimpleGrantedAuthority(roleStr));
        logger.debug("Created authorities: {}", authorities);

        String processedRoleStr = null;
        if (roleStr != null && roleStr.startsWith("ROLE_")) {
            processedRoleStr = roleStr.substring(5);
        } else {
            processedRoleStr = roleStr;
        }
        logger.info("[JwtTokenProvider] Processed role string for Enum.valueOf(): '{}'", processedRoleStr);
        
        UserRoleType userRoleTypeEnum;
        try {
            userRoleTypeEnum = UserRoleType.valueOf(processedRoleStr);
        } catch (IllegalArgumentException e) {
            logger.error("[JwtTokenProvider] Failed to convert processed role string '{}' to UserRoleType enum. Error: {}", processedRoleStr, e.getMessage());
            throw e;
        }

        UserDetails principal = User.builder()
                .uuid(claims.get("userId", String.class))
                .username(claims.getSubject())
                .role(userRoleTypeEnum)
                .status("ACTIVE")
                .password("")
                .email(claims.get("email", String.class))
                .name(claims.get("name", String.class))
                .gender(claims.get("gender", String.class))
                .phone(claims.get("phone", String.class))
                .build();
        logger.debug("Created UserDetails: {}, with gender: {}, name: '{}', email: '{}', phone: '{}'", 
            principal, ((User)principal).getGender(), ((User)principal).getName(), ((User)principal).getEmail(), ((User)principal).getPhone());

        JwtAuthenticationToken authentication = new JwtAuthenticationToken(principal, token, authorities, token);
        logger.debug("Created JwtAuthenticationToken: {}", authentication);
        logger.debug("=== JwtTokenProvider.getAuthentication End ===");
        
        return authentication;
    }

    public String resolveToken(HttpServletRequest request) {
        String bearerToken = request.getHeader(AUTHORIZATION_HEADER);
        if (bearerToken != null && bearerToken.startsWith(BEARER_PREFIX)) {
            return bearerToken.substring(BEARER_PREFIX.length());
        }
        return null;
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
            logger.debug("Name: {}", claims.get("name"));
            logger.debug("Email: {}", claims.get("email"));
            logger.debug("Phone: {}", claims.get("phone"));
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
                logger.error("Missing required claims in token (userId or role)");
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
        } catch (SignatureException e) {
            logger.error("JWT signature validation failed: {}", e.getMessage());
            throw new JwtException("토큰 서명이 유효하지 않습니다.");
        } catch (JwtException | IllegalArgumentException e) {
            logger.error("JWT Token validation failed: {}", e.getMessage());
            throw new JwtException("유효하지 않은 토큰입니다.");
        }
    }

    public String getTokenType(String token) {
        try {
            Key key = new SecretKeySpec(secretKey.getBytes(StandardCharsets.UTF_8), SignatureAlgorithm.HS256.getJcaName());
            Claims claims = Jwts.parserBuilder()
                    .setSigningKey(key)
                    .build()
                    .parseClaimsJws(token)
                    .getBody();
            return claims.get(TOKEN_TYPE_CLAIM, String.class);
        } catch (JwtException | IllegalArgumentException e) {
            return null;
        }
    }

    public boolean isRefreshToken(String token) {
        return TOKEN_TYPE_REFRESH.equals(getTokenType(token));
    }

    public boolean isAccessToken(String token) {
        return TOKEN_TYPE_ACCESS.equals(getTokenType(token));
    }
} 