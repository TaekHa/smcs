package com.smcs.issue.export;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.smcs.security.JwtService;
import com.smcs.user.User;
import com.smcs.user.UserRepository;
import java.nio.charset.StandardCharsets;
import org.hamcrest.Matchers;
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
class IssueExportIntegrationTest {

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

	private long categoryId(int level) {
		return jdbc.queryForObject(
				"SELECT id FROM categories WHERE level = ? ORDER BY sort_order LIMIT 1", Long.class, level);
	}

	private void createIssue(String title, String priority, String phone, String callerName) throws Exception {
		String body = """
				{"title":"%s","callerName":"%s","callerPhone":"%s",
				 "categoryL1Id":%d,"categoryL2Id":%d,"categoryL3Id":%d,
				 "priority":"%s","description":"본문 %s"}"""
				.formatted(title, callerName, phone, categoryId(1), categoryId(2), categoryId(3), priority, title);
		mockMvc.perform(post("/api/issues")
				.header("Authorization", "Bearer " + token("agent1"))
				.contentType(MediaType.APPLICATION_JSON).content(body))
				.andExpect(status().isCreated());
	}

	@BeforeEach
	void seed() throws Exception {
		jdbc.update("DELETE FROM notifications");
		jdbc.update("DELETE FROM attachments");
		jdbc.update("DELETE FROM issue_events");
		jdbc.update("DELETE FROM issues");
		// First title has a comma so we can verify RFC 4180 escaping on a real round-trip.
		createIssue("타이틀,쉼표", "URGENT", "010-1111-2222", "홍길동");
		createIssue("일반 제목", "LOW", "010-3333-4444", "김철수");
	}

	@Test
	void adminExportsCsvWithBomAndKoreanHeaderAndSeverityOrder() throws Exception {
		MvcResult result = mockMvc.perform(get("/api/issues/export?format=csv")
				.header("Authorization", "Bearer " + token("admin1")))
				.andExpect(status().isOk())
				.andExpect(header -> {
					String ct = header.getResponse().getContentType();
					assertThat(ct).isNotNull().startsWith("text/csv");
					assertThat(ct.toLowerCase()).contains("utf-8");
				})
				.andReturn();

		String disposition = result.getResponse().getHeader("Content-Disposition");
		assertThat(disposition).isNotNull().startsWith("attachment; filename=\"issues-").endsWith(".csv\"");

		byte[] bytes = result.getResponse().getContentAsByteArray();
		assertThat(bytes[0]).isEqualTo((byte) 0xEF);
		assertThat(bytes[1]).isEqualTo((byte) 0xBB);
		assertThat(bytes[2]).isEqualTo((byte) 0xBF);

		String body = new String(bytes, 3, bytes.length - 3, StandardCharsets.UTF_8);
		assertThat(body).startsWith("ID,제목,카테고리,우선순위,상태,담당자,접수일,처리일,처리 시간(분)\r\n");

		String[] lines = body.split("\r\n");
		assertThat(lines).hasSize(3); // header + 2 data rows
		// Default severity order (URGENT before LOW) → "타이틀,쉼표" first; comma must be quoted.
		assertThat(lines[1]).contains("\"타이틀,쉼표\"");
		assertThat(lines[1]).contains(",URGENT,");
		assertThat(lines[2]).contains("일반 제목").contains(",LOW,");

		// PII columns not present by default.
		assertThat(body).doesNotContain("홍길동");
		assertThat(body).doesNotContain("010-1111-2222");
	}

	@Test
	void adminExportsCsvWithPiiColumnsAtTailWhenIncludePiiTrue() throws Exception {
		byte[] bytes = mockMvc.perform(get("/api/issues/export?format=csv&includePii=true")
				.header("Authorization", "Bearer " + token("admin1")))
				.andExpect(status().isOk())
				.andReturn().getResponse().getContentAsByteArray();

		String body = new String(bytes, 3, bytes.length - 3, StandardCharsets.UTF_8);
		assertThat(body).startsWith(
				"ID,제목,카테고리,우선순위,상태,담당자,접수일,처리일,처리 시간(분),발신자명,발신자전화번호\r\n");
		// Encrypted columns decrypted on read.
		assertThat(body).contains("홍길동");
		assertThat(body).contains("010-1111-2222");
		assertThat(body).contains("김철수");
	}

	@Test
	void statusFilterNarrowsExportToHeaderOnly() throws Exception {
		byte[] bytes = mockMvc.perform(get("/api/issues/export?format=csv&status=DONE")
				.header("Authorization", "Bearer " + token("admin1")))
				.andExpect(status().isOk())
				.andReturn().getResponse().getContentAsByteArray();
		String body = new String(bytes, 3, bytes.length - 3, StandardCharsets.UTF_8);
		// header only (CRLF-terminated) → split yields 1 element
		assertThat(body.split("\r\n")).hasSize(1);
	}

	@Test
	void unsupportedFormatReturns400() throws Exception {
		mockMvc.perform(get("/api/issues/export?format=xlsx")
				.header("Authorization", "Bearer " + token("admin1")))
				.andExpect(status().isBadRequest())
				.andExpect(jsonPath("$.code").value("UNSUPPORTED_FORMAT"));
	}

	@Test
	void agentForbidden() throws Exception {
		mockMvc.perform(get("/api/issues/export?format=csv")
				.header("Authorization", "Bearer " + token("agent1")))
				.andExpect(status().isForbidden());
	}

	@Test
	void fieldForbidden() throws Exception {
		mockMvc.perform(get("/api/issues/export?format=csv")
				.header("Authorization", "Bearer " + token("field1")))
				.andExpect(status().isForbidden());
	}

	@Test
	void unauthenticatedRejected() throws Exception {
		mockMvc.perform(get("/api/issues/export?format=csv"))
				.andExpect(status().isUnauthorized());
	}

	@Test
	void exceedingMaxRowsReturns400ExportTooManyRows() throws Exception {
		// Bulk-insert 5000 rows directly so total > MAX_ROWS (5000). PII columns are nullable.
		long l1 = categoryId(1);
		long l2 = categoryId(2);
		long l3 = categoryId(3);
		long creator = userRepository.findByUsername("agent1").orElseThrow().getId();
		jdbc.update("""
				INSERT INTO issues (title, description, category_l1_id, category_l2_id, category_l3_id,
				                    priority, status, created_by, created_at, updated_at)
				SELECT 'bulk-' || g, 'desc', ?, ?, ?, 'LOW', 'NEW', ?, now(), now()
				FROM generate_series(1, ?) AS g
				""", l1, l2, l3, creator, 5000);

		mockMvc.perform(get("/api/issues/export?format=csv")
				.header("Authorization", "Bearer " + token("admin1")))
				.andExpect(status().isBadRequest())
				.andExpect(jsonPath("$.code").value("EXPORT_TOO_MANY_ROWS"))
				.andExpect(jsonPath("$.message").value(Matchers.containsString("필터를 좁혀주세요")));
	}
}
