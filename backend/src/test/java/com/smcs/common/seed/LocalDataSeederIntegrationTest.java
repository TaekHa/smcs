package com.smcs.common.seed;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.PostgreSQLContainer;

@SpringBootTest
@ActiveProfiles("local")
class LocalDataSeederIntegrationTest {

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
	JdbcTemplate jdbc;

	@Autowired
	LocalDataSeeder seeder;

	@Test
	void seedsEightUsers() {
		Integer count = jdbc.queryForObject("SELECT COUNT(*) FROM users", Integer.class);
		assertThat(count).isEqualTo(8);
	}

	@Test
	void roleDistributionMatchesSpec() {
		List<Map<String, Object>> rows = jdbc.queryForList(
				"SELECT role, COUNT(*) AS c FROM users GROUP BY role");
		Map<String, Long> byRole = rows.stream().collect(Collectors.toMap(
				r -> (String) r.get("role"),
				r -> ((Number) r.get("c")).longValue()));
		assertThat(byRole).containsEntry("AGENT", 3L);
		assertThat(byRole).containsEntry("FIELD", 4L);
		assertThat(byRole).containsEntry("ADMIN", 1L);
	}

	@Test
	void seedsTwentyIssues() {
		Integer count = jdbc.queryForObject("SELECT COUNT(*) FROM issues", Integer.class);
		assertThat(count).isEqualTo(20);
	}

	@Test
	void allPasswordsAreBcryptHashesOfDev1234() {
		List<String> hashes = jdbc.queryForList("SELECT password_hash FROM users", String.class);
		BCryptPasswordEncoder encoder = new BCryptPasswordEncoder();
		assertThat(hashes).hasSize(8);
		for (String hash : hashes) {
			assertThat(hash).matches("^\\$2[aby]\\$.+");
			assertThat(encoder.matches("dev1234", hash)).isTrue();
		}
	}

	@Test
	void seederIsIdempotentOnSecondRun() {
		Integer usersBefore = jdbc.queryForObject("SELECT COUNT(*) FROM users", Integer.class);
		Integer issuesBefore = jdbc.queryForObject("SELECT COUNT(*) FROM issues", Integer.class);

		seeder.run(null);

		Integer usersAfter = jdbc.queryForObject("SELECT COUNT(*) FROM users", Integer.class);
		Integer issuesAfter = jdbc.queryForObject("SELECT COUNT(*) FROM issues", Integer.class);
		assertThat(usersAfter).isEqualTo(usersBefore);
		assertThat(issuesAfter).isEqualTo(issuesBefore);
	}
}
