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
}
