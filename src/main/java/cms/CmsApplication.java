package cms;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.context.annotation.ComponentScan;
import org.springframework.scheduling.annotation.EnableScheduling;
import org.springframework.retry.annotation.EnableRetry;

@SpringBootApplication
@ComponentScan(basePackages = {
        "egov",
        "cms",
        "feature"
})
@EnableScheduling // 배치 작업을 위한 스케줄링 활성화
@EnableRetry // 재시도 기능 활성화
public class CmsApplication {
    public static void main(String[] args) {
        SpringApplication.run(CmsApplication.class, args);
    }
}