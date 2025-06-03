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
import cms.user.domain.User;
import cms.user.repository.UserRepository;
import cms.user.dto.CustomUserDetails;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import java.util.UUID;

@Aspect
@Component
@RequiredArgsConstructor
public class LoggingAspect {

    private final UserActivityLogService userActivityLogService;
    private final UserRepository userRepository;
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
                String userUuid = null;
                Object principal = authentication.getPrincipal();

                if (principal instanceof CustomUserDetails) {
                    userUuid = ((CustomUserDetails) principal).getUser().getUuid();
                } else if (principal instanceof User) {
                    userUuid = ((User) principal).getUuid();
                } else if (principal instanceof String) {
                    String username = (String) principal;
                    User user = userRepository.findByUsername(username)
                                    .orElse(null);
                    if (user != null) {
                        userUuid = user.getUuid();
                    } else {
                        log.warn("User not found with username: {} during activity logging.", username);
                        userUuid = username;
                    }
                }

                if (userUuid == null) {
                    log.warn("Could not determine user UUID for activity logging. Principal type: {}", principal.getClass().getName());
                    userUuid = authentication.getName();
                }

                String methodName = joinPoint.getSignature().getName();
                String className = joinPoint.getTarget().getClass().getSimpleName();
                String description = String.format("%s.%s", className, methodName);
                
                if (errorMessage != null) {
                    description += " - Error: " + errorMessage;
                }

                String defaultGroupId = "00000000-0000-0000-0000-000000000000";
                String defaultOrgId = "00000000-0000-0000-0000-000000000000";

                userActivityLogService.logActivity(
                    UUID.randomUUID().toString(),
                    userUuid,
                    defaultGroupId,
                    defaultOrgId,
                    action,
                    description,
                    request.getHeader("User-Agent"),
                    userUuid,
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
            Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
            String userUuid = null;
            String usernameForLog = "SYSTEM";

            if (authentication != null && authentication.isAuthenticated()) {
                 Object principal = authentication.getPrincipal();
                 usernameForLog = authentication.getName();

                if (principal instanceof CustomUserDetails) {
                    userUuid = ((CustomUserDetails) principal).getUser().getUuid();
                } else if (principal instanceof User) {
                    userUuid = ((User) principal).getUuid();
                } else if (principal instanceof String) {
                    User user = userRepository.findByUsername((String) principal).orElse(null);
                    if (user != null) {
                        userUuid = user.getUuid();
                    } else {
                         log.warn("User not found with username: {} during general activity logging.", principal);
                    }
                }
            }

            if (userUuid == null) {
                log.warn("User UUID not found for logging activity '{}'. Using username '{}' or system default.", activityType, usernameForLog);
            }

            String ipAddress = getClientIp();
            String userAgent = getClientUserAgent();
            
            String groupId = "00000000-0000-0000-0000-000000000000";
            String organizationId = "00000000-0000-0000-0000-000000000000";
            
            String effectiveUserIdForLog = (userUuid != null) ? userUuid : usernameForLog;

            userActivityLogService.logActivity(
                UUID.randomUUID().toString(),
                effectiveUserIdForLog,
                groupId,
                organizationId,
                activityType,
                description,
                userAgent,
                effectiveUserIdForLog,
                ipAddress
            );
        } catch (Exception e) {
            log.error("Failed to log activity: {}", e.getMessage());
        }
    }
} 