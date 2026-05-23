package com.smcs.report;

import static org.assertj.core.api.Assertions.assertThat;

import com.smcs.user.UserRepository;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.time.ZoneId;
import java.time.ZoneOffset;
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
 * Verifies the scheduler-orchestrated archive end-to-end (Story 3.4). Covers V6/V7 schema apply
 * + idempotent upsert (AC4) + REPORT_READY fan-out to active ADMINs (AC6) with {@code issue_id IS NULL}.
 * Failure paths are covered by {@link ReportArchiveServiceTest}; running the real PDF engine here
 * keeps the integration test honest about font + file IO. CI-only (Docker required).
 */
@SpringBootTest
@ActiveProfiles("local")
class ReportArchiveIntegrationTest {

	private static final ZoneId KST = ZoneId.of("Asia/Seoul");

	@SuppressWarnings("resource")
	static final PostgreSQLContainer<?> POSTGRES = new PostgreSQLContainer<>("postgres:16-alpine");

	static final Path FILES_DIR;

	static {
		POSTGRES.start();
		try {
			FILES_DIR = Files.createTempDirectory("smcs-reports-test");
		} catch (IOException e) {
			throw new IllegalStateException(e);
		}
	}

	@DynamicPropertySource
	static void properties(DynamicPropertyRegistry registry) {
		registry.add("spring.datasource.url", POSTGRES::getJdbcUrl);
		registry.add("spring.datasource.username", POSTGRES::getUsername);
		registry.add("spring.datasource.password", POSTGRES::getPassword);
		registry.add("smcs.files.dir", () -> FILES_DIR.toString());
		// Disable both cron jobs in tests (Spring rejects a literal "disabled" — use an unreachable cron).
		registry.add("smcs.reports.daily-cron", () -> "0 0 5 31 2 *");
		registry.add("smcs.reports.weekly-cron", () -> "0 0 5 31 2 *");
	}

	@Autowired ReportArchiveService archiveService;
	@Autowired ReportRepository reportRepository;
	@Autowired UserRepository userRepository;
	@Autowired JdbcTemplate jdbc;

	@BeforeEach
	void seed() {
		jdbc.update("DELETE FROM notifications");
		jdbc.update("DELETE FROM attachments");
		jdbc.update("DELETE FROM comments");
		jdbc.update("DELETE FROM issue_events");
		jdbc.update("DELETE FROM issues");
		jdbc.update("DELETE FROM reports");

		// One open issue so the PDF has at least one row in the "미처리" section.
		Instant base = LocalDate.now(KST).atTime(12, 0).atZone(KST).toInstant();
		long agentId = userRepository.findByUsername("agent1").orElseThrow().getId();
		long fieldId = userRepository.findByUsername("field1").orElseThrow().getId();
		long catL1 = jdbc.queryForObject(
				"SELECT id FROM categories WHERE level = 1 ORDER BY sort_order LIMIT 1", Long.class);
		long catL2 = jdbc.queryForObject(
				"SELECT id FROM categories WHERE level = 2 ORDER BY sort_order LIMIT 1", Long.class);
		long catL3 = jdbc.queryForObject(
				"SELECT id FROM categories WHERE level = 3 ORDER BY sort_order LIMIT 1", Long.class);
		jdbc.update(
				"INSERT INTO issues (title, description, category_l1_id, category_l2_id, category_l3_id, "
						+ "priority, status, created_by, assigned_to, resolved_at, created_at, updated_at) "
						+ "VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)",
				"보관함 테스트 이슈", null, catL1, catL2, catL3,
				"HIGH", "IN_PROGRESS", agentId, fieldId, null, ts(base), ts(base));
	}

	@Test
	void dailyJobInsertsMetadataAndAdminAlerts() {
		LocalDate yesterday = LocalDate.now(KST).minusDays(1);
		long adminCount = countActiveAdmins();

		archiveService.generateAndStoreDaily(yesterday);

		Integer reportRows = jdbc.queryForObject(
				"SELECT COUNT(*) FROM reports WHERE kind = 'DAILY' AND period_key = ?",
				Integer.class, yesterday.toString());
		assertThat(reportRows).isEqualTo(1);

		Integer readyAlerts = jdbc.queryForObject(
				"SELECT COUNT(*) FROM notifications WHERE kind = 'REPORT_READY' AND issue_id IS NULL",
				Integer.class);
		assertThat((long) readyAlerts).isEqualTo(adminCount);

		String filePath = jdbc.queryForObject(
				"SELECT file_path FROM reports WHERE kind = 'DAILY' AND period_key = ?",
				String.class, yesterday.toString());
		assertThat(Files.exists(FILES_DIR.resolve(filePath))).isTrue();
	}

	@Test
	void rerunUpsertsTheSameRowAndKeepsCreatedAt() throws Exception {
		LocalDate yesterday = LocalDate.now(KST).minusDays(1);
		archiveService.generateAndStoreDaily(yesterday);
		Instant firstCreatedAt = reportRepository
				.findByKindAndPeriodKey(com.smcs.report.dto.ReportKind.DAILY, yesterday.toString())
				.orElseThrow().getCreatedAt();

		Thread.sleep(10); // ensure a measurable gap in case createdAt were (incorrectly) reset
		archiveService.generateAndStoreDaily(yesterday);

		Integer rows = jdbc.queryForObject(
				"SELECT COUNT(*) FROM reports WHERE kind = 'DAILY' AND period_key = ?",
				Integer.class, yesterday.toString());
		assertThat(rows).isEqualTo(1); // upsert — no new row

		Instant afterRerun = reportRepository
				.findByKindAndPeriodKey(com.smcs.report.dto.ReportKind.DAILY, yesterday.toString())
				.orElseThrow().getCreatedAt();
		assertThat(afterRerun).isEqualTo(firstCreatedAt); // createdAt preserved

		// Two REPORT_READY rows (one per run) per active ADMIN — every successful archive alerts.
		Integer readyAlerts = jdbc.queryForObject(
				"SELECT COUNT(*) FROM notifications WHERE kind = 'REPORT_READY' AND issue_id IS NULL",
				Integer.class);
		assertThat((long) readyAlerts).isEqualTo(countActiveAdmins() * 2L);
	}

	@Test
	void weeklyJobStoresUnderWeeklyKindAndIsoWeekKey() {
		LocalDate lastWeek = LocalDate.now(KST).minusWeeks(1);
		int year = lastWeek.get(java.time.temporal.IsoFields.WEEK_BASED_YEAR);
		int week = lastWeek.get(java.time.temporal.IsoFields.WEEK_OF_WEEK_BASED_YEAR);
		String periodKey = String.format("%d-W%02d", year, week);

		archiveService.generateAndStoreWeekly(year, week);

		Integer rows = jdbc.queryForObject(
				"SELECT COUNT(*) FROM reports WHERE kind = 'WEEKLY' AND period_key = ?",
				Integer.class, periodKey);
		assertThat(rows).isEqualTo(1);

		String filePath = jdbc.queryForObject(
				"SELECT file_path FROM reports WHERE kind = 'WEEKLY' AND period_key = ?",
				String.class, periodKey);
		assertThat(filePath).startsWith("reports/WEEKLY/");
		assertThat(Files.exists(FILES_DIR.resolve(filePath))).isTrue();
	}

	private long countActiveAdmins() {
		return jdbc.queryForObject(
				"SELECT COUNT(*) FROM users WHERE role = 'ADMIN' AND active = true", Long.class);
	}

	private static Object ts(Instant i) {
		return OffsetDateTime.ofInstant(i, ZoneOffset.UTC);
	}
}
