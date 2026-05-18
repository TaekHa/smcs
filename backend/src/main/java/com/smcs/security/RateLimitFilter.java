package com.smcs.security;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.smcs.common.ErrorResponse;
import io.github.bucket4j.Bandwidth;
import io.github.bucket4j.Bucket;
import io.github.bucket4j.ConsumptionProbe;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.time.Duration;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.filter.OncePerRequestFilter;

public class RateLimitFilter extends OncePerRequestFilter {

	private final int perUserPerMinute;
	private final ObjectMapper objectMapper;
	private final Map<Long, Bucket> buckets = new ConcurrentHashMap<>();

	public RateLimitFilter(
			@Value("${smcs.rate-limit.per-user-per-minute:300}") int perUserPerMinute,
			ObjectMapper objectMapper) {
		this.perUserPerMinute = perUserPerMinute;
		this.objectMapper = objectMapper;
	}

	@Override
	protected boolean shouldNotFilter(HttpServletRequest request) {
		String path = request.getRequestURI();
		if (!path.startsWith("/api/")) {
			return true;
		}
		return path.equals("/api/auth/login") || path.equals("/api/health");
	}

	@Override
	protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain chain)
			throws ServletException, IOException {
		Long userId = currentUserId();
		if (userId == null) {
			chain.doFilter(request, response);
			return;
		}
		Bucket bucket = buckets.computeIfAbsent(userId, id -> Bucket.builder()
				.addLimit(Bandwidth.builder()
						.capacity(perUserPerMinute)
						.refillIntervally(perUserPerMinute, Duration.ofMinutes(1))
						.build())
				.build());
		ConsumptionProbe probe = bucket.tryConsumeAndReturnRemaining(1);
		if (probe.isConsumed()) {
			chain.doFilter(request, response);
			return;
		}
		long retryAfterSeconds = Math.max(1, probe.getNanosToWaitForRefill() / 1_000_000_000L);
		response.setStatus(HttpStatus.TOO_MANY_REQUESTS.value());
		response.setHeader("Retry-After", String.valueOf(retryAfterSeconds));
		response.setContentType(MediaType.APPLICATION_JSON_VALUE);
		objectMapper.writeValue(response.getOutputStream(),
				ErrorResponse.of("RATE_LIMIT_EXCEEDED", "Too many requests. Try again in "
						+ retryAfterSeconds + " seconds."));
	}

	private Long currentUserId() {
		Authentication auth = SecurityContextHolder.getContext().getAuthentication();
		if (auth == null || !auth.isAuthenticated()) {
			return null;
		}
		Object principal = auth.getPrincipal();
		if (principal instanceof Long id) {
			return id;
		}
		return null;
	}
}
