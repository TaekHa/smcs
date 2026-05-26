package com.smcs.category;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.smcs.security.JwtService;
import com.smcs.user.User;
import com.smcs.user.UserRepository;
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
class CategoryControllerIntegrationTest {

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

	@Test
	void authenticatedUserGetsL1Categories() throws Exception {
		mockMvc.perform(get("/api/categories?level=1")
				.header("Authorization", "Bearer " + token("agent1")))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.length()").value(3))
				.andExpect(jsonPath("$[0].level").value(1));
	}

	@Test
	void unauthenticatedIsRejected() throws Exception {
		mockMvc.perform(get("/api/categories?level=1"))
				.andExpect(status().isUnauthorized());
	}

	@Test
	void nonNumericLevelReturns400() throws Exception {
		mockMvc.perform(get("/api/categories?level=abc")
				.header("Authorization", "Bearer " + token("agent1")))
				.andExpect(status().isBadRequest())
				.andExpect(jsonPath("$.code").value("VALIDATION_FAILED"));
	}

	@Test
	void missingLevelReturns400() throws Exception {
		mockMvc.perform(get("/api/categories")
				.header("Authorization", "Bearer " + token("agent1")))
				.andExpect(status().isBadRequest())
				.andExpect(jsonPath("$.code").value("VALIDATION_FAILED"));
	}

	@Test
	void responseIncludesKeywordsArrayFedByAdminUpsert() throws Exception {
		// Story 4.5 admin upsert is the canonical write path; seed the keyword via SQL so this
		// test stands on its own without depending on AdminCategoryController being up.
		Long id = jdbc.queryForObject(
				"SELECT id FROM categories WHERE level = 1 AND name = 'voip/pbx' LIMIT 1", Long.class);
		jdbc.update("UPDATE categories SET keywords = '[\"VOIP\", \"전화\"]'::jsonb WHERE id = ?", id);

		try {
			mockMvc.perform(get("/api/categories?level=1")
					.header("Authorization", "Bearer " + token("agent1")))
					.andExpect(status().isOk())
					.andExpect(jsonPath("$[?(@.name == 'voip/pbx')].keywords[0]")
							.value(org.hamcrest.Matchers.hasItem("VOIP")))
					.andExpect(jsonPath("$[?(@.name == 'voip/pbx')].keywords[1]")
							.value(org.hamcrest.Matchers.hasItem("전화")))
					// Other rows still return keywords (empty array per V2 default) — not absent.
					.andExpect(jsonPath("$[?(@.name == '아파트먼트v1')].keywords")
							.value(org.hamcrest.Matchers.hasItem(org.hamcrest.Matchers.empty())));
		} finally {
			// reset for cross-test isolation
			jdbc.update("UPDATE categories SET keywords = '[]'::jsonb WHERE id = ?", id);
		}
	}

	@Test
	void adminUpsertImmediatelyReflectsInPublicLookup() throws Exception {
		Long id = jdbc.queryForObject(
				"SELECT id FROM categories WHERE level = 2 AND name = '입주민앱' LIMIT 1", Long.class);

		mockMvc.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders
				.post("/api/admin/categories")
				.header("Authorization", "Bearer " + token("admin1"))
				.contentType(MediaType.APPLICATION_JSON)
				.content("""
						{"id":%d,"level":2,"name":"입주민앱","keywords":["app","앱","민원"],"sortOrder":2,"active":true}
						""".formatted(id)))
				.andExpect(status().isOk());

		try {
			mockMvc.perform(get("/api/categories?level=2")
					.header("Authorization", "Bearer " + token("agent1")))
					.andExpect(status().isOk())
					.andExpect(jsonPath("$[?(@.name == '입주민앱')].keywords[*]")
							.value(org.hamcrest.Matchers.hasItems("app", "앱", "민원")));
		} finally {
			jdbc.update("UPDATE categories SET keywords = '[]'::jsonb WHERE id = ?", id);
		}
	}
}
