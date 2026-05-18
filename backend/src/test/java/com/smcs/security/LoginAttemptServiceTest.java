package com.smcs.security;

import static org.assertj.core.api.Assertions.assertThat;

import com.smcs.user.UserRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.PostgreSQLContainer;

@SpringBootTest
@ActiveProfiles("local")
class LoginAttemptServiceTest {

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
	LoginAttemptService service;

	@Autowired
	JdbcTemplate jdbc;

	@Autowired
	UserRepository userRepository;

	@BeforeEach
	void clean() {
		jdbc.update("DELETE FROM login_attempt");
	}

	@Test
	void fourFailuresDoNotLockButFifthDoes() {
		String user = "agent1";
		String ip = "10.0.0.1";
		for (int i = 0; i < 4; i++) {
			service.recordFailure(user, ip);
		}
		assertThat(service.isLocked(user, ip)).isFalse();

		service.recordFailure(user, ip);
		assertThat(service.isLocked(user, ip)).isTrue();
	}

	@Test
	void ipBasedLockoutBlocksDifferentUsername() {
		String ip = "10.0.0.2";
		for (int i = 0; i < 5; i++) {
			service.recordFailure("attacker" + i, ip);
		}
		assertThat(service.isLocked("victim", ip)).isTrue();
	}

	@Test
	void successDoesNotTriggerLockout() {
		String user = "agent1";
		String ip = "10.0.0.3";
		for (int i = 0; i < 10; i++) {
			service.recordSuccess(user, ip);
		}
		assertThat(service.isLocked(user, ip)).isFalse();
	}
}
