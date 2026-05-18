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
class IssueListIntegrationTest {

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

	private long categoryId(int level) {
		return jdbc.queryForObject(
				"SELECT id FROM categories WHERE level = ? ORDER BY sort_order LIMIT 1", Long.class, level);
	}

	private void createIssue(String title, String priority, String phone, String callerName) throws Exception {
		String body = """
				{"title":"%s","callerName":"%s","callerPhone":"%s",
				 "categoryL1Id":%d,"categoryL2Id":%d,"categoryL3Id":%d,
				 "priority":"%s","description":"본문 %s"}"""
				.formatted(title, callerName, phone, categoryId(1), categoryId(2), categoryId(3), priority, title);
		mockMvc.perform(post("/api/issues")
				.header("Authorization", "Bearer " + token("agent1"))
				.contentType(MediaType.APPLICATION_JSON).content(body))
				.andExpect(status().isCreated());
	}

	@BeforeEach
	void seed() throws Exception {
		jdbc.update("DELETE FROM issue_events");
		jdbc.update("DELETE FROM issues");
		createIssue("엘리베이터 긴급", "URGENT", "010-9999-8888", "홍길동");
		createIssue("정기 점검 요청", "LOW", "010-1111-2222", "김특이름홍보");
	}

	@Test
	void agentListsWithPageStructureAndDefaultSeverityOrder() throws Exception {
		mockMvc.perform(get("/api/issues").header("Authorization", "Bearer " + token("agent1")))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.size").value(50))
				.andExpect(jsonPath("$.totalElements").value(2))
				// default order = priority severity (URGENT before LOW)
				.andExpect(jsonPath("$.content[0].priority").value("URGENT"))
				.andExpect(jsonPath("$.content[1].priority").value("LOW"))
				.andExpect(jsonPath("$.content[0].categoryL1Name").isNotEmpty())
				// caller PII never present in list rows
				.andExpect(jsonPath("$.content[0].callerName").doesNotExist());
	}

	@Test
	void statusFilterNarrowsResults() throws Exception {
		mockMvc.perform(get("/api/issues?status=NEW").header("Authorization", "Bearer " + token("agent1")))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.totalElements").value(2));
		mockMvc.perform(get("/api/issues?status=DONE").header("Authorization", "Bearer " + token("agent1")))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.totalElements").value(0));
	}

	@Test
	void titleSearchMatchesPartial() throws Exception {
		mockMvc.perform(get("/api/issues?q=엘리베이터").header("Authorization", "Bearer " + token("agent1")))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.totalElements").value(1))
				.andExpect(jsonPath("$.content[0].title").value("엘리베이터 긴급"));
	}

	@Test
	void phoneSearchIsHmacExactMatch() throws Exception {
		mockMvc.perform(get("/api/issues?q=010-9999-8888").header("Authorization", "Bearer " + token("agent1")))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.totalElements").value(1))
				.andExpect(jsonPath("$.content[0].title").value("엘리베이터 긴급"));
	}

	@Test
	void callerNameIsNotSearchable() throws Exception {
		// '김특이름홍보' is a caller name (encrypted column) — must NOT be found by search
		mockMvc.perform(get("/api/issues?q=김특이름홍보").header("Authorization", "Bearer " + token("agent1")))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.totalElements").value(0));
	}

	@Test
	void fieldRoleIsForbidden() throws Exception {
		mockMvc.perform(get("/api/issues").header("Authorization", "Bearer " + token("field1")))
				.andExpect(status().isForbidden());
	}

	@Test
	void unauthenticatedIsRejected() throws Exception {
		mockMvc.perform(get("/api/issues")).andExpect(status().isUnauthorized());
	}

	@Test
	void usersEndpointForAssigneeFilter() throws Exception {
		mockMvc.perform(get("/api/users").header("Authorization", "Bearer " + token("agent1")))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.length()").value(org.hamcrest.Matchers.greaterThanOrEqualTo(8)))
				.andExpect(jsonPath("$[0].displayName").isNotEmpty());
		mockMvc.perform(get("/api/users").header("Authorization", "Bearer " + token("field1")))
				.andExpect(status().isForbidden());
	}
}
