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
class MeAssignedIntegrationTest {

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

	private long createIssue(String title, String priority) throws Exception {
		String body = """
				{"title":"%s","callerName":"홍길동","callerPhone":"010-1234-5678",
				 "categoryL1Id":%d,"categoryL2Id":%d,"categoryL3Id":%d,
				 "priority":"%s","description":"본문"}"""
				.formatted(title, categoryId(1), categoryId(2), categoryId(3), priority);
		mockMvc.perform(post("/api/issues")
				.header("Authorization", "Bearer " + token("agent1"))
				.contentType(MediaType.APPLICATION_JSON).content(body))
				.andExpect(status().isCreated());
		return jdbc.queryForObject("SELECT id FROM issues ORDER BY id DESC LIMIT 1", Long.class);
	}

	@BeforeEach
	void seed() throws Exception {
		jdbc.update("DELETE FROM notifications");
		jdbc.update("DELETE FROM attachments");
		jdbc.update("DELETE FROM issue_events");
		jdbc.update("DELETE FROM issues");
		long low = createIssue("점검건", "LOW");
		long urgent = createIssue("긴급건", "URGENT");
		long others = createIssue("타인건", "URGENT");
		jdbc.update("UPDATE issues SET assigned_to = ? WHERE id IN (?, ?)", userId("field1"), low, urgent);
		jdbc.update("UPDATE issues SET assigned_to = ? WHERE id = ?", userId("field2"), others);
	}

	@Test
	void fieldSeesOwnAssignedIssuesSeverityOrdered() throws Exception {
		mockMvc.perform(get("/api/me/assigned").header("Authorization", "Bearer " + token("field1")))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.length()").value(2))
				// severity order: URGENT before LOW
				.andExpect(jsonPath("$[0].priority").value("URGENT"))
				.andExpect(jsonPath("$[0].title").value("긴급건"))
				.andExpect(jsonPath("$[1].priority").value("LOW"))
				.andExpect(jsonPath("$[0].categoryL1Name").isNotEmpty());
	}

	@Test
	void otherFieldsIssuesAreExcluded() throws Exception {
		// field1 must not see field2's issue
		mockMvc.perform(get("/api/me/assigned").header("Authorization", "Bearer " + token("field1")))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$[?(@.title == '타인건')]").isEmpty());
		// field2 sees only their one
		mockMvc.perform(get("/api/me/assigned").header("Authorization", "Bearer " + token("field2")))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.length()").value(1))
				.andExpect(jsonPath("$[0].title").value("타인건"));
	}

	@Test
	void nonFieldIsForbidden() throws Exception {
		mockMvc.perform(get("/api/me/assigned").header("Authorization", "Bearer " + token("agent1")))
				.andExpect(status().isForbidden());
	}

	@Test
	void unauthenticatedIsRejected() throws Exception {
		mockMvc.perform(get("/api/me/assigned")).andExpect(status().isUnauthorized());
	}
}
