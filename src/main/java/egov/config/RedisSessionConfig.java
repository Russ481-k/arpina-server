package egov.config;

import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Profile;
import org.springframework.session.data.redis.config.annotation.web.http.EnableRedisHttpSession;

@Configuration
@Profile("!dev") // Active when the 'dev' profile is NOT active
@EnableRedisHttpSession
public class RedisSessionConfig {
    // You can add further Redis-specific session configurations here if needed
    // e.g., configure lettuce/jedis connection factory properties specifically for sessions,
    // session timeout, etc., though Spring Boot's auto-configuration with application.yml
    // properties usually handles most common cases.
} 