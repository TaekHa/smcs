package com.smcs.security;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.smcs.user.User;
import java.lang.reflect.Field;
import java.time.Instant;
import java.util.Date;
import javax.crypto.SecretKey;
import org.junit.jupiter.api.Test;

import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;

class JwtServiceTest {

	private static final String SECRET = "test-secret-must-be-at-least-32-bytes-long-padding";

	@Test
	void generateThenParseRoundTrip() {
		JwtService jwt = newJwtService(SECRET, 60);

		JwtService.TokenIssued issued = jwt.generate(42L, User.Role.ADMIN);
		JwtService.ParsedJwt parsed = jwt.parse(issued.token());

		assertThat(issued.expiresInSeconds()).isEqualTo(60);
		assertThat(parsed.userId()).isEqualTo(42L);
		assertThat(parsed.role()).isEqualTo(User.Role.ADMIN);
		assertThat(parsed.expiresAt()).isAfter(Instant.now());
	}

	@Test
	void secretShorterThan32BytesFailsInit() {
		JwtService jwt = new JwtService("too-short", 60);
		assertThatThrownBy(jwt::init)
				.isInstanceOf(IllegalStateException.class)
				.hasMessageContaining("32");
	}

	@Test
	void expiredTokenThrowsInvalidJwt() throws Exception {
		JwtService jwt = newJwtService(SECRET, 60);
		SecretKey key = getKey(jwt);
		String expired = Jwts.builder()
				.subject("1")
				.claim("role", "AGENT")
				.issuedAt(Date.from(Instant.now().minusSeconds(120)))
				.expiration(Date.from(Instant.now().minusSeconds(60)))
				.signWith(key)
				.compact();

		assertThatThrownBy(() -> jwt.parse(expired))
				.isInstanceOf(InvalidJwtException.class)
				.hasMessageContaining("expired");
	}

	@Test
	void invalidSignatureThrowsInvalidJwt() {
		JwtService jwt = newJwtService(SECRET, 60);
		SecretKey otherKey = Keys.hmacShaKeyFor("totally-different-secret-also-32-bytes-padding".getBytes());
		String forged = Jwts.builder()
				.subject("1")
				.claim("role", "AGENT")
				.issuedAt(Date.from(Instant.now()))
				.expiration(Date.from(Instant.now().plusSeconds(60)))
				.signWith(otherKey)
				.compact();

		assertThatThrownBy(() -> jwt.parse(forged))
				.isInstanceOf(InvalidJwtException.class);
	}

	private JwtService newJwtService(String secret, long expirationSeconds) {
		JwtService jwt = new JwtService(secret, expirationSeconds);
		jwt.init();
		return jwt;
	}

	private SecretKey getKey(JwtService jwt) throws Exception {
		Field f = JwtService.class.getDeclaredField("key");
		f.setAccessible(true);
		return (SecretKey) f.get(jwt);
	}
}
