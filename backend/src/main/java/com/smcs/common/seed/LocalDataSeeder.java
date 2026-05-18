package com.smcs.common.seed;

import java.sql.Timestamp;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.concurrent.ThreadLocalRandom;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.context.annotation.Profile;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;

@Component
@Profile("local")
public class LocalDataSeeder implements ApplicationRunner {

	private static final Logger log = LoggerFactory.getLogger(LocalDataSeeder.class);

	private static final String SEED_PASSWORD = "dev1234";

	private final JdbcTemplate jdbc;
	private final PasswordEncoder encoder;

	public LocalDataSeeder(JdbcTemplate jdbc, PasswordEncoder encoder) {
		this.jdbc = jdbc;
		this.encoder = encoder;
	}

	@Override
	public void run(ApplicationArguments args) {
		Integer existing = jdbc.queryForObject("SELECT COUNT(*) FROM users", Integer.class);
		if (existing != null && existing > 0) {
			log.info("LocalDataSeeder: users table already populated ({}), skipping seed.", existing);
			return;
		}

		seedUsers();
		List<Long> agentIds = jdbc.queryForList("SELECT id FROM users WHERE role = 'AGENT' ORDER BY id", Long.class);
		List<Long> fieldIds = jdbc.queryForList("SELECT id FROM users WHERE role = 'FIELD' ORDER BY id", Long.class);
		List<Long> l1Ids = jdbc.queryForList("SELECT id FROM categories WHERE level = 1 ORDER BY sort_order", Long.class);
		List<Long> l2Ids = jdbc.queryForList("SELECT id FROM categories WHERE level = 2 ORDER BY sort_order", Long.class);
		List<Long> l3Ids = jdbc.queryForList("SELECT id FROM categories WHERE level = 3 ORDER BY sort_order", Long.class);
		seedIssues(agentIds, fieldIds, l1Ids, l2Ids, l3Ids);

		log.info("Seeded 8 users + 20 issues (local profile)");
		log.warn("Seed credentials: agent1/agent2/agent3/field1..4/admin1, all password = '{}'. "
				+ "NEVER use in production. Real users go through Story 1.3 registration with PRD 6.9 policy.",
				SEED_PASSWORD);
	}

	private void seedUsers() {
		String hash = encoder.encode(SEED_PASSWORD);
		String sql = "INSERT INTO users (username, password_hash, display_name, role, phone, active) "
				+ "VALUES (?, ?, ?, ?, NULL, TRUE)";
		jdbc.update(sql, "agent1", hash, "김상담1", "AGENT");
		jdbc.update(sql, "agent2", hash, "김상담2", "AGENT");
		jdbc.update(sql, "agent3", hash, "김상담3", "AGENT");
		jdbc.update(sql, "field1", hash, "이현장1", "FIELD");
		jdbc.update(sql, "field2", hash, "이현장2", "FIELD");
		jdbc.update(sql, "field3", hash, "이현장3", "FIELD");
		jdbc.update(sql, "field4", hash, "이현장4", "FIELD");
		jdbc.update(sql, "admin1", hash, "관리자", "ADMIN");
	}

	private record IssueSpec(int count, String priority, String status, boolean assignField, Integer resolvedOffsetDays) {}

	private void seedIssues(List<Long> agentIds, List<Long> fieldIds,
			List<Long> l1Ids, List<Long> l2Ids, List<Long> l3Ids) {
		// Distribution from Story 1.2 Dev Notes: 20 issues total.
		List<IssueSpec> dist = List.of(
				new IssueSpec(2, "URGENT", "NEW",         false, null),
				new IssueSpec(1, "URGENT", "ASSIGNED",    true,  null),
				new IssueSpec(1, "URGENT", "IN_PROGRESS", true,  null),
				new IssueSpec(2, "HIGH",   "NEW",         false, null),
				new IssueSpec(2, "HIGH",   "ASSIGNED",    true,  null),
				new IssueSpec(1, "HIGH",   "DONE",        true,  1),
				new IssueSpec(3, "NORMAL", "NEW",         false, null),
				new IssueSpec(2, "NORMAL", "IN_PROGRESS", true,  null),
				new IssueSpec(2, "NORMAL", "DONE",        true,  2),
				new IssueSpec(1, "NORMAL", "VERIFIED",    true,  3),
				new IssueSpec(2, "LOW",    "NEW",         false, null),
				new IssueSpec(1, "LOW",    "IN_PROGRESS", true,  null)
		);

		String sql = "INSERT INTO issues ("
				+ "title, description, "
				+ "category_l1_id, category_l2_id, category_l3_id, "
				+ "priority, status, created_by, assigned_to, "
				+ "resolved_at, created_at, updated_at"
				+ ") VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)";

		Instant now = Instant.now();
		int issueNum = 1;
		int fieldCursor = 0;
		int agentCursor = 0;
		int l1Cursor = 0;
		int l2Cursor = 0;
		int l3Cursor = 0;

		for (IssueSpec spec : dist) {
			for (int i = 0; i < spec.count(); i++) {
				int createdDaysAgo = ThreadLocalRandom.current().nextInt(0, 8);
				Instant createdAt = now.minus(Duration.ofDays(createdDaysAgo));
				Timestamp resolvedAtTs = spec.resolvedOffsetDays() != null
						? Timestamp.from(createdAt.plus(Duration.ofDays(spec.resolvedOffsetDays())))
						: null;

				long l1 = l1Ids.get(l1Cursor++ % l1Ids.size());
				long l2 = l2Ids.get(l2Cursor++ % l2Ids.size());
				long l3 = l3Ids.get(l3Cursor++ % l3Ids.size());
				long createdBy = agentIds.get(agentCursor++ % agentIds.size());
				Long assignedTo = spec.assignField() ? fieldIds.get(fieldCursor++ % fieldIds.size()) : null;

				jdbc.update(sql,
						"샘플 이슈 #" + issueNum,
						"개발 시드 데이터 (Story 1.2)",
						l1, l2, l3,
						spec.priority(), spec.status(), createdBy, assignedTo,
						resolvedAtTs,
						Timestamp.from(createdAt),
						Timestamp.from(createdAt));
				issueNum++;
			}
		}
	}
}
