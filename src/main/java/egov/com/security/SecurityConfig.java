package egov.com.security;

import egov.com.jwt.JwtAuthenticationEntryPoint;
import egov.com.jwt.JwtRequestFilter;
import lombok.RequiredArgsConstructor;

import org.springframework.beans.factory.annotation.Value;
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
import org.springframework.security.web.util.matcher.AntPathRequestMatcher;
import org.springframework.security.web.util.matcher.OrRequestMatcher;
import org.springframework.security.web.util.matcher.RequestMatcher;

import cms.auth.service.CustomUserDetailsService;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import javax.servlet.MultipartConfigElement;

/**
 * fileName : SecurityConfig
 * author : crlee
 * date : 2023/06/10
 * description :
 * ===========================================================
 * DATE AUTHOR NOTE
 * -----------------------------------------------------------
 * 2023/06/10 crlee 최초 생성
 */
@Configuration
@EnableWebSecurity
@EnableMethodSecurity
@RequiredArgsConstructor
public class SecurityConfig {

	private final CustomUserDetailsService userDetailsService;
	private final JwtRequestFilter jwtRequestFilter;
	private final JwtAuthenticationEntryPoint jwtAuthenticationEntryPoint;
	private final RequestMatcher permitAllRequestMatcher;

	@Value("${cors.allowed-origins}")
	private String corsAllowedOrigins;

	@Value("${app.environment.cors-enabled:true}")
	private boolean corsEnabled;

	@Value("${app.environment.name:local}")
	private String environmentName;

	@Bean
	public static RequestMatcher permitAllRequestMatcherBean() {
		List<RequestMatcher> matchers = new ArrayList<>();

		matchers.add(new AntPathRequestMatcher("/**", HttpMethod.OPTIONS.toString()));

		List<String> permitAllAntPatterns = Arrays.asList(
				"/login/**",
				"/swagger-ui/**",
				"/v3/api-docs/**",
				"/swagger-resources/**",
				"/webjars/**",
				"/nice/checkplus/**",
				"/api/v1/v3/api-docs/**",
				"/api/v1/auth/**",
				"/api/v1/cms/menu/public",
				"/api/v1/cms/menu/public/**/page-details",
				"/api/v1/cms/template/public",
				"/api/v1/cms/template",
				"/api/v1/cms/bbs/master",
				"/api/v1/cms/schedule/public**",
				"/api/v1/cms/file/public/**",
				"/api/v1/cms/popups/active",
				"/api/v1/swimming/lessons/**",
				"/api/v1/nice/checkplus/**",
				"/api/v1/group-reservations",
				"/api/v1/external/**");
		for (String pattern : permitAllAntPatterns) {
			matchers.add(new AntPathRequestMatcher(pattern));
		}

		// GET 요청에 대해서만 허용할 경로 목록
		List<String> getOnlyPatterns = Arrays.asList(
				"/api/v1/cms/contents/main",
				"/api/v1/cms/bbs/article",
				"/api/v1/cms/bbs/article/**",
				"/api/v1/cms/bbs/article/board/**",
				"/api/v1/cms/bbs",
				"/api/v1/cms/bbs/**",
				"/api/v1/cms/bbs/voice/read/**/comments", // 댓글 조회 허용
				"/api/v1/cms/schedule/**",
				"/api/v1/cms/enterprises",
				"/api/v1/cms/enterprises/{id}");

		for (String pattern : getOnlyPatterns) {
			matchers.add(new AntPathRequestMatcher(pattern, HttpMethod.GET.toString()));
		}

		return new OrRequestMatcher(matchers);
	}

	@Bean
	public SecurityFilterChain filterChain(HttpSecurity http) throws Exception {
		// CORS 설정을 환경변수에 따라 조건부로 적용
		if (corsEnabled) {
			http.cors().configurationSource(corsConfigurationSource());
		} else {
			http.cors().disable();
		}

		http
				.csrf().disable()
				.sessionManagement().sessionCreationPolicy(SessionCreationPolicy.STATELESS)
				.and()
				.authorizeHttpRequests(authz -> authz
						.requestMatchers(this.permitAllRequestMatcher).permitAll()
						.antMatchers(HttpMethod.GET, "/api/v1/cms/bbs/**").permitAll()
						.antMatchers(HttpMethod.POST, "/cms/bbs/voice/read/**/comments").authenticated()
						.antMatchers(HttpMethod.PUT, "/cms/bbs/voice/read/**/comments/**").authenticated()
						.antMatchers(HttpMethod.DELETE, "/cms/bbs/voice/read/**/comments/**").authenticated()
						.antMatchers(HttpMethod.POST, "/api/v1/cms/bbs/article").authenticated()
						.antMatchers(HttpMethod.PUT, "/api/v1/cms/bbs/article/**").authenticated()
						.antMatchers(HttpMethod.DELETE, "/api/v1/cms/bbs/article/**").authenticated()
						.antMatchers(
								HttpMethod.POST, "/api/v1/cms/enterprises")
						.hasRole("ADMIN")
						.antMatchers(
								HttpMethod.PUT, "/api/v1/cms/enterprises/{id}")
						.hasRole("ADMIN")
						.antMatchers(
								HttpMethod.DELETE, "/api/v1/cms/enterprises/{id}")
						.hasRole("ADMIN")
						.antMatchers(
								HttpMethod.POST, "/api/v1/cms/payments/**")
						.hasAnyRole("ADMIN", "SYSTEM_ADMIN")
						.antMatchers(
								"/api/v1/cms/menu",
								"/api/v1/cms/menu/type/**",
								"/api/v1/cms/bbs/master/**",
								"/api/v1/cms/content",
								"/api/v1/cms/user",
								"/api/v1/cms/file/private/**",
								"/api/v1/cms/popups**")
						.authenticated()
						.antMatchers("/api/v1/mypage/**").hasRole("USER")
						.anyRequest().authenticated())
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

		// 환경별 Origin 설정
		String[] origins = corsAllowedOrigins.split(",");
		configuration.setAllowedOriginPatterns(Arrays.asList(origins));
		configuration.setAllowedMethods(Arrays.asList("HEAD", "POST", "GET", "DELETE", "PUT", "PATCH", "OPTIONS"));
		configuration.setAllowedHeaders(Arrays.asList(
				"Authorization",
				"Cache-Control",
				"Content-Type",
				"Origin",
				"Accept",
				"X-Requested-With",
				"Access-Control-Request-Method",
				"Access-Control-Request-Headers"));
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