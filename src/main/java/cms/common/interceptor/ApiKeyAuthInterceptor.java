package cms.common.interceptor;

import cms.config.ExternalApiProperties;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.servlet.HandlerInterceptor;

@Component
@RequiredArgsConstructor
public class ApiKeyAuthInterceptor implements HandlerInterceptor {

    private static final Logger logger = LoggerFactory.getLogger(ApiKeyAuthInterceptor.class);
    private final ExternalApiProperties apiProperties;
    private static final String API_KEY_HEADER = "X-API-KEY";

    @Override
    public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler)
            throws Exception {
        String clientIp = getClientIp(request);

        logger.info("External API Access attempt - Client IP: [{}], Whitelisted IPs: {}", clientIp,
                apiProperties.getWhitelistIps());

        if (!isIpWhitelisted(clientIp)) {
            logger.warn("IP Address [{}] is not in the whitelist. Access denied.", clientIp);
            response.sendError(HttpServletResponse.SC_FORBIDDEN, "Forbidden: IP not allowed");
            return false;
        }

        String apiKey = request.getHeader(API_KEY_HEADER);
        if (!isValidApiKey(apiKey)) {
            logger.warn("Invalid API Key received. Access denied for IP [{}].", clientIp);
            response.sendError(HttpServletResponse.SC_UNAUTHORIZED, "Unauthorized: Invalid API Key");
            return false;
        }

        logger.info("External API Access GRANTED for IP [{}]", clientIp);
        return true;
    }

    private boolean isIpWhitelisted(String ip) {
        return apiProperties.getWhitelistIps().contains(ip);
    }

    private boolean isValidApiKey(String apiKey) {
        return apiProperties.getApiKey().equals(apiKey);
    }

    private String getClientIp(HttpServletRequest request) {
        String ip = request.getHeader("X-Forwarded-For");
        if (ip == null || ip.isEmpty() || "unknown".equalsIgnoreCase(ip)) {
            ip = request.getHeader("Proxy-Client-IP");
        }
        if (ip == null || ip.isEmpty() || "unknown".equalsIgnoreCase(ip)) {
            ip = request.getHeader("WL-Proxy-Client-IP");
        }
        if (ip == null || ip.isEmpty() || "unknown".equalsIgnoreCase(ip)) {
            ip = request.getHeader("HTTP_CLIENT_IP");
        }
        if (ip == null || ip.isEmpty() || "unknown".equalsIgnoreCase(ip)) {
            ip = request.getHeader("HTTP_X_FORWARDED_FOR");
        }
        if (ip == null || ip.isEmpty() || "unknown".equalsIgnoreCase(ip)) {
            ip = request.getRemoteAddr();
        }
        return ip;
    }
}