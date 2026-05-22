package com.smcs.stats;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.smcs.security.JwtService;
import com.smcs.user.User;
import com.smcs.user.UserRepository;
import java.time.DayOfWeek;
import java.time.Instant;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.time.temporal.TemporalAdjusters;
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
 * Verifies aggregation correctness against a controlled seed (all issues created "today" KST so
 * period=today captures them). Local Windows skips Testcontainers — runs in CI (Story 2.x precedent).
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("local")
class StatsControllerIntegrationTest {

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

	@Autowired
	MockMvc mockMvc;
	@Autowired
	JwtService jwtService;
	@Autowired
	UserRepository userRepository;
	@Autowired
	JdbcTemplate jdbc;

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

	private void insertIssue(String priority, String status, Long assignedTo, Instant createdAt, Instant resolvedAt) {
		jdbc.update(
				"INSERT INTO issues (title, description, category_l1_id, category_l2_id, category_l3_id, "
						+ "priority, status, created_by, assigned_to, resolved_at, created_at, updated_at) "
						+ "VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)",
				"stat-" + priority + "-" + status, null,
				categoryId(1), categoryId(2), categoryId(3),
				priority, status, userId("agent1"), assignedTo,
				ts(resolvedAt), ts(createdAt), ts(createdAt));
	}

	@BeforeEach
	void seed() {
		jdbc.update("DELETE FROM notifications");
		jdbc.update("DELETE FROM attachments");
		jdbc.update("DELETE FROM issue_events");
		jdbc.update("DELETE FROM issues");

		Instant base = LocalDate.now(KST).atTime(12, 0).atZone(KST).toInstant(); // today noon KST
		long field1 = userId("field1");
		long field2 = userId("field2");

		insertIssue("URGENT", "NEW", null, base, null);
		insertIssue("HIGH", "DONE", field1, base, base.plusSeconds(60 * 60));   // resolved in 60 min
		insertIssue("NORMAL", "VERIFIED", field1, base, base.plusSeconds(120 * 60)); // resolved in 120 min
		insertIssue("LOW", "IN_PROGRESS", field2, base, null);
	}

	@Test
	void dashboardAggregatesTodayCorrectly() throws Exception {
		mockMvc.perform(get("/api/stats/dashboard?period=today")
				.header("Authorization", "Bearer " + token("admin1")))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.kpi.newCount").value(4))
				.andExpect(jsonPath("$.kpi.resolvedCount").value(2))
				.andExpect(jsonPath("$.kpi.openCount").value(2)) // NEW + IN_PROGRESS
				.andExpect(jsonPath("$.kpi.avgResolveMinutes").value(90)) // (60+120)/2
				.andExpect(jsonPath("$.byCategory.length()").value(1)) // all share one L1
				.andExpect(jsonPath("$.byCategory[0].count").value(4))
				.andExpect(jsonPath("$.byPriority.length()").value(4))
				.andExpect(jsonPath("$.byAssignee.length()").value(1)) // only field1 resolved
				.andExpect(jsonPath("$.byAssignee[0].resolved").value(2))
				.andExpect(jsonPath("$.trend.length()").value(1)) // single day
				.andExpect(jsonPath("$.trend[0].newCount").value(4))
				.andExpect(jsonPath("$.trend[0].resolvedCount").value(2));
	}

	@Test
	void defaultPeriodIsToday() throws Exception {
		mockMvc.perform(get("/api/stats/dashboard")
				.header("Authorization", "Bearer " + token("agent1")))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.kpi.newCount").value(4));
	}

	@Test
	void weekTrendSpansMultipleKstDays() throws Exception {
		// Override the @BeforeEach today-seed with a 2-day spread inside the current ISO week.
		jdbc.update("DELETE FROM issues");
		LocalDate monday = LocalDate.now(KST).with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY));
		Instant monNoon = monday.atTime(12, 0).atZone(KST).toInstant();
		Instant tueNoon = monday.plusDays(1).atTime(12, 0).atZone(KST).toInstant();
		long field1 = userId("field1");
		// Monday: 2 created, 1 resolved
		insertIssue("URGENT", "NEW", null, monNoon, null);
		insertIssue("HIGH", "DONE", field1, monNoon, monNoon.plusSeconds(1800));
		// Tuesday: 1 created, 1 resolved
		insertIssue("NORMAL", "DONE", field1, tueNoon, tueNoon.plusSeconds(1800));

		mockMvc.perform(get("/api/stats/dashboard?period=week")
				.header("Authorization", "Bearer " + token("admin1")))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.trend.length()").value(7)) // Mon..Sun, zero-filled
				.andExpect(jsonPath("$.trend[0].newCount").value(2)) // Monday
				.andExpect(jsonPath("$.trend[0].resolvedCount").value(1))
				.andExpect(jsonPath("$.trend[1].newCount").value(1)) // Tuesday
				.andExpect(jsonPath("$.trend[1].resolvedCount").value(1))
				.andExpect(jsonPath("$.kpi.newCount").value(3))
				.andExpect(jsonPath("$.kpi.resolvedCount").value(2));
	}

	@Test
	void unauthenticatedIsRejected() throws Exception {
		mockMvc.perform(get("/api/stats/dashboard?period=today")).andExpect(status().isUnauthorized());
	}

	@Test
	void invalidPeriodIsBadRequest() throws Exception {
		mockMvc.perform(get("/api/stats/dashboard?period=year")
				.header("Authorization", "Bearer " + token("admin1")))
				.andExpect(status().isBadRequest())
				.andExpect(jsonPath("$.code").value("VALIDATION_FAILED"));
	}
}
