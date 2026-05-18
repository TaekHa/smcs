package com.smcs.auth;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.sql.Timestamp;
import java.time.Instant;
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
import org.springframework.test.web.servlet.MvcResult;
import org.testcontainers.containers.PostgreSQLContainer;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("local")
class AuthIntegrationTest {

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
	JdbcTemplate jdbc;

	@BeforeEach
	void setup() {
		jdbc.update("DELETE FROM login_attempt");
	}

	@Test
	void loginSuccessReturnsJwtAndUserSummary() throws Exception {
		mockMvc.perform(post("/api/auth/login")
				.contentType(MediaType.APPLICATION_JSON)
				.content("{\"username\":\"agent1\",\"password\":\"dev1234\"}"))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.token").isNotEmpty())
				.andExpect(jsonPath("$.expiresInSeconds").value(28800))
				.andExpect(jsonPath("$.user.username").value("agent1"))
				.andExpect(jsonPath("$.user.role").value("AGENT"));
	}

	@Test
	void loginWithWrongPasswordReturns401WithStandardError() throws Exception {
		mockMvc.perform(post("/api/auth/login")
				.contentType(MediaType.APPLICATION_JSON)
				.content("{\"username\":\"agent1\",\"password\":\"wrong\"}"))
				.andExpect(status().isUnauthorized())
				.andExpect(jsonPath("$.code").value("INVALID_CREDENTIALS"))
				.andExpect(jsonPath("$.message").isNotEmpty())
				.andExpect(jsonPath("$.traceId").isNotEmpty());
	}

	@Test
	void fiveFailuresTriggerLockoutOnSixthAttempt() throws Exception {
		String body = "{\"username\":\"agent2\",\"password\":\"wrong\"}";
		for (int i = 0; i < 5; i++) {
			mockMvc.perform(post("/api/auth/login")
					.contentType(MediaType.APPLICATION_JSON).content(body))
					.andExpect(status().isUnauthorized());
		}
		mockMvc.perform(post("/api/auth/login")
				.contentType(MediaType.APPLICATION_JSON).content(body))
				.andExpect(status().isLocked())
				.andExpect(jsonPath("$.code").value("ACCOUNT_LOCKED"));
	}

	@Test
	void agedFailuresOutsideWindowDoNotCount() throws Exception {
		Timestamp old = Timestamp.from(Instant.now().minusSeconds(20 * 60));
		for (int i = 0; i < 5; i++) {
			jdbc.update(
					"INSERT INTO login_attempt (username, ip_address, attempted_at, success) VALUES (?, ?, ?, FALSE)",
					"agent3", "127.0.0.1", old);
		}
		mockMvc.perform(post("/api/auth/login")
				.contentType(MediaType.APPLICATION_JSON)
				.content("{\"username\":\"agent3\",\"password\":\"dev1234\"}"))
				.andExpect(status().isOk());
	}

	@Test
	void successfulLoginRecordsSuccessRow() throws Exception {
		MvcResult result = mockMvc.perform(post("/api/auth/login")
				.contentType(MediaType.APPLICATION_JSON)
				.content("{\"username\":\"agent1\",\"password\":\"dev1234\"}"))
				.andExpect(status().isOk())
				.andReturn();
		assert result.getResponse().getStatus() == 200;

		Integer successCount = jdbc.queryForObject(
				"SELECT COUNT(*) FROM login_attempt WHERE username = ? AND success = TRUE",
				Integer.class, "agent1");
		assert successCount != null && successCount >= 1;
	}
}
