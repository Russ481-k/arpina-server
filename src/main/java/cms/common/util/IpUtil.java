package cms.common.util;

import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

import javax.servlet.http.HttpServletRequest;

public class IpUtil {

    /**
     * 현재 요청의 클라이언트 IP 주소를 반환합니다.
     * 리버스 프록시/로드 밸런서 환경을 고려하여 X-Forwarded-For 헤더를 우선적으로 확인합니다.
     * 
     * @return 클라이언트 IP 주소 문자열
     */
    public static String getClientIp() {
        // RequestContextHolder를 통해 현재 요청의 HttpServletRequest 객체를 가져옵니다.
        // 이 방법은 컨트롤러가 아닌 서비스 계층 등에서 요청 정보에 접근할 때 유용합니다.
        HttpServletRequest request = ((ServletRequestAttributes) RequestContextHolder.getRequestAttributes())
                .getRequest();

        String ip = request.getHeader("X-Forwarded-For");

        if (ip != null && !ip.isEmpty() && !"unknown".equalsIgnoreCase(ip)) {
            // X-Forwarded-For 헤더가 여러 IP를 포함하는 경우(e.g., "client, proxy1, proxy2"),
            // 목록의 첫 번째 IP가 실제 클라이언트의 IP입니다.
            return ip.split(",")[0].trim();
        }

        // X-Forwarded-For 헤더가 없는 경우, getRemoteAddr()를 통해 직접 연결된 클라이언트의 IP를 가져옵니다.
        return request.getRemoteAddr();
    }
}