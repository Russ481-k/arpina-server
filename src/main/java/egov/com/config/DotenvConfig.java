package egov.com.config;

import io.github.cdimascio.dotenv.Dotenv;
import org.springframework.boot.context.event.ApplicationEnvironmentPreparedEvent;
import org.springframework.context.ApplicationListener;
import org.springframework.core.env.ConfigurableEnvironment;
import org.springframework.core.env.MapPropertySource;
import org.springframework.core.env.MutablePropertySources;
import org.springframework.stereotype.Component;

import java.util.HashMap;
import java.util.Map;

@Component
public class DotenvConfig implements ApplicationListener<ApplicationEnvironmentPreparedEvent> {

    @Override
    public void onApplicationEvent(ApplicationEnvironmentPreparedEvent event) {
        try {
            System.out.println("=== DotenvConfig: .env 파일 로드 시작 ===");

            Dotenv dotenv = Dotenv.configure()
                    .directory(".")
                    .ignoreIfMissing()
                    .ignoreIfMalformed()
                    .load();

            Map<String, Object> envMap = new HashMap<>();
            dotenv.entries().forEach(entry -> {
                envMap.put(entry.getKey(), entry.getValue());
                System.out.println("환경변수 로드: " + entry.getKey() + "=" +
                        (entry.getKey().contains("SECRET") || entry.getKey().contains("PASSWORD") ? "[MASKED]"
                                : entry.getValue()));
            });

            if (!envMap.isEmpty()) {
                ConfigurableEnvironment environment = event.getEnvironment();
                MutablePropertySources propertySources = environment.getPropertySources();
                propertySources.addFirst(new MapPropertySource("dotenvProperties", envMap));

                System.out.println("✅ .env 파일 로드 완료: " + envMap.size() + "개 환경변수");

                // JWT_SECRET 확인
                String jwtSecret = environment.getProperty("JWT_SECRET");
                System.out.println("JWT_SECRET 확인: " + (jwtSecret != null ? "[EXISTS]" : "[NOT_FOUND]"));
            } else {
                System.out.println("⚠️ .env 파일이 비어있거나 찾을 수 없습니다.");
            }

        } catch (Exception e) {
            System.err.println("❌ .env 파일 로드 실패: " + e.getMessage());
            e.printStackTrace();
        }
    }
}