package com.smcs.security;

import com.smcs.user.User;
import io.jsonwebtoken.Claims;
import io.jsonwebtoken.ExpiredJwtException;
import io.jsonwebtoken.JwtException;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import jakarta.annotation.PostConstruct;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.Instant;
import java.util.Date;
import javax.crypto.SecretKey;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

@Service
public class JwtService {

	private static final int MIN_SECRET_BYTES = 32;

	private final String secret;
	private final long expirationSeconds;
	private SecretKey key;

	public JwtService(
			@Value("${smcs.jwt.secret}") String secret,
			@Value("${smcs.jwt.expiration-seconds:28800}") long expirationSeconds) {
		this.secret = secret;
		this.expirationSeconds = expirationSeconds;
	}

	@PostConstruct
	void init() {
		if (secret == null || secret.getBytes(StandardCharsets.UTF_8).length < MIN_SECRET_BYTES) {
			throw new IllegalStateException(
					"smcs.jwt.secret must be >= " + MIN_SECRET_BYTES + " bytes for HMAC-SHA256");
		}
		this.key = Keys.hmacShaKeyFor(secret.getBytes(StandardCharsets.UTF_8));
	}

	public TokenIssued generate(Long userId, User.Role role) {
		Instant now = Instant.now();
		Instant exp = now.plus(Duration.ofSeconds(expirationSeconds));
		String token = Jwts.builder()
				.subject(userId.toString())
				.claim("role", role.name())
				.issuedAt(Date.from(now))
				.expiration(Date.from(exp))
				.signWith(key)
				.compact();
		return new TokenIssued(token, expirationSeconds);
	}

	public ParsedJwt parse(String token) {
		try {
			Claims claims = Jwts.parser()
					.verifyWith(key)
					.build()
					.parseSignedClaims(token)
					.getPayload();
			Long userId = Long.parseLong(claims.getSubject());
			User.Role role = User.Role.valueOf(claims.get("role", String.class));
			return new ParsedJwt(userId, role, claims.getExpiration().toInstant());
		} catch (ExpiredJwtException e) {
			throw new InvalidJwtException("token expired", e);
		} catch (JwtException | IllegalArgumentException e) {
			throw new InvalidJwtException("invalid token", e);
		}
	}

	public long getExpirationSeconds() {
		return expirationSeconds;
	}

	public record TokenIssued(String token, long expiresInSeconds) {
	}

	public record ParsedJwt(Long userId, User.Role role, Instant expiresAt) {
	}
}
