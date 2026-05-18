package com.smcs.migration;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.PostgreSQLContainer;

@SpringBootTest
class FlywayMigrationIntegrationTest {

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

	@Test
	void allSevenTablesExist() {
		List<String> tables = jdbc.queryForList(
				"SELECT table_name FROM information_schema.tables "
						+ "WHERE table_schema = 'public' AND table_type = 'BASE TABLE'",
				String.class);
		assertThat(tables).contains(
				"categories", "users", "issues",
				"comments", "attachments", "issue_events", "notifications");
	}

	@Test
	void coreIndexesExist() {
		List<String> indexes = jdbc.queryForList(
				"SELECT indexname FROM pg_indexes WHERE schemaname = 'public'",
				String.class);
		assertThat(indexes).contains(
				"idx_issues_priority_created_at",
				"idx_notifications_recipient_read");
	}

	@Test
	void categoriesSeedHasTenRows() {
		Integer total = jdbc.queryForObject("SELECT COUNT(*) FROM categories", Integer.class);
		assertThat(total).isEqualTo(10);
	}

	@Test
	void categoriesSeedDistributionByLevel() {
		Integer l1 = jdbc.queryForObject("SELECT COUNT(*) FROM categories WHERE level = 1", Integer.class);
		Integer l2 = jdbc.queryForObject("SELECT COUNT(*) FROM categories WHERE level = 2", Integer.class);
		Integer l3 = jdbc.queryForObject("SELECT COUNT(*) FROM categories WHERE level = 3", Integer.class);
		assertThat(l1).isEqualTo(3);
		assertThat(l2).isEqualTo(4);
		assertThat(l3).isEqualTo(3);
	}

	@Test
	void usersTableEmptyWithoutLocalProfile() {
		Integer userCount = jdbc.queryForObject("SELECT COUNT(*) FROM users", Integer.class);
		assertThat(userCount).isZero();
	}

	@Test
	void slaPoliciesTableDoesNotExist() {
		List<String> tables = jdbc.queryForList(
				"SELECT table_name FROM information_schema.tables "
						+ "WHERE table_schema = 'public' AND table_name ILIKE '%sla%'",
				String.class);
		assertThat(tables).isEmpty();
	}
}
