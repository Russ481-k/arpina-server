package cms.common.interceptor;

import cms.config.ExternalApiProperties;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.servlet.HandlerInterceptor;

import cms.common.util.IpUtil;

@Component
@RequiredArgsConstructor
public class ApiKeyAuthInterceptor implements HandlerInterceptor {

    private static final Logger logger = LoggerFactory.getLogger(ApiKeyAuthInterceptor.class);
    private final ExternalApiProperties apiProperties;

    @Override
    public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler)
            throws Exception {
        String apiKey = request.getHeader("X-API-KEY");
        String clientIp = IpUtil.getClientIp();

        logger.debug("Request URI: {}, API Key: {}, Client IP: {}", request.getRequestURI(), apiKey, clientIp);

        if (apiKey == null || !apiKey.equals(apiProperties.getApiKey())) {
            logger.warn("Invalid API Key received. Access denied for IP [{}].", clientIp);
            response.sendError(HttpServletResponse.SC_UNAUTHORIZED, "Unauthorized: Invalid API Key");
            return false;
        }

        if (!isIpWhitelisted(clientIp)) {
            logger.warn("IP Address [{}] is not in the whitelist. Access denied.", clientIp);
            response.sendError(HttpServletResponse.SC_FORBIDDEN, "Forbidden: IP not allowed");
            return false;
        }

        logger.info("External API Access GRANTED for IP [{}]", clientIp);
        return true;
    }

    private boolean isIpWhitelisted(String ip) {
        return apiProperties.getWhitelistIps().contains(ip);
    }
}