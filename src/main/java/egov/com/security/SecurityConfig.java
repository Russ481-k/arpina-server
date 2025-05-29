package egov.com.security;

import egov.com.jwt.JwtAuthenticationEntryPoint;
import egov.com.jwt.JwtRequestFilter;
import lombok.RequiredArgsConstructor;
import org.springframework.boot.web.servlet.MultipartConfigFactory;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.dao.DaoAuthenticationProvider;
import org.springframework.security.config.annotation.authentication.configuration.AuthenticationConfiguration;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;
import org.springframework.util.unit.DataSize;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.CorsConfigurationSource;
import org.springframework.web.cors.UrlBasedCorsConfigurationSource;
import org.springframework.http.HttpMethod;

import cms.auth.service.CustomUserDetailsService;

import java.util.Arrays;

import javax.servlet.MultipartConfigElement;

/**
 * fileName       : SecurityConfig
 * author         : crlee
 * date           : 2023/06/10
 * description    :
 * ===========================================================
 * DATE              AUTHOR             NOTE
 * -----------------------------------------------------------
 * 2023/06/10        crlee       최초 생성
 */
@Configuration
@EnableWebSecurity
@EnableMethodSecurity
@RequiredArgsConstructor
public class SecurityConfig {
	
	private final CustomUserDetailsService userDetailsService;
	private final JwtRequestFilter jwtRequestFilter;
	private final JwtAuthenticationEntryPoint jwtAuthenticationEntryPoint;

	@Bean
	public SecurityFilterChain filterChain(HttpSecurity http) throws Exception {
		http
			.cors().configurationSource(corsConfigurationSource()) // CORS 활성화
			.and()
			.csrf().disable()
			.sessionManagement().sessionCreationPolicy(SessionCreationPolicy.STATELESS)
			.and()
			.authorizeHttpRequests()
				// 우선 순위가 높은 수영 API 규칙 (모든 HTTP 메서드 허용)
				.antMatchers("/api/v1/swimming/**").permitAll()
				.antMatchers(HttpMethod.OPTIONS, "/**").permitAll()
				.antMatchers(HttpMethod.GET, "/api/v1/cms/enterprises", "/api/v1/cms/enterprises/{id}").permitAll()
				.antMatchers(
					"/login/**",
					"/swagger-ui/**",
					"/v3/api-docs/**",
					"/swagger-resources/**",
					"/webjars/**",
					"/api/v1/v3/api-docs/**",
					"/api/v1/auth/login",
					"/api/v1/auth/register",
					"/api/v1/auth/logout",
					"/api/v1/auth/verify",
					"/api/v1/auth/signup",
					"/api/v1/auth/check-username/**",
					"/api/v1/cms/menu/public",
					"/api/v1/cms/menu/public/**/page-details",
					"/api/v1/cms/template/public",
					"/api/v1/cms/template",
					"/api/v1/cms/bbs/article",
					"/api/v1/cms/bbs/article/**",
					"/api/v1/cms/bbs/article/board/**",
					"/api/v1/cms/bbs",
					"/api/v1/cms/bbs/**",
					"/api/v1/cms/bbs/master",
					"/api/v1/cms/schedule/public**",
					"/api/v1/cms/schedule/**",
					"/api/v1/cms/file/public/**",
					"/api/v1/swimming/**",
					"/api/v1/nice/checkplus/**"
				).permitAll()
				.antMatchers(
					HttpMethod.POST, "/api/v1/cms/enterprises"
				).hasRole("ADMIN")
				.antMatchers(
					HttpMethod.PUT, "/api/v1/cms/enterprises/{id}"
				).hasRole("ADMIN")
				.antMatchers(
					HttpMethod.DELETE, "/api/v1/cms/enterprises/{id}"
				).hasRole("ADMIN")
				.antMatchers(
					"/api/v1/cms/menu",
					"/api/v1/cms/menu/type/**",
					"/api/v1/cms/bbs/master/**",
					"/api/v1/cms/content",
					"/api/v1/cms/user",
					"/api/v1/cms/file/private/**"
				).authenticated()
				.antMatchers("/api/v1/mypage/**").hasRole("USER")
				.anyRequest().authenticated()
			.and()
			.addFilterBefore(jwtRequestFilter, UsernamePasswordAuthenticationFilter.class)
			.exceptionHandling()
				.authenticationEntryPoint(jwtAuthenticationEntryPoint);
		return http.build();
	}

	@Bean
	public AuthenticationManager authenticationManager(AuthenticationConfiguration authConfig) throws Exception {
		return authConfig.getAuthenticationManager();
	}

	@Bean
	public PasswordEncoder passwordEncoder() {
		return new BCryptPasswordEncoder();
	}

	@Bean
	public DaoAuthenticationProvider authenticationProvider() {
		DaoAuthenticationProvider authProvider = new DaoAuthenticationProvider();
		authProvider.setUserDetailsService(userDetailsService);
		authProvider.setPasswordEncoder(passwordEncoder());
		return authProvider;
	}

	@Bean
	protected CorsConfigurationSource corsConfigurationSource() {
		CorsConfiguration configuration = new CorsConfiguration();
		
		// 개발환경용 Origin 설정
		configuration.setAllowedOriginPatterns(Arrays.asList("http://localhost:3000", "http://127.0.0.1:3000", "http://localhost:*"));
		configuration.setAllowedMethods(Arrays.asList("HEAD", "POST", "GET", "DELETE", "PUT", "PATCH", "OPTIONS"));
		configuration.setAllowedHeaders(Arrays.asList(
			"Authorization", 
			"Cache-Control", 
			"Content-Type",
			"Origin",
			"Accept",
			"X-Requested-With",
			"Access-Control-Request-Method",
			"Access-Control-Request-Headers"
		));
		configuration.setAllowCredentials(true);
		configuration.setExposedHeaders(Arrays.asList("Authorization"));
		configuration.setMaxAge(3600L);
		
		UrlBasedCorsConfigurationSource source = new UrlBasedCorsConfigurationSource();
		source.registerCorsConfiguration("/**", configuration); // CORS 설정 활성화
		return source;
	}

	@Bean
	public MultipartConfigElement multipartConfigElement() {
		MultipartConfigFactory factory = new MultipartConfigFactory();
		factory.setMaxFileSize(DataSize.ofMegabytes(10));
		factory.setMaxRequestSize(DataSize.ofMegabytes(10));
		return factory.createMultipartConfig();
	}
}