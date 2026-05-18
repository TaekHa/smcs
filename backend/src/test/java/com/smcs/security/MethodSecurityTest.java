package com.smcs.security;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.smcs.user.User;
import com.smcs.user.UserRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.web.FilterChainProxy;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.context.WebApplicationContext;
import org.testcontainers.containers.PostgreSQLContainer;

@SpringBootTest
@ActiveProfiles("local")
@Import(MethodSecurityTest.AdminOnlyTestController.class)
class MethodSecurityTest {

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

	@BeforeEach
	void setup() {
		mockMvc = MockMvcBuilders.webAppContextSetup(context)
				.addFilters(springSecurityFilterChain)
				.build();
	}

	@Test
	void agentCannotAccessAdminOnlyEndpoint() throws Exception {
		User agent = userRepository.findByUsername("agent1").orElseThrow();
		String token = jwtService.generate(agent.getId(), agent.getRole()).token();
		mockMvc.perform(get("/test/admin-only").header("Authorization", "Bearer " + token))
				.andExpect(status().isForbidden());
	}

	@Test
	void adminCanAccessAdminOnlyEndpoint() throws Exception {
		User admin = userRepository.findByUsername("admin1").orElseThrow();
		String token = jwtService.generate(admin.getId(), admin.getRole()).token();
		mockMvc.perform(get("/test/admin-only").header("Authorization", "Bearer " + token))
				.andExpect(status().isOk());
	}

	@RestController
	static class AdminOnlyTestController {

		@GetMapping("/test/admin-only")
		@PreAuthorize("hasRole('ADMIN')")
		public String adminOnly() {
			return "ok";
		}
	}
}
