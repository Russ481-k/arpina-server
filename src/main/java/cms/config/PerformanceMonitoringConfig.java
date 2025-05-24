package cms.config;

import org.springframework.boot.actuate.health.Health;
import org.springframework.boot.actuate.health.HealthIndicator;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Profile;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.boot.actuate.endpoint.annotation.Endpoint;
import org.springframework.boot.actuate.endpoint.annotation.ReadOperation;
import org.springframework.stereotype.Component;

import javax.sql.DataSource;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;
import java.util.HashMap;
import java.util.Map;

@Configuration
public class PerformanceMonitoringConfig {

    /**
     * 동시성 제어 관련 메트릭 수집
     */
    @Bean
    public EnrollmentMetrics enrollmentMetrics() {
        return new EnrollmentMetrics();
    }

    /**
     * 데이터베이스 성능 모니터링
     */
    @Bean
    public HealthIndicator databaseHealthIndicator(DataSource dataSource) {
        return new DatabaseHealthIndicator(dataSource);
    }

    /**
     * 개발환경용 커스텀 메트릭 엔드포인트
     */
    @Bean
    @Profile("dev")
    public EnrollmentMetricsEndpoint enrollmentMetricsEndpoint(EnrollmentMetrics metrics) {
        return new EnrollmentMetricsEndpoint(metrics);
    }

    /**
     * 신청 관련 메트릭 수집 클래스
     */
    public static class EnrollmentMetrics {
        private final AtomicInteger totalEnrollmentAttempts = new AtomicInteger(0);
        private final AtomicInteger successfulEnrollments = new AtomicInteger(0);
        private final AtomicInteger failedEnrollments = new AtomicInteger(0);
        private final AtomicInteger deadlockRetries = new AtomicInteger(0);
        private final AtomicLong totalProcessingTime = new AtomicLong(0);
        private final AtomicInteger concurrentUsers = new AtomicInteger(0);

        public void recordEnrollmentAttempt() {
            totalEnrollmentAttempts.incrementAndGet();
        }

        public void recordSuccessfulEnrollment(long processingTimeMs) {
            successfulEnrollments.incrementAndGet();
            totalProcessingTime.addAndGet(processingTimeMs);
        }

        public void recordFailedEnrollment() {
            failedEnrollments.incrementAndGet();
        }

        public void recordDeadlockRetry() {
            deadlockRetries.incrementAndGet();
        }

        public void incrementConcurrentUsers() {
            concurrentUsers.incrementAndGet();
        }

        public void decrementConcurrentUsers() {
            concurrentUsers.decrementAndGet();
        }

        // Getters for metrics exposure
        public int getTotalEnrollmentAttempts() { return totalEnrollmentAttempts.get(); }
        public int getSuccessfulEnrollments() { return successfulEnrollments.get(); }
        public int getFailedEnrollments() { return failedEnrollments.get(); }
        public int getDeadlockRetries() { return deadlockRetries.get(); }
        public int getConcurrentUsers() { return concurrentUsers.get(); }
        public double getAverageProcessingTime() {
            int successful = successfulEnrollments.get();
            return successful > 0 ? (double) totalProcessingTime.get() / successful : 0.0;
        }
        public double getSuccessRate() {
            int total = totalEnrollmentAttempts.get();
            return total > 0 ? (double) successfulEnrollments.get() / total * 100 : 0.0;
        }
        
        // 개발환경용 메트릭 리셋 기능
        public void resetMetrics() {
            totalEnrollmentAttempts.set(0);
            successfulEnrollments.set(0);
            failedEnrollments.set(0);
            deadlockRetries.set(0);
            totalProcessingTime.set(0);
            concurrentUsers.set(0);
        }
    }

    /**
     * 데이터베이스 헬스 체크
     */
    public static class DatabaseHealthIndicator implements HealthIndicator {
        private final JdbcTemplate jdbcTemplate;

        public DatabaseHealthIndicator(DataSource dataSource) {
            this.jdbcTemplate = new JdbcTemplate(dataSource);
        }

        @Override
        public Health health() {
            try {
                // Connection Pool 상태 체크
                long startTime = System.currentTimeMillis();
                jdbcTemplate.queryForObject("SELECT 1", Integer.class);
                long responseTime = System.currentTimeMillis() - startTime;

                // 응답 시간에 따른 헬스 상태 결정 (개발환경은 기준 완화)
                if (responseTime < 200) {
                    return Health.up()
                            .withDetail("database", "responsive")
                            .withDetail("responseTime", responseTime + "ms")
                            .withDetail("environment", "development")
                            .build();
                } else if (responseTime < 2000) {
                    return Health.up()
                            .withDetail("database", "slow")
                            .withDetail("responseTime", responseTime + "ms")
                            .withDetail("environment", "development")
                            .build();
                } else {
                    return Health.down()
                            .withDetail("database", "very_slow")
                            .withDetail("responseTime", responseTime + "ms")
                            .withDetail("environment", "development")
                            .build();
                }
            } catch (Exception e) {
                return Health.down()
                        .withDetail("database", "unavailable")
                        .withDetail("error", e.getMessage())
                        .withDetail("environment", "development")
                        .build();
            }
        }
    }

    /**
     * 개발환경용 커스텀 메트릭 엔드포인트
     */
    @Endpoint(id = "enrollment-metrics")
    @Profile("dev")
    public static class EnrollmentMetricsEndpoint {
        
        private final EnrollmentMetrics metrics;
        
        public EnrollmentMetricsEndpoint(EnrollmentMetrics metrics) {
            this.metrics = metrics;
        }
        
        @ReadOperation
        public Map<String, Object> enrollmentMetrics() {
            Map<String, Object> result = new HashMap<>();
            result.put("totalAttempts", metrics.getTotalEnrollmentAttempts());
            result.put("successfulEnrollments", metrics.getSuccessfulEnrollments());
            result.put("failedEnrollments", metrics.getFailedEnrollments());
            result.put("deadlockRetries", metrics.getDeadlockRetries());
            result.put("concurrentUsers", metrics.getConcurrentUsers());
            result.put("averageProcessingTime", metrics.getAverageProcessingTime());
            result.put("successRate", metrics.getSuccessRate());
            result.put("environment", "development");
            return result;
        }
    }
} 