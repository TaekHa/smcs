package com.smcs.user;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.smcs.security.JwtService;
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
class AdminUserIntegrationTest {

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

	private void cleanupCreatedUsers() {
		jdbc.update("DELETE FROM users WHERE username LIKE 'test4-4-%'");
	}

	@Test
	void adminListsAllUsersIncludingInactive() throws Exception {
		mockMvc.perform(get("/api/admin/users")
				.header("Authorization", "Bearer " + token("admin1")))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.length()").value(Matchers.greaterThanOrEqualTo(8)))
				.andExpect(jsonPath("$[0].username").isNotEmpty())
				// passwordHash MUST NEVER appear in admin responses (§9.1)
				.andExpect(jsonPath("$[0].passwordHash").doesNotExist());
	}

	@Test
	void adminCreatesUserAt201WithOneTimeTemporaryPasswordAndBcryptHashInDb() throws Exception {
		try {
			mockMvc.perform(post("/api/admin/users")
					.header("Authorization", "Bearer " + token("admin1"))
					.contentType(MediaType.APPLICATION_JSON)
					.content("""
							{"username":"test4-4-create","displayName":"테스트생성","role":"AGENT","phone":"010-9999-8888"}
							"""))
					.andExpect(status().isCreated())
					.andExpect(jsonPath("$.user.username").value("test4-4-create"))
					.andExpect(jsonPath("$.user.role").value("AGENT"))
					.andExpect(jsonPath("$.user.active").value(true))
					// password NEVER appears in the nested admin DTO
					.andExpect(jsonPath("$.user.passwordHash").doesNotExist())
					// temporary password is 12 alphanumeric chars (AC2)
					.andExpect(jsonPath("$.temporaryPassword").value(Matchers.matchesRegex("^[A-Za-z0-9]{12}$")));

			// DB inspection — the password_hash column holds a BCrypt hash, not the plaintext.
			String hash = jdbc.queryForObject(
					"SELECT password_hash FROM users WHERE username = ?", String.class, "test4-4-create");
			org.assertj.core.api.Assertions.assertThat(hash).startsWith("$2a$");
		} finally {
			cleanupCreatedUsers();
		}
	}

	@Test
	void duplicateUsernameReturns400() throws Exception {
		mockMvc.perform(post("/api/admin/users")
				.header("Authorization", "Bearer " + token("admin1"))
				.contentType(MediaType.APPLICATION_JSON)
				.content("""
						{"username":"admin1","displayName":"중복","role":"ADMIN"}
						"""))
				.andExpect(status().isBadRequest())
				.andExpect(jsonPath("$.code").value("DUPLICATE_USERNAME"));
	}

	@Test
	void blankUsernameReturns400() throws Exception {
		mockMvc.perform(post("/api/admin/users")
				.header("Authorization", "Bearer " + token("admin1"))
				.contentType(MediaType.APPLICATION_JSON)
				.content("""
						{"username":"  ","displayName":"x","role":"AGENT"}
						"""))
				.andExpect(status().isBadRequest())
				.andExpect(jsonPath("$.code").value("VALIDATION_FAILED"));
	}

	@Test
	void invalidRoleReturns400() throws Exception {
		mockMvc.perform(post("/api/admin/users")
				.header("Authorization", "Bearer " + token("admin1"))
				.contentType(MediaType.APPLICATION_JSON)
				.content("""
						{"username":"test4-4-bad","displayName":"x","role":"SUPERUSER"}
						"""))
				.andExpect(status().isBadRequest())
				.andExpect(jsonPath("$.code").value("VALIDATION_FAILED"));
	}

	@Test
	void adminUpdatesPartialAndPasswordNeverLeaks() throws Exception {
		Long agentId = userRepository.findByUsername("agent1").orElseThrow().getId();
		String originalDisplay = userRepository.findById(agentId).orElseThrow().getDisplayName();
		try {
			mockMvc.perform(post("/api/admin/users/" + agentId)
					.header("Authorization", "Bearer " + token("admin1"))
					.contentType(MediaType.APPLICATION_JSON)
					.content("""
							{"displayName":"수정-에이전트"}
							"""))
					.andExpect(status().isOk())
					.andExpect(jsonPath("$.username").value("agent1"))
					.andExpect(jsonPath("$.displayName").value("수정-에이전트"))
					.andExpect(jsonPath("$.role").value("AGENT"))
					.andExpect(jsonPath("$.passwordHash").doesNotExist())
					.andExpect(jsonPath("$.temporaryPassword").doesNotExist());
		} finally {
			jdbc.update("UPDATE users SET display_name = ? WHERE id = ?", originalDisplay, agentId);
		}
	}

	@Test
	void selfDeactivationReturns400AC5() throws Exception {
		Long adminId = userRepository.findByUsername("admin1").orElseThrow().getId();
		mockMvc.perform(post("/api/admin/users/" + adminId)
				.header("Authorization", "Bearer " + token("admin1"))
				.contentType(MediaType.APPLICATION_JSON)
				.content("""
						{"active":false}
						"""))
				.andExpect(status().isBadRequest())
				.andExpect(jsonPath("$.code").value("SELF_DEACTIVATION_FORBIDDEN"));
	}

	@Test
	void unknownUserIdUpdateReturns404() throws Exception {
		mockMvc.perform(post("/api/admin/users/9999999")
				.header("Authorization", "Bearer " + token("admin1"))
				.contentType(MediaType.APPLICATION_JSON)
				.content("""
						{"displayName":"x"}
						"""))
				.andExpect(status().isNotFound())
				.andExpect(jsonPath("$.code").value("USER_NOT_FOUND"));
	}

	@Test
	void agentForbiddenOnAdminEndpoints() throws Exception {
		mockMvc.perform(get("/api/admin/users")
				.header("Authorization", "Bearer " + token("agent1")))
				.andExpect(status().isForbidden());
		mockMvc.perform(post("/api/admin/users")
				.header("Authorization", "Bearer " + token("agent1"))
				.contentType(MediaType.APPLICATION_JSON)
				.content("{\"username\":\"x\",\"displayName\":\"x\",\"role\":\"AGENT\"}"))
				.andExpect(status().isForbidden());
	}

	@Test
	void unauthenticatedRejected() throws Exception {
		mockMvc.perform(get("/api/admin/users"))
				.andExpect(status().isUnauthorized());
		mockMvc.perform(post("/api/admin/users")
				.contentType(MediaType.APPLICATION_JSON)
				.content("{\"username\":\"x\",\"displayName\":\"x\",\"role\":\"AGENT\"}"))
				.andExpect(status().isUnauthorized());
	}

	@Test
	void deactivatedUserCannotLogin() throws Exception {
		Long agentId = userRepository.findByUsername("agent1").orElseThrow().getId();
		try {
			// deactivate agent1
			mockMvc.perform(post("/api/admin/users/" + agentId)
					.header("Authorization", "Bearer " + token("admin1"))
					.contentType(MediaType.APPLICATION_JSON)
					.content("{\"active\":false}"))
					.andExpect(status().isOk());

			// login attempt — SmcsUserDetailsService rejects inactive accounts (AC3 regression).
			mockMvc.perform(post("/api/auth/login")
					.contentType(MediaType.APPLICATION_JSON)
					.content("{\"username\":\"agent1\",\"password\":\"agent1!\"}"))
					.andExpect(status().is4xxClientError());
		} finally {
			// restore active for cross-test isolation
			jdbc.update("UPDATE users SET active = TRUE WHERE id = ?", agentId);
		}
	}
}
