package com.smcs.category;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.smcs.security.JwtService;
import com.smcs.user.User;
import com.smcs.user.UserRepository;
import org.hamcrest.Matchers;
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
class AdminCategoryIntegrationTest {

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

	@Test
	void adminListsLevel1IncludingInactiveOrderedBySortOrder() throws Exception {
		jdbc.update("UPDATE categories SET active = TRUE WHERE level = 1"); // reset cross-test residue

		mockMvc.perform(get("/api/admin/categories?level=1")
				.header("Authorization", "Bearer " + token("admin1")))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.length()").value(Matchers.greaterThanOrEqualTo(3)))
				.andExpect(jsonPath("$[0].name").value("아파트먼트v1"))
				.andExpect(jsonPath("$[0].sortOrder").value(1))
				.andExpect(jsonPath("$[0].active").value(true))
				.andExpect(jsonPath("$[0].keywords").isArray());
	}

	@Test
	void adminCreatesNewCategoryAt201WithSortOrderMaxPlusOne() throws Exception {
		Integer maxBefore = jdbc.queryForObject(
				"SELECT COALESCE(MAX(sort_order), 0) FROM categories WHERE level = 2",
				Integer.class);

		mockMvc.perform(post("/api/admin/categories")
				.header("Authorization", "Bearer " + token("admin1"))
				.contentType(MediaType.APPLICATION_JSON)
				.content("""
						{"level":2,"name":"테스트분류","keywords":["인터넷","wifi"]}
						"""))
				.andExpect(status().isCreated())
				.andExpect(jsonPath("$.id").isNumber())
				.andExpect(jsonPath("$.level").value(2))
				.andExpect(jsonPath("$.name").value("테스트분류"))
				.andExpect(jsonPath("$.sortOrder").value(maxBefore + 1))
				.andExpect(jsonPath("$.active").value(true))
				.andExpect(jsonPath("$.keywords").isArray())
				.andExpect(jsonPath("$.keywords[0]").value("인터넷"))
				.andExpect(jsonPath("$.keywords[1]").value("wifi"));

		// cleanup so cross-test state stays predictable
		jdbc.update("DELETE FROM categories WHERE name = '테스트분류' AND level = 2");
	}

	@Test
	void adminUpdatesExistingCategoryReturns200AndPersistsKeywordsJsonbRoundtrip() throws Exception {
		Long id = jdbc.queryForObject(
				"SELECT id FROM categories WHERE level = 3 AND name = '기기미동작' LIMIT 1", Long.class);

		mockMvc.perform(post("/api/admin/categories")
				.header("Authorization", "Bearer " + token("admin1"))
				.contentType(MediaType.APPLICATION_JSON)
				.content("""
						{"id":%d,"level":3,"name":"기기미동작-개정","keywords":["네트워크","끊김","wifi 끊김"],"sortOrder":1,"active":true}
						""".formatted(id)))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.name").value("기기미동작-개정"))
				.andExpect(jsonPath("$.keywords.length()").value(3))
				.andExpect(jsonPath("$.keywords[2]").value("wifi 끊김"));

		// reset to seed values so other tests aren't affected
		jdbc.update("UPDATE categories SET name = '기기미동작', keywords = '[]'::jsonb WHERE id = ?", id);
	}

	@Test
	void adminUpdateUnknownIdReturns404CategoryNotFound() throws Exception {
		mockMvc.perform(post("/api/admin/categories")
				.header("Authorization", "Bearer " + token("admin1"))
				.contentType(MediaType.APPLICATION_JSON)
				.content("""
						{"id":999999,"level":1,"name":"x","keywords":[],"sortOrder":1,"active":true}
						"""))
				.andExpect(status().isNotFound())
				.andExpect(jsonPath("$.code").value("CATEGORY_NOT_FOUND"));
	}

	@Test
	void invalidLevelReturns400() throws Exception {
		mockMvc.perform(post("/api/admin/categories")
				.header("Authorization", "Bearer " + token("admin1"))
				.contentType(MediaType.APPLICATION_JSON)
				.content("""
						{"level":4,"name":"n","keywords":[]}
						"""))
				.andExpect(status().isBadRequest())
				.andExpect(jsonPath("$.code").value("VALIDATION_FAILED"));
	}

	@Test
	void blankNameReturns400() throws Exception {
		mockMvc.perform(post("/api/admin/categories")
				.header("Authorization", "Bearer " + token("admin1"))
				.contentType(MediaType.APPLICATION_JSON)
				.content("""
						{"level":1,"name":"  ","keywords":[]}
						"""))
				.andExpect(status().isBadRequest())
				.andExpect(jsonPath("$.code").value("VALIDATION_FAILED"));
	}

	@Test
	void deactivatingCategoryHidesItFromPublicLookupSoT() throws Exception {
		Long id = jdbc.queryForObject(
				"SELECT id FROM categories WHERE level = 1 AND name = '아파트먼트v1' LIMIT 1", Long.class);

		// deactivate via admin upsert
		mockMvc.perform(post("/api/admin/categories")
				.header("Authorization", "Bearer " + token("admin1"))
				.contentType(MediaType.APPLICATION_JSON)
				.content("""
						{"id":%d,"level":1,"name":"아파트먼트v1","keywords":[],"sortOrder":1,"active":false}
						""".formatted(id)))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.active").value(false));

		// admin still sees it
		mockMvc.perform(get("/api/admin/categories?level=1")
				.header("Authorization", "Bearer " + token("admin1")))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$[?(@.name == '아파트먼트v1')].active").value(Matchers.hasItem(false)));

		// public form dropdown must NOT see it (AC4 SoT regression — Story 2.1 lookup)
		mockMvc.perform(get("/api/categories?level=1")
				.header("Authorization", "Bearer " + token("agent1")))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$[?(@.name == '아파트먼트v1')]").isEmpty());

		// reset for cross-test isolation
		jdbc.update("UPDATE categories SET active = TRUE WHERE id = ?", id);
	}

	@Test
	void agentForbidden() throws Exception {
		mockMvc.perform(get("/api/admin/categories?level=1")
				.header("Authorization", "Bearer " + token("agent1")))
				.andExpect(status().isForbidden());
		mockMvc.perform(post("/api/admin/categories")
				.header("Authorization", "Bearer " + token("agent1"))
				.contentType(MediaType.APPLICATION_JSON)
				.content("{\"level\":1,\"name\":\"x\",\"keywords\":[]}"))
				.andExpect(status().isForbidden());
	}

	@Test
	void fieldForbidden() throws Exception {
		mockMvc.perform(get("/api/admin/categories?level=1")
				.header("Authorization", "Bearer " + token("field1")))
				.andExpect(status().isForbidden());
	}

	@Test
	void unauthenticatedRejected() throws Exception {
		mockMvc.perform(get("/api/admin/categories?level=1"))
				.andExpect(status().isUnauthorized());
		mockMvc.perform(post("/api/admin/categories")
				.contentType(MediaType.APPLICATION_JSON)
				.content("{\"level\":1,\"name\":\"x\"}"))
				.andExpect(status().isUnauthorized());
	}
}
