package cms.config;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.ContentNegotiationConfigurer;
import org.springframework.web.servlet.config.annotation.CorsRegistry;
import org.springframework.web.servlet.config.annotation.PathMatchConfigurer;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;
import org.springframework.lang.NonNull;

@Configuration
public class WebConfig implements WebMvcConfigurer {

    @Value("${cors.allowed-origins}")
    private String allowedOrigins;
    @Override
    public void configurePathMatch(@NonNull PathMatchConfigurer configurer) {
        configurer
            .addPathPrefix("/api/v1", c -> c.isAnnotationPresent(org.springframework.web.bind.annotation.RestController.class));
    }

    @Override
    public void configureContentNegotiation(@NonNull ContentNegotiationConfigurer configurer) {
        configurer
            .favorParameter(false)
            .ignoreAcceptHeader(false)
            .useRegisteredExtensionsOnly(true);
    }

    @Override
    public void addCorsMappings(CorsRegistry registry) {
        // 환경변수를 통한 CORS 설정
        // registry.addMapping("/**")
        //     .allowedOriginPatterns(allowedOrigins)
        //     .allowedMethods("GET", "POST", "PUT", "DELETE", "OPTIONS", "PATCH")
        //     .allowedHeaders("*")
        //     .allowCredentials(true)
        //     .exposedHeaders("Authorization")
        //     .maxAge(3600);
    }
} 
 
 
 