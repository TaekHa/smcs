package com.smcs.report;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.smcs.report.dto.ReportKind;
import com.smcs.security.JwtService;
import com.smcs.user.User;
import com.smcs.user.UserRepository;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import org.hamcrest.Matchers;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.testcontainers.containers.PostgreSQLContainer;

/**
 * Story 3.5 archive endpoints + cleanup job. Covers the list API (page/sort/authz), the file
 * stream (preview/download disposition, 404, authz), and the cleanup service against a real DB.
 * CI-only (Docker required) — Story 2.x/3.x precedent.
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("local")
class ReportArchiveAccessIntegrationTest {

	@SuppressWarnings("resource")
	static final PostgreSQLContainer<?> POSTGRES = new PostgreSQLContainer<>("postgres:16-alpine");

	static final Path FILES_DIR;

	static {
		POSTGRES.start();
		try {
			FILES_DIR = Files.createTempDirectory("smcs-reports-3-5-test");
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
		// Keep every scheduler quiet so timer-driven inserts don't pollute the assertions.
		registry.add("smcs.reports.daily-cron", () -> "0 0 5 31 2 *");
		registry.add("smcs.reports.weekly-cron", () -> "0 0 5 31 2 *");
		registry.add("smcs.reports.cleanup-cron", () -> "0 0 5 31 2 *");
	}

	@Autowired MockMvc mockMvc;
	@Autowired JwtService jwtService;
	@Autowired UserRepository userRepository;
	@Autowired JdbcTemplate jdbc;
	@Autowired ReportCleanupService cleanupService;

	private String token(String username) {
		User u = userRepository.findByUsername(username).orElseThrow();
		return jwtService.generate(u.getId(), u.getRole()).token();
	}

	@BeforeEach
	void seed() {
		jdbc.update("DELETE FROM reports");
	}

	private long insertReport(ReportKind kind, String periodKey, Instant createdAt) throws IOException {
		String relative = "reports/" + kind.name() + "/" + periodKey + ".pdf";
		Path target = FILES_DIR.resolve(relative);
		Files.createDirectories(target.getParent());
		Files.writeString(target, "%PDF-fake-" + periodKey, StandardCharsets.US_ASCII);
		long size = Files.size(target);
		jdbc.update(
				"INSERT INTO reports (kind, period_key, file_path, size_bytes, created_at) VALUES (?, ?, ?, ?, ?)",
				kind.name(), periodKey, relative, size, java.sql.Timestamp.from(createdAt));
		return jdbc.queryForObject(
				"SELECT id FROM reports WHERE kind = ? AND period_key = ?",
				Long.class, kind.name(), periodKey);
	}

	// ── List endpoint ────────────────────────────────────────────────────────────

	@Test
	void listReturnsNewestFirstForAdmin() throws Exception {
		Instant t0 = Instant.now().truncatedTo(ChronoUnit.SECONDS);
		insertReport(ReportKind.DAILY, "2026-05-20", t0.minus(2, ChronoUnit.DAYS));
		insertReport(ReportKind.DAILY, "2026-05-21", t0.minus(1, ChronoUnit.DAYS));
		insertReport(ReportKind.WEEKLY, "2026-W21", t0);

		mockMvc.perform(get("/api/reports?kind=DAILY")
				.header("Authorization", "Bearer " + token("admin1")))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.content.length()").value(2))
				.andExpect(jsonPath("$.content[0].periodKey").value("2026-05-21")) // newest first
				.andExpect(jsonPath("$.content[1].periodKey").value("2026-05-20"))
				.andExpect(jsonPath("$.content[0].kind").value("DAILY"))
				// filePath must NOT leak (security)
				.andExpect(jsonPath("$.content[0].filePath").doesNotExist());
	}

	@Test
	void listRequiresKindAndRejectsInvalid() throws Exception {
		mockMvc.perform(get("/api/reports?kind=YEARLY")
				.header("Authorization", "Bearer " + token("admin1")))
				.andExpect(status().isBadRequest())
				.andExpect(jsonPath("$.code").value("VALIDATION_FAILED"));
	}

	@Test
	void listUnauthenticatedIs401() throws Exception {
		mockMvc.perform(get("/api/reports?kind=DAILY")).andExpect(status().isUnauthorized());
	}

	@Test
	void listAgentForbidden() throws Exception {
		mockMvc.perform(get("/api/reports?kind=DAILY")
				.header("Authorization", "Bearer " + token("agent1")))
				.andExpect(status().isForbidden());
	}

	@Test
	void listFieldForbidden() throws Exception {
		mockMvc.perform(get("/api/reports?kind=DAILY")
				.header("Authorization", "Bearer " + token("field1")))
				.andExpect(status().isForbidden());
	}

	// ── File endpoint ────────────────────────────────────────────────────────────

	@Test
	void previewReturnsInlinePdf() throws Exception {
		long id = insertReport(ReportKind.DAILY, "2026-05-21", Instant.now());

		mockMvc.perform(get("/api/reports/" + id + "/file?mode=preview")
				.header("Authorization", "Bearer " + token("admin1")))
				.andExpect(status().isOk())
				.andExpect(content().contentTypeCompatibleWith("application/pdf"))
				.andExpect(header().string("Content-Disposition", Matchers.containsString("inline")))
				.andExpect(header().string("Content-Disposition", Matchers.containsString("DAILY-2026-05-21.pdf")));
	}

	@Test
	void downloadReturnsAttachmentPdf() throws Exception {
		long id = insertReport(ReportKind.DAILY, "2026-05-21", Instant.now());

		mockMvc.perform(get("/api/reports/" + id + "/file?mode=download")
				.header("Authorization", "Bearer " + token("admin1")))
				.andExpect(status().isOk())
				.andExpect(header().string("Content-Disposition", Matchers.containsString("attachment")))
				.andExpect(header().string("Content-Disposition", Matchers.containsString("DAILY-2026-05-21.pdf")));
	}

	@Test
	void filePreviewDefaultsWhenModeOmitted() throws Exception {
		long id = insertReport(ReportKind.DAILY, "2026-05-21", Instant.now());

		mockMvc.perform(get("/api/reports/" + id + "/file")
				.header("Authorization", "Bearer " + token("admin1")))
				.andExpect(status().isOk())
				.andExpect(header().string("Content-Disposition", Matchers.containsString("inline")));
	}

	@Test
	void fileInvalidModeIs400() throws Exception {
		long id = insertReport(ReportKind.DAILY, "2026-05-21", Instant.now());

		mockMvc.perform(get("/api/reports/" + id + "/file?mode=hack")
				.header("Authorization", "Bearer " + token("admin1")))
				.andExpect(status().isBadRequest())
				.andExpect(jsonPath("$.code").value("VALIDATION_FAILED"));
	}

	@Test
	void fileUnknownIdIs404() throws Exception {
		mockMvc.perform(get("/api/reports/999999/file?mode=preview")
				.header("Authorization", "Bearer " + token("admin1")))
				.andExpect(status().isNotFound())
				.andExpect(jsonPath("$.code").value("REPORT_NOT_FOUND"));
	}

	@Test
	void fileAgentForbidden() throws Exception {
		long id = insertReport(ReportKind.DAILY, "2026-05-21", Instant.now());

		mockMvc.perform(get("/api/reports/" + id + "/file?mode=preview")
				.header("Authorization", "Bearer " + token("agent1")))
				.andExpect(status().isForbidden());
	}

	// ── Cleanup service ──────────────────────────────────────────────────────────

	@Test
	void cleanupDeletesOnlyExpiredFilesAndRows() throws Exception {
		Instant now = Instant.now();
		long expiredId = insertReport(ReportKind.DAILY, "2025-12-01", now.minus(120, ChronoUnit.DAYS));
		long keepId = insertReport(ReportKind.DAILY, "2026-05-21", now.minus(2, ChronoUnit.DAYS));
		Instant cutoff = now.minus(90, ChronoUnit.DAYS);

		int deleted = cleanupService.cleanupExpired(cutoff);

		assertThat(deleted).isEqualTo(1);
		Integer expiredRows = jdbc.queryForObject(
				"SELECT COUNT(*) FROM reports WHERE id = ?", Integer.class, expiredId);
		assertThat(expiredRows).isZero();
		Integer keepRows = jdbc.queryForObject(
				"SELECT COUNT(*) FROM reports WHERE id = ?", Integer.class, keepId);
		assertThat(keepRows).isEqualTo(1);

		assertThat(Files.exists(FILES_DIR.resolve("reports/DAILY/2025-12-01.pdf"))).isFalse();
		assertThat(Files.exists(FILES_DIR.resolve("reports/DAILY/2026-05-21.pdf"))).isTrue();
	}
}
