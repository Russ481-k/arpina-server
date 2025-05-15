package cms.common.aspect;

import javax.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import org.aspectj.lang.JoinPoint;
import org.aspectj.lang.annotation.AfterReturning;
import org.aspectj.lang.annotation.AfterThrowing;
import org.aspectj.lang.annotation.Aspect;
import org.aspectj.lang.annotation.Pointcut;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

import cms.user.service.UserActivityLogService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import java.util.UUID;

@Aspect
@Component
@RequiredArgsConstructor
public class LoggingAspect {

    private final UserActivityLogService userActivityLogService;
    private static final Logger log = LoggerFactory.getLogger(LoggingAspect.class);

    @Pointcut("execution(* cms..*Controller.*(..))")
    public void controllerPointcut() {}

    @AfterReturning(pointcut = "controllerPointcut()", returning = "result")
    public void logAfterReturning(JoinPoint joinPoint, Object result) {
        logActivity(joinPoint, "SUCCESS", null);
    }

    @AfterThrowing(pointcut = "controllerPointcut()", throwing = "error")
    public void logAfterThrowing(JoinPoint joinPoint, Throwable error) {
        logActivity(joinPoint, "ERROR", error.getMessage());
    }

    public void logActivity(JoinPoint joinPoint, String action, String errorMessage) {
        try {
            HttpServletRequest request = ((ServletRequestAttributes) RequestContextHolder.currentRequestAttributes()).getRequest();
            Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
            
            if (authentication != null && authentication.isAuthenticated()) {
                String userId = authentication.getName();
                String methodName = joinPoint.getSignature().getName();
                String className = joinPoint.getTarget().getClass().getSimpleName();
                String description = String.format("%s.%s", className, methodName);
                
                if (errorMessage != null) {
                    description += " - Error: " + errorMessage;
                }

                // 기본 그룹과 조직 ID 설정
                String defaultGroupId = "00000000-0000-0000-0000-000000000000";  // 기본 그룹 UUID
                String defaultOrgId = "00000000-0000-0000-0000-000000000000";    // 기본 조직 UUID

                userActivityLogService.logActivity(
                    UUID.randomUUID().toString(), // 로그 엔트리의 UUID
                    userId, // 사용자 UUID
                    defaultGroupId,
                    defaultOrgId,
                    action,
                    description,
                    request.getHeader("User-Agent"),
                    userId,
                    request.getRemoteAddr()
                );
            }
        } catch (Exception e) {
            log.error("Failed to log activity: {}", e.getMessage());
        }
    }

    private String getClientIp() {
        HttpServletRequest request = ((ServletRequestAttributes) RequestContextHolder.currentRequestAttributes()).getRequest();
        return request.getRemoteAddr();
    }

    private String getClientUserAgent() {
        HttpServletRequest request = ((ServletRequestAttributes) RequestContextHolder.currentRequestAttributes()).getRequest();
        return request.getHeader("User-Agent");
    }

    private void logActivity(String activityType, String description) {
        try {
            String userId = SecurityContextHolder.getContext().getAuthentication().getName();
            String ipAddress = getClientIp();
            String userAgent = getClientUserAgent();
            
            // 기본값 설정
            String groupId = "00000000-0000-0000-0000-000000000000";
            String organizationId = "00000000-0000-0000-0000-000000000000";
            
            userActivityLogService.logActivity(
                UUID.randomUUID().toString(), // 로그 엔트리의 UUID
                userId, // 사용자 UUID
                groupId,
                organizationId,
                activityType,
                description,
                userAgent,
                userId,
                ipAddress
            );
        } catch (Exception e) {
            log.error("Failed to log activity: {}", e.getMessage());
        }
    }
} 