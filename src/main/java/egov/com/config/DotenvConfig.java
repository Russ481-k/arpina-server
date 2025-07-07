package egov.com.config;

import org.springframework.stereotype.Component;

import javax.annotation.PostConstruct;

@Component
public class DotenvConfig {

    @PostConstruct
    public void loadDotenv() {
        // EgovConfigAppDataSource에서 이미 .env 로드를 처리하므로
        // 여기서는 JWT_SECRET이 제대로 설정되었는지만 확인
        try {
            String jwtSecret = System.getProperty("JWT_SECRET");
            System.out.println("=== DotenvConfig: JWT_SECRET 확인 ===");
            System.out.println("JWT_SECRET 상태: " + (jwtSecret != null ? "[EXISTS]" : "[NOT_FOUND]"));

            if (jwtSecret == null) {
                System.err.println("❌ JWT_SECRET이 설정되지 않았습니다!");
            }
        } catch (Exception e) {
            System.err.println("❌ JWT_SECRET 확인 실패: " + e.getMessage());
            e.printStackTrace();
        }
    }
}