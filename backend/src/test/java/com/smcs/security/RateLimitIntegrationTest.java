package com.smcs.security;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.smcs.user.User;
import com.smcs.user.UserRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.security.web.FilterChainProxy;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.context.WebApplicationContext;
import org.testcontainers.containers.PostgreSQLContainer;

@SpringBootTest
@ActiveProfiles("local")
@TestPropertySource(properties = "smcs.rate-limit.per-user-per-minute=5")
class RateLimitIntegrationTest {

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
	WebApplicationContext context;

	@Autowired
	FilterChainProxy springSecurityFilterChain;

	@Autowired
	JwtService jwtService;

	@Autowired
	UserRepository userRepository;

	MockMvc mockMvc;
	String authHeader;

	@BeforeEach
	void setup() {
		mockMvc = MockMvcBuilders.webAppContextSetup(context)
				.addFilters(springSecurityFilterChain)
				.build();
		User user = userRepository.findByUsername("agent1").orElseThrow();
		authHeader = "Bearer " + jwtService.generate(user.getId(), user.getRole()).token();
	}

	@Test
	void sixthRequestExceedsLimitAndReturns429() throws Exception {
		for (int i = 0; i < 5; i++) {
			mockMvc.perform(get("/api/me").header("Authorization", authHeader))
					.andExpect(status().isOk());
		}
		mockMvc.perform(get("/api/me").header("Authorization", authHeader))
				.andExpect(status().isTooManyRequests())
				.andExpect(header().exists("Retry-After"))
				.andExpect(jsonPath("$.code").value("RATE_LIMIT_EXCEEDED"));
	}

	@Test
	void healthEndpointIsExemptFromRateLimit() throws Exception {
		for (int i = 0; i < 10; i++) {
			mockMvc.perform(get("/api/health"))
					.andExpect(status().isOk());
		}
	}
}
