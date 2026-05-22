package com.smcs.notification;

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
class NotificationIntegrationTest {

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

	private void assignToField1() throws Exception {
		mockMvc.perform(post("/api/issues/" + issueId + "/assign")
				.header("Authorization", "Bearer " + token("agent1"))
				.contentType(MediaType.APPLICATION_JSON)
				.content("{\"assigneeId\":" + userId("field1") + "}"))
				.andExpect(status().isOk());
	}

	@BeforeEach
	void seed() throws Exception {
		jdbc.update("DELETE FROM notifications");
		jdbc.update("DELETE FROM attachments");
		jdbc.update("DELETE FROM comments");
		jdbc.update("DELETE FROM issue_events");
		jdbc.update("DELETE FROM issues");
		String body = """
				{"title":"알림대상","callerName":"홍길동","callerPhone":"010-1234-5678",
				 "categoryL1Id":%d,"categoryL2Id":%d,"categoryL3Id":%d,
				 "priority":"URGENT","description":"본문"}"""
				.formatted(categoryId(1), categoryId(2), categoryId(3));
		mockMvc.perform(post("/api/issues")
				.header("Authorization", "Bearer " + token("agent1"))
				.contentType(MediaType.APPLICATION_JSON).content(body))
				.andExpect(status().isCreated());
		issueId = jdbc.queryForObject("SELECT id FROM issues ORDER BY id DESC LIMIT 1", Long.class);
	}

	@Test
	void assignNotifiesAssignee() throws Exception {
		assignToField1();
		mockMvc.perform(get("/api/notifications").header("Authorization", "Bearer " + token("field1")))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.content[0].kind").value("ISSUE_ASSIGNED"))
				.andExpect(jsonPath("$.content[0].issueId").value((int) issueId))
				.andExpect(jsonPath("$.content[0].actorName").isNotEmpty());
		// the assigner (agent1) is not notified about their own action
		mockMvc.perform(get("/api/notifications/unread-count").header("Authorization", "Bearer " + token("agent1")))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.count").value(0));
	}

	@Test
	void commentNotifiesStakeholdersExceptAuthor() throws Exception {
		assignToField1();
		// agent1 (creator) comments → field1 (assignee) is notified; agent1 (author) is not
		mockMvc.perform(post("/api/issues/" + issueId + "/comments")
				.header("Authorization", "Bearer " + token("agent1"))
				.contentType(MediaType.APPLICATION_JSON).content("{\"body\":\"확인 바랍니다\"}"))
				.andExpect(status().isCreated());
		Integer commented = jdbc.queryForObject(
				"SELECT COUNT(*) FROM notifications WHERE recipient_id = ? AND kind = 'ISSUE_COMMENTED'",
				Integer.class, userId("field1"));
		Integer agentNotifs = jdbc.queryForObject(
				"SELECT COUNT(*) FROM notifications WHERE recipient_id = ?", Integer.class, userId("agent1"));
		Assertions.assertEquals(1, commented);
		Assertions.assertEquals(0, agentNotifs);
	}

	@Test
	void transitionNotifiesStakeholdersExceptActor() throws Exception {
		assignToField1();
		mockMvc.perform(post("/api/issues/" + issueId + "/transition")
				.header("Authorization", "Bearer " + token("agent1"))
				.contentType(MediaType.APPLICATION_JSON).content("{\"to\":\"IN_PROGRESS\"}"))
				.andExpect(status().isOk());
		Integer statusNotifs = jdbc.queryForObject(
				"SELECT COUNT(*) FROM notifications WHERE recipient_id = ? AND kind = 'ISSUE_STATUS_CHANGED'",
				Integer.class, userId("field1"));
		Assertions.assertEquals(1, statusNotifs);
	}

	@Test
	void markReadClearsUnreadCount() throws Exception {
		assignToField1();
		Long notifId = jdbc.queryForObject(
				"SELECT id FROM notifications WHERE recipient_id = ? ORDER BY id LIMIT 1",
				Long.class, userId("field1"));
		mockMvc.perform(post("/api/notifications/" + notifId + "/read")
				.header("Authorization", "Bearer " + token("field1")))
				.andExpect(status().isNoContent());
		mockMvc.perform(get("/api/notifications/unread-count").header("Authorization", "Bearer " + token("field1")))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.count").value(0));
	}

	@Test
	void cannotReadOthersNotification() throws Exception {
		assignToField1();
		Long notifId = jdbc.queryForObject(
				"SELECT id FROM notifications WHERE recipient_id = ? ORDER BY id LIMIT 1",
				Long.class, userId("field1"));
		// field2 must not be able to read field1's notification (AC6)
		mockMvc.perform(post("/api/notifications/" + notifId + "/read")
				.header("Authorization", "Bearer " + token("field2")))
				.andExpect(status().isNotFound())
				.andExpect(jsonPath("$.code").value("NOTIFICATION_NOT_FOUND"));
	}

	@Test
	void readAllClearsUnread() throws Exception {
		assignToField1();
		mockMvc.perform(post("/api/issues/" + issueId + "/comments")
				.header("Authorization", "Bearer " + token("agent1"))
				.contentType(MediaType.APPLICATION_JSON).content("{\"body\":\"코멘트\"}"))
				.andExpect(status().isCreated());
		mockMvc.perform(post("/api/notifications/read-all").header("Authorization", "Bearer " + token("field1")))
				.andExpect(status().isNoContent());
		mockMvc.perform(get("/api/notifications/unread-count").header("Authorization", "Bearer " + token("field1")))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.count").value(0));
	}

	@Test
	void unauthenticatedIsRejected() throws Exception {
		mockMvc.perform(get("/api/notifications/unread-count")).andExpect(status().isUnauthorized());
	}
}
