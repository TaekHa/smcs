package com.smcs.notification;

import static org.junit.jupiter.api.Assertions.assertEquals;

import com.smcs.user.UserRepository;
import java.sql.Timestamp;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.PostgreSQLContainer;

/**
 * Story 4.1 AC8 — verifies the 90-day retention cleanup of the notifications table.
 * Seeds report-scoped rows (issue_id NULL, V7) so we don't need to also create issues.
 */
@SpringBootTest
@ActiveProfiles("local")
class NotificationCleanupIntegrationTest {

	@SuppressWarnings("resource")
	static final PostgreSQLContainer<?> POSTGRES = new PostgreSQLContainer<>("postgres:16-alpine");

	static {
		POSTGRES.start();
	}

	@DynamicPropertySource
	static void datasourceProperties(DynamicPropertyRegistry registry) {
		registry.add("spring.datasource.url", POSTGRES::getJdbcUrl);
		registry.add("spring.datasource.username", POSTGRES::getUsername);
		registry.add("spring.datasource.password", POSTGRES::getPassword);
	}

	@Autowired
	NotificationCleanupService cleanupService;
	@Autowired
	NotificationRepository notificationRepository;
	@Autowired
	UserRepository userRepository;
	@Autowired
	JdbcTemplate jdbc;

	private static final Instant NOW = Instant.parse("2026-05-27T12:00:00Z");

	@BeforeEach
	void cleanSlate() {
		jdbc.update("DELETE FROM notifications");
	}

	private void seedReportNotification(long recipientId, String message, Instant createdAt) {
		jdbc.update(
				"INSERT INTO notifications (recipient_id, kind, issue_id, actor_id, message, read_at, created_at) "
						+ "VALUES (?, 'REPORT_READY', NULL, NULL, ?, NULL, ?)",
				recipientId, message, Timestamp.from(createdAt));
	}

	private long agentUserId() {
		return userRepository.findByUsername("agent1").orElseThrow().getId();
	}

	@Test
	void deletesNotificationsStrictlyOlderThanCutoff() {
		long uid = agentUserId();
		seedReportNotification(uid, "expired (100d old)", NOW.minus(100, ChronoUnit.DAYS));
		seedReportNotification(uid, "fresh (30d old)", NOW.minus(30, ChronoUnit.DAYS));
		seedReportNotification(uid, "newest", NOW);

		int deleted = cleanupService.cleanupExpired(NOW.minus(90, ChronoUnit.DAYS));

		assertEquals(1, deleted);
		assertEquals(2L, notificationRepository.count());
	}

	@Test
	void noopWhenAllNotificationsAreFresh() {
		long uid = agentUserId();
		seedReportNotification(uid, "fresh (10d old)", NOW.minus(10, ChronoUnit.DAYS));

		int deleted = cleanupService.cleanupExpired(NOW.minus(90, ChronoUnit.DAYS));

		assertEquals(0, deleted);
		assertEquals(1L, notificationRepository.count());
	}

	@Test
	void noopWhenTableIsEmpty() {
		int deleted = cleanupService.cleanupExpired(NOW.minus(90, ChronoUnit.DAYS));
		assertEquals(0, deleted);
	}
}
