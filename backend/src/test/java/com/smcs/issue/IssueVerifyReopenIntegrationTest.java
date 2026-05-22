package com.smcs.issue;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.smcs.security.JwtService;
import com.smcs.user.User;
import com.smcs.user.UserRepository;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.testcontainers.containers.PostgreSQLContainer;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("local")
class IssueVerifyReopenIntegrationTest {

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

	private long issueId;

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

	@BeforeEach
	void seed() throws Exception {
		jdbc.update("DELETE FROM notifications");
		jdbc.update("DELETE FROM attachments");
		jdbc.update("DELETE FROM comments");
		jdbc.update("DELETE FROM issue_events");
		jdbc.update("DELETE FROM issues");
		String body = """
				{"title":"검수대상","callerName":"홍길동","callerPhone":"010-1234-5678",
				 "categoryL1Id":%d,"categoryL2Id":%d,"categoryL3Id":%d,
				 "priority":"URGENT","description":"본문"}"""
				.formatted(categoryId(1), categoryId(2), categoryId(3));
		mockMvc.perform(post("/api/issues")
				.header("Authorization", "Bearer " + token("agent1"))
				.contentType(MediaType.APPLICATION_JSON).content(body))
				.andExpect(status().isCreated());
		issueId = jdbc.queryForObject("SELECT id FROM issues ORDER BY id DESC LIMIT 1", Long.class);
		// put it in DONE, assigned to field1, resolved
		jdbc.update("UPDATE issues SET status = 'DONE', assigned_to = ?, resolved_at = NOW() WHERE id = ?",
				userId("field1"), issueId);
	}

	@Test
	void agentVerifiesDoneIssue() throws Exception {
		mockMvc.perform(post("/api/issues/" + issueId + "/transition")
				.header("Authorization", "Bearer " + token("agent1"))
				.contentType(MediaType.APPLICATION_JSON).content("{\"to\":\"VERIFIED\"}"))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.status").value("VERIFIED"));
		Integer statusChanged = jdbc.queryForObject(
				"SELECT COUNT(*) FROM issue_events WHERE issue_id = ? AND event_type = 'STATUS_CHANGED'",
				Integer.class, issueId);
		Assertions.assertEquals(1, statusChanged);
	}

	@Test
	void agentReopensWithReason() throws Exception {
		mockMvc.perform(post("/api/issues/" + issueId + "/transition")
				.header("Authorization", "Bearer " + token("agent1"))
				.contentType(MediaType.APPLICATION_JSON)
				.content("{\"to\":\"IN_PROGRESS\",\"reason\":\"추가 작업 필요\"}"))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.status").value("IN_PROGRESS"))
				.andExpect(jsonPath("$.resolvedAt").isEmpty());
		Integer reasonComment = jdbc.queryForObject(
				"SELECT COUNT(*) FROM comments WHERE issue_id = ? AND kind = 'NOTE' AND body LIKE '재오픈 사유:%'",
				Integer.class, issueId);
		Integer commented = jdbc.queryForObject(
				"SELECT COUNT(*) FROM issue_events WHERE issue_id = ? AND event_type = 'COMMENTED'",
				Integer.class, issueId);
		Integer reopened = jdbc.queryForObject(
				"SELECT COUNT(*) FROM notifications WHERE issue_id = ? AND kind = 'ISSUE_REOPENED'",
				Integer.class, issueId);
		Assertions.assertEquals(1, reasonComment);
		Assertions.assertEquals(1, commented);
		Assertions.assertEquals(1, reopened);
	}

	@Test
	void reopenWithoutReasonIs400() throws Exception {
		mockMvc.perform(post("/api/issues/" + issueId + "/transition")
				.header("Authorization", "Bearer " + token("agent1"))
				.contentType(MediaType.APPLICATION_JSON).content("{\"to\":\"IN_PROGRESS\"}"))
				.andExpect(status().isBadRequest())
				.andExpect(jsonPath("$.code").value("REOPEN_REASON_REQUIRED"));
	}

	@Test
	void fieldCannotVerifyOrReopen() throws Exception {
		mockMvc.perform(post("/api/issues/" + issueId + "/transition")
				.header("Authorization", "Bearer " + token("field1")) // assignee, but DONE-origin is AGENT/ADMIN only
				.contentType(MediaType.APPLICATION_JSON).content("{\"to\":\"VERIFIED\"}"))
				.andExpect(status().isForbidden())
				.andExpect(jsonPath("$.code").value("ISSUE_FORBIDDEN"));
		mockMvc.perform(post("/api/issues/" + issueId + "/transition")
				.header("Authorization", "Bearer " + token("field1"))
				.contentType(MediaType.APPLICATION_JSON)
				.content("{\"to\":\"IN_PROGRESS\",\"reason\":\"x\"}"))
				.andExpect(status().isForbidden())
				.andExpect(jsonPath("$.code").value("ISSUE_FORBIDDEN"));
	}

	@Test
	void invalidTransitionFromDoneIs409() throws Exception {
		mockMvc.perform(post("/api/issues/" + issueId + "/transition")
				.header("Authorization", "Bearer " + token("agent1"))
				.contentType(MediaType.APPLICATION_JSON).content("{\"to\":\"ASSIGNED\"}"))
				.andExpect(status().isConflict())
				.andExpect(jsonPath("$.code").value("INVALID_TRANSITION"));
	}
}
