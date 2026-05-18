package com.smcs.security;

import java.sql.Timestamp;
import java.time.Duration;
import java.time.Instant;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class LoginAttemptService {

	static final int MAX_FAILURES = 5;
	static final Duration WINDOW = Duration.ofMinutes(10);

	private final JdbcTemplate jdbc;

	public LoginAttemptService(JdbcTemplate jdbc) {
		this.jdbc = jdbc;
	}

	@Transactional(readOnly = true)
	public boolean isLocked(String username, String ipAddress) {
		Timestamp windowStart = Timestamp.from(Instant.now().minus(WINDOW));
		Integer count = jdbc.queryForObject(
				"SELECT COUNT(*) FROM login_attempt "
						+ "WHERE attempted_at >= ? AND success = FALSE "
						+ "AND (username = ? OR ip_address = ?)",
				Integer.class,
				windowStart, username, ipAddress);
		return count != null && count >= MAX_FAILURES;
	}

	@Transactional
	public void recordFailure(String username, String ipAddress) {
		jdbc.update(
				"INSERT INTO login_attempt (username, ip_address, success) VALUES (?, ?, FALSE)",
				username, ipAddress);
	}

	@Transactional
	public void recordSuccess(String username, String ipAddress) {
		jdbc.update(
				"INSERT INTO login_attempt (username, ip_address, success) VALUES (?, ?, TRUE)",
				username, ipAddress);
	}
}
