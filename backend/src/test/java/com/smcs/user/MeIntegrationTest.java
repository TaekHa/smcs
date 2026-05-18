package com.smcs.user;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.smcs.security.JwtService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.testcontainers.containers.PostgreSQLContainer;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("local")
class MeIntegrationTest {

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

	@Test
	void unauthenticatedRequestReturns401() throws Exception {
		mockMvc.perform(get("/api/me"))
				.andExpect(status().isUnauthorized())
				.andExpect(jsonPath("$.code").isNotEmpty())
				.andExpect(jsonPath("$.traceId").isNotEmpty());
	}

	@Test
	void invalidTokenReturns401() throws Exception {
		mockMvc.perform(get("/api/me").header("Authorization", "Bearer garbage.token.here"))
				.andExpect(status().isUnauthorized());
	}

	@Test
	void validTokenReturnsUserSummary() throws Exception {
		User agent = userRepository.findByUsername("agent1").orElseThrow();
		JwtService.TokenIssued issued = jwtService.generate(agent.getId(), agent.getRole());

		mockMvc.perform(get("/api/me").header("Authorization", "Bearer " + issued.token()))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.username").value("agent1"))
				.andExpect(jsonPath("$.role").value("AGENT"));
	}
}
