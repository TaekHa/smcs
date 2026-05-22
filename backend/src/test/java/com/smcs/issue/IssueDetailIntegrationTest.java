package com.smcs.issue;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.smcs.security.JwtService;
import com.smcs.user.User;
import com.smcs.user.UserRepository;
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
class IssueDetailIntegrationTest {

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

	private long assignedIssueId;
	private long unassignedIssueId;

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

	private long createIssue(String title, String phone, String callerName) throws Exception {
		String body = """
				{"title":"%s","callerName":"%s","callerPhone":"%s",
				 "categoryL1Id":%d,"categoryL2Id":%d,"categoryL3Id":%d,
				 "priority":"URGENT","description":"본문 %s"}"""
				.formatted(title, callerName, phone, categoryId(1), categoryId(2), categoryId(3), title);
		mockMvc.perform(post("/api/issues")
				.header("Authorization", "Bearer " + token("agent1"))
				.contentType(MediaType.APPLICATION_JSON).content(body))
				.andExpect(status().isCreated());
		return jdbc.queryForObject("SELECT id FROM issues ORDER BY id DESC LIMIT 1", Long.class);
	}

	@BeforeEach
	void seed() throws Exception {
		jdbc.update("DELETE FROM comments");
		jdbc.update("DELETE FROM issue_events");
		jdbc.update("DELETE FROM issues");
		assignedIssueId = createIssue("배정된 이슈", "010-1234-5678", "홍길동");
		// assignment is Story 2.4 — set it directly for this test
		jdbc.update("UPDATE issues SET assigned_to = ? WHERE id = ?", userId("field1"), assignedIssueId);
		unassignedIssueId = createIssue("미배정 이슈", "010-2222-3333", "김미배정");
	}

	@Test
	void agentSeesDetailWithCallerPlaintext() throws Exception {
		mockMvc.perform(get("/api/issues/" + assignedIssueId)
				.header("Authorization", "Bearer " + token("agent1")))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.title").value("배정된 이슈"))
				.andExpect(jsonPath("$.categoryL1.name").isNotEmpty())
				.andExpect(jsonPath("$.callerName").value("홍길동"))
				.andExpect(jsonPath("$.callerPhone").value("010-1234-5678"))
				.andExpect(jsonPath("$.comments").isArray())
				.andExpect(jsonPath("$.attachments").isArray());
	}

	@Test
	void adminSeesDetailWithCallerPlaintext() throws Exception {
		mockMvc.perform(get("/api/issues/" + assignedIssueId)
				.header("Authorization", "Bearer " + token("admin1")))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.callerName").value("홍길동"));
	}

	@Test
	void fieldAssigneeSeesDetailWithoutPii() throws Exception {
		mockMvc.perform(get("/api/issues/" + assignedIssueId)
				.header("Authorization", "Bearer " + token("field1")))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.title").value("배정된 이슈"))
				.andExpect(jsonPath("$.callerName").isEmpty())
				.andExpect(jsonPath("$.callerPhone").isEmpty());
	}

	@Test
	void fieldNonAssigneeIsForbidden() throws Exception {
		mockMvc.perform(get("/api/issues/" + unassignedIssueId)
				.header("Authorization", "Bearer " + token("field1")))
				.andExpect(status().isForbidden())
				.andExpect(jsonPath("$.code").value("ISSUE_FORBIDDEN"));
	}

	@Test
	void missingIssueIs404() throws Exception {
		mockMvc.perform(get("/api/issues/99999999")
				.header("Authorization", "Bearer " + token("agent1")))
				.andExpect(status().isNotFound())
				.andExpect(jsonPath("$.code").value("ISSUE_NOT_FOUND"));
	}

	@Test
	void unauthenticatedIs401() throws Exception {
		mockMvc.perform(get("/api/issues/" + assignedIssueId))
				.andExpect(status().isUnauthorized());
	}

	@Test
	void addCommentCreatesCommentAndEvent() throws Exception {
		mockMvc.perform(post("/api/issues/" + assignedIssueId + "/comments")
				.header("Authorization", "Bearer " + token("agent1"))
				.contentType(MediaType.APPLICATION_JSON).content("{\"body\":\"확인했습니다\"}"))
				.andExpect(status().isCreated())
				.andExpect(jsonPath("$.body").value("확인했습니다"))
				.andExpect(jsonPath("$.kind").value("NOTE"))
				.andExpect(jsonPath("$.authorName").isNotEmpty());

		Integer comments = jdbc.queryForObject(
				"SELECT COUNT(*) FROM comments WHERE issue_id = ?", Integer.class, assignedIssueId);
		Integer commented = jdbc.queryForObject(
				"SELECT COUNT(*) FROM issue_events WHERE issue_id = ? AND event_type = 'COMMENTED'",
				Integer.class, assignedIssueId);
		org.junit.jupiter.api.Assertions.assertEquals(1, comments);
		org.junit.jupiter.api.Assertions.assertEquals(1, commented);
	}

	@Test
	void addCommentRejectsBlankBody() throws Exception {
		mockMvc.perform(post("/api/issues/" + assignedIssueId + "/comments")
				.header("Authorization", "Bearer " + token("agent1"))
				.contentType(MediaType.APPLICATION_JSON).content("{\"body\":\"  \"}"))
				.andExpect(status().isBadRequest())
				.andExpect(jsonPath("$.code").value("VALIDATION_FAILED"));
	}

	@Test
	void fieldNonAssigneeCannotComment() throws Exception {
		mockMvc.perform(post("/api/issues/" + unassignedIssueId + "/comments")
				.header("Authorization", "Bearer " + token("field1"))
				.contentType(MediaType.APPLICATION_JSON).content("{\"body\":\"막혀야 함\"}"))
				.andExpect(status().isForbidden())
				.andExpect(jsonPath("$.code").value("ISSUE_FORBIDDEN"));
	}

	@Test
	void eventsAreNewestFirst() throws Exception {
		mockMvc.perform(post("/api/issues/" + assignedIssueId + "/comments")
				.header("Authorization", "Bearer " + token("agent1"))
				.contentType(MediaType.APPLICATION_JSON).content("{\"body\":\"코멘트\"}"))
				.andExpect(status().isCreated());

		mockMvc.perform(get("/api/issues/" + assignedIssueId + "/events")
				.header("Authorization", "Bearer " + token("agent1")))
				.andExpect(status().isOk())
				// newest first: COMMENTED before the original CREATED
				.andExpect(jsonPath("$[0].eventType").value("COMMENTED"))
				.andExpect(jsonPath("$[1].eventType").value("CREATED"));
	}
}
