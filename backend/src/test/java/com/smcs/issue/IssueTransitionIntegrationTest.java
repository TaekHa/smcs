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
class IssueTransitionIntegrationTest {

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

	private long createIssue() throws Exception {
		String body = """
				{"title":"전이대상","callerName":"홍길동","callerPhone":"010-1234-5678",
				 "categoryL1Id":%d,"categoryL2Id":%d,"categoryL3Id":%d,
				 "priority":"URGENT","description":"본문"}"""
				.formatted(categoryId(1), categoryId(2), categoryId(3));
		mockMvc.perform(post("/api/issues")
				.header("Authorization", "Bearer " + token("agent1"))
				.contentType(MediaType.APPLICATION_JSON).content(body))
				.andExpect(status().isCreated());
		return jdbc.queryForObject("SELECT id FROM issues ORDER BY id DESC LIMIT 1", Long.class);
	}

	private void assignTo(String username, String assigneeUsername) throws Exception {
		mockMvc.perform(post("/api/issues/" + issueId + "/assign")
				.header("Authorization", "Bearer " + token(username))
				.contentType(MediaType.APPLICATION_JSON)
				.content("{\"assigneeId\":" + userId(assigneeUsername) + "}"))
				.andExpect(status().isOk());
	}

	private void transition(String username, String to) throws Exception {
		mockMvc.perform(post("/api/issues/" + issueId + "/transition")
				.header("Authorization", "Bearer " + token(username))
				.contentType(MediaType.APPLICATION_JSON).content("{\"to\":\"" + to + "\"}"))
				.andExpect(status().isOk());
	}

	@BeforeEach
	void seed() throws Exception {
		jdbc.update("DELETE FROM comments");
		jdbc.update("DELETE FROM issue_events");
		jdbc.update("DELETE FROM issues");
		issueId = createIssue(); // status NEW
	}

	@Test
	void agentAssignsFieldAndAutoTransitionsToAssigned() throws Exception {
		mockMvc.perform(post("/api/issues/" + issueId + "/assign")
				.header("Authorization", "Bearer " + token("agent1"))
				.contentType(MediaType.APPLICATION_JSON)
				.content("{\"assigneeId\":" + userId("field1") + "}"))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.status").value("ASSIGNED"))
				.andExpect(jsonPath("$.assigneeName").isNotEmpty());

		Long assignedTo = jdbc.queryForObject(
				"SELECT assigned_to FROM issues WHERE id = ?", Long.class, issueId);
		Integer assignedEvents = jdbc.queryForObject(
				"SELECT COUNT(*) FROM issue_events WHERE issue_id = ? AND event_type = 'ASSIGNED'",
				Integer.class, issueId);
		Assertions.assertEquals(userId("field1"), assignedTo);
		Assertions.assertEquals(1, assignedEvents);
	}

	@Test
	void assigningNonFieldUserIs400() throws Exception {
		mockMvc.perform(post("/api/issues/" + issueId + "/assign")
				.header("Authorization", "Bearer " + token("agent1"))
				.contentType(MediaType.APPLICATION_JSON)
				.content("{\"assigneeId\":" + userId("agent2") + "}"))
				.andExpect(status().isBadRequest())
				.andExpect(jsonPath("$.code").value("INVALID_ASSIGNEE"));
	}

	@Test
	void fieldCannotAssign() throws Exception {
		mockMvc.perform(post("/api/issues/" + issueId + "/assign")
				.header("Authorization", "Bearer " + token("field1"))
				.contentType(MediaType.APPLICATION_JSON)
				.content("{\"assigneeId\":" + userId("field1") + "}"))
				.andExpect(status().isForbidden());
	}

	@Test
	void assignedToInProgressRecordsStatusChanged() throws Exception {
		assignTo("agent1", "field1"); // NEW → ASSIGNED
		transition("agent1", "IN_PROGRESS");

		Integer statusChanged = jdbc.queryForObject(
				"SELECT COUNT(*) FROM issue_events WHERE issue_id = ? AND event_type = 'STATUS_CHANGED'",
				Integer.class, issueId);
		String status = jdbc.queryForObject("SELECT status FROM issues WHERE id = ?", String.class, issueId);
		Assertions.assertEquals(1, statusChanged);
		Assertions.assertEquals("IN_PROGRESS", status);
	}

	@Test
	void inProgressToDoneRecordsResolvedAndStampsResolvedAt() throws Exception {
		assignTo("agent1", "field1");
		transition("agent1", "IN_PROGRESS");
		transition("agent1", "DONE");

		Integer resolved = jdbc.queryForObject(
				"SELECT COUNT(*) FROM issue_events WHERE issue_id = ? AND event_type = 'RESOLVED'",
				Integer.class, issueId);
		Object resolvedAt = jdbc.queryForMap("SELECT resolved_at FROM issues WHERE id = ?", issueId)
				.get("resolved_at");
		Assertions.assertEquals(1, resolved);
		Assertions.assertNotNull(resolvedAt);
	}

	@Test
	void invalidTransitionIs409() throws Exception {
		// NEW has no valid transition to DONE (AC5: NEW→DONE 불가)
		mockMvc.perform(post("/api/issues/" + issueId + "/transition")
				.header("Authorization", "Bearer " + token("agent1"))
				.contentType(MediaType.APPLICATION_JSON).content("{\"to\":\"DONE\"}"))
				.andExpect(status().isConflict())
				.andExpect(jsonPath("$.code").value("INVALID_TRANSITION"));
	}

	@Test
	void fieldNonAssigneeCannotTransition() throws Exception {
		assignTo("agent1", "field2"); // assigned to field2
		mockMvc.perform(post("/api/issues/" + issueId + "/transition")
				.header("Authorization", "Bearer " + token("field1")) // not the assignee
				.contentType(MediaType.APPLICATION_JSON).content("{\"to\":\"IN_PROGRESS\"}"))
				.andExpect(status().isForbidden())
				.andExpect(jsonPath("$.code").value("ISSUE_FORBIDDEN"));
	}

	@Test
	void missingIssueAndUnauthenticated() throws Exception {
		mockMvc.perform(post("/api/issues/99999999/transition")
				.header("Authorization", "Bearer " + token("agent1"))
				.contentType(MediaType.APPLICATION_JSON).content("{\"to\":\"IN_PROGRESS\"}"))
				.andExpect(status().isNotFound())
				.andExpect(jsonPath("$.code").value("ISSUE_NOT_FOUND"));
		mockMvc.perform(post("/api/issues/" + issueId + "/assign")
				.contentType(MediaType.APPLICATION_JSON).content("{\"assigneeId\":1}"))
				.andExpect(status().isUnauthorized());
	}
}
