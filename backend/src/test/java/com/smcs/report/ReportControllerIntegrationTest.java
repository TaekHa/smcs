package com.smcs.report;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.smcs.security.JwtService;
import com.smcs.user.User;
import com.smcs.user.UserRepository;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.time.ZoneId;
import java.time.ZoneOffset;
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
 * Verifies the PDF report endpoints end-to-end. Local Windows skips Testcontainers — runs in CI
 * (Story 2.x precedent). Asserts ADMIN authz (§6), inline PDF stream, and 400 on bad period.
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("local")
class ReportControllerIntegrationTest {

	private static final ZoneId KST = ZoneId.of("Asia/Seoul");

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

	@Autowired MockMvc mockMvc;
	@Autowired JwtService jwtService;
	@Autowired UserRepository userRepository;
	@Autowired JdbcTemplate jdbc;

	private String token(String username) {
		User u = userRepository.findByUsername(username).orElseThrow();
		return jwtService.generate(u.getId(), u.getRole()).token();
	}

	private long userId(String username) {
		return userRepository.findByUsername(username).orElseThrow().getId();
	}

	private long categoryId(int level) {
		return jdbc.queryForObject(
				"SELECT id FROM categories WHERE level = ? ORDER BY sort_order LIMIT 1", Long.class, level);
	}

	private static Object ts(Instant i) {
		return i == null ? null : OffsetDateTime.ofInstant(i, ZoneOffset.UTC);
	}

	@BeforeEach
	void seed() {
		jdbc.update("DELETE FROM notifications");
		jdbc.update("DELETE FROM attachments");
		jdbc.update("DELETE FROM issue_events");
		jdbc.update("DELETE FROM issues");

		Instant base = LocalDate.now(KST).atTime(12, 0).atZone(KST).toInstant();
		jdbc.update(
				"INSERT INTO issues (title, description, category_l1_id, category_l2_id, category_l3_id, "
						+ "priority, status, created_by, assigned_to, resolved_at, created_at, updated_at) "
						+ "VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)",
				"미처리 이슈", null, categoryId(1), categoryId(2), categoryId(3),
				"HIGH", "IN_PROGRESS", userId("agent1"), userId("field1"), null, ts(base), ts(base));
	}

	@Test
	void dailyReturnsPdfForAdmin() throws Exception {
		String today = LocalDate.now(KST).toString();
		byte[] body = mockMvc.perform(get("/api/reports/daily?date=" + today)
				.header("Authorization", "Bearer " + token("admin1")))
				.andExpect(status().isOk())
				.andExpect(content().contentTypeCompatibleWith("application/pdf"))
				.andExpect(header().string("Content-Disposition", Matchers.containsString("inline")))
				.andExpect(header().string("Content-Disposition", Matchers.containsString("daily-" + today + ".pdf")))
				.andReturn().getResponse().getContentAsByteArray();
		assertThat(body).hasSizeGreaterThan(4);
		assertThat(new String(body, 0, 4, StandardCharsets.US_ASCII)).isEqualTo("%PDF");
	}

	@Test
	void weeklyReturnsPdfForAdmin() throws Exception {
		// Build the current ISO week token.
		java.time.LocalDate today = java.time.LocalDate.now(KST);
		int year = today.get(java.time.temporal.IsoFields.WEEK_BASED_YEAR);
		int week = today.get(java.time.temporal.IsoFields.WEEK_OF_WEEK_BASED_YEAR);
		String token = String.format("%d-W%02d", year, week);

		mockMvc.perform(get("/api/reports/weekly?week=" + token)
				.header("Authorization", "Bearer " + token("admin1")))
				.andExpect(status().isOk())
				.andExpect(content().contentTypeCompatibleWith("application/pdf"));
	}

	@Test
	void unauthenticatedDailyIs401() throws Exception {
		mockMvc.perform(get("/api/reports/daily?date=2026-05-21"))
				.andExpect(status().isUnauthorized());
	}

	@Test
	void agentForbiddenFromDaily() throws Exception {
		mockMvc.perform(get("/api/reports/daily?date=2026-05-21")
				.header("Authorization", "Bearer " + token("agent1")))
				.andExpect(status().isForbidden());
	}

	@Test
	void fieldForbiddenFromDaily() throws Exception {
		mockMvc.perform(get("/api/reports/daily?date=2026-05-21")
				.header("Authorization", "Bearer " + token("field1")))
				.andExpect(status().isForbidden());
	}

	@Test
	void invalidDateIs400() throws Exception {
		mockMvc.perform(get("/api/reports/daily?date=not-a-date")
				.header("Authorization", "Bearer " + token("admin1")))
				.andExpect(status().isBadRequest())
				.andExpect(jsonPath("$.code").value("VALIDATION_FAILED"));
	}

	@Test
	void missingDateIs400() throws Exception {
		mockMvc.perform(get("/api/reports/daily")
				.header("Authorization", "Bearer " + token("admin1")))
				.andExpect(status().isBadRequest())
				.andExpect(jsonPath("$.code").value("VALIDATION_FAILED"));
	}

	@Test
	void invalidWeekIs400() throws Exception {
		mockMvc.perform(get("/api/reports/weekly?week=bad")
				.header("Authorization", "Bearer " + token("admin1")))
				.andExpect(status().isBadRequest())
				.andExpect(jsonPath("$.code").value("VALIDATION_FAILED"));
	}

}
