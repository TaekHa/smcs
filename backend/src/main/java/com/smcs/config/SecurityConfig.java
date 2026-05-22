package com.smcs.config;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.smcs.common.ErrorResponse;
import com.smcs.security.JwtAuthenticationFilter;
import com.smcs.security.RateLimitFilter;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.web.servlet.FilterRegistrationBean;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.config.annotation.authentication.configuration.AuthenticationConfiguration;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;

@Configuration
@EnableWebSecurity
@EnableMethodSecurity(prePostEnabled = true)
public class SecurityConfig {

	private final JwtAuthenticationFilter jwtAuthenticationFilter;
	private final ObjectMapper objectMapper;
	private final int rateLimitPerUserPerMinute;

	public SecurityConfig(JwtAuthenticationFilter jwtAuthenticationFilter,
			ObjectMapper objectMapper,
			@Value("${smcs.rate-limit.per-user-per-minute:300}") int rateLimitPerUserPerMinute) {
		this.jwtAuthenticationFilter = jwtAuthenticationFilter;
		this.objectMapper = objectMapper;
		this.rateLimitPerUserPerMinute = rateLimitPerUserPerMinute;
	}

	@Bean
	public RateLimitFilter rateLimitFilter() {
		return new RateLimitFilter(rateLimitPerUserPerMinute, objectMapper);
	}

	@Bean
	public FilterRegistrationBean<RateLimitFilter> disableRateLimitAutoRegistration(RateLimitFilter filter) {
		FilterRegistrationBean<RateLimitFilter> reg = new FilterRegistrationBean<>(filter);
		reg.setEnabled(false);
		return reg;
	}

	@Bean
	public FilterRegistrationBean<JwtAuthenticationFilter> disableJwtFilterAutoRegistration(JwtAuthenticationFilter filter) {
		FilterRegistrationBean<JwtAuthenticationFilter> reg = new FilterRegistrationBean<>(filter);
		reg.setEnabled(false);
		return reg;
	}

	@Bean
	public PasswordEncoder passwordEncoder() {
		return new BCryptPasswordEncoder();
	}

	@Bean
	public AuthenticationManager authenticationManager(AuthenticationConfiguration config) throws Exception {
		return config.getAuthenticationManager();
	}

	@Bean
	public SecurityFilterChain securityFilterChain(HttpSecurity http) throws Exception {
		http
				.csrf(csrf -> csrf.disable())
				.sessionManagement(sm -> sm.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
				.authorizeHttpRequests(authz -> authz
						.requestMatchers("/api/auth/login", "/api/health").permitAll()
						.requestMatchers("/api/**").authenticated()
						.requestMatchers("/files/**").authenticated()
						.anyRequest().permitAll())
				.exceptionHandling(ex -> ex
						.authenticationEntryPoint((req, resp, e) -> writeError(resp,
								HttpStatus.UNAUTHORIZED, "UNAUTHORIZED",
								"Authentication required to access this resource."))
						.accessDeniedHandler((req, resp, e) -> writeError(resp,
								HttpStatus.FORBIDDEN, "FORBIDDEN",
								"You do not have permission to access this resource.")))
				.addFilterBefore(jwtAuthenticationFilter, UsernamePasswordAuthenticationFilter.class)
				.addFilterAfter(rateLimitFilter(), JwtAuthenticationFilter.class);
		return http.build();
	}

	private void writeError(HttpServletResponse response, HttpStatus status, String code, String message)
			throws java.io.IOException {
		response.setStatus(status.value());
		response.setContentType(MediaType.APPLICATION_JSON_VALUE);
		objectMapper.writeValue(response.getOutputStream(), ErrorResponse.of(code, message));
	}
}
