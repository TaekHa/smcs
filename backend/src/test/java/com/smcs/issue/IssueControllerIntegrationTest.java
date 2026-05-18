package com.smcs.issue;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.smcs.crypto.AesGcmCipher;
import com.smcs.crypto.HmacHasher;
import com.smcs.security.JwtService;
import com.smcs.user.User;
import com.smcs.user.UserRepository;
import java.nio.charset.StandardCharsets;
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
class IssueControllerIntegrationTest {

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
	@Autowired
	JdbcTemplate jdbc;
	@Autowired
	ObjectMapper objectMapper;
	@Autowired
	AesGcmCipher aesGcmCipher;
	@Autowired
	HmacHasher hmacHasher;

	private String token(String username) {
		User u = userRepository.findByUsername(username).orElseThrow();
		return jwtService.generate(u.getId(), u.getRole()).token();
	}

	private long categoryId(int level) {
		return jdbc.queryForObject(
				"SELECT id FROM categories WHERE level = ? ORDER BY sort_order LIMIT 1", Long.class, level);
	}

	private String body() {
		return """
				{"title":"엘리베이터 고장","callerName":"홍길동","callerPhone":"010-1234-5678",
				 "categoryL1Id":%d,"categoryL2Id":%d,"categoryL3Id":%d,
				 "priority":"URGENT","description":"3호기 멈춤"}"""
				.formatted(categoryId(1), categoryId(2), categoryId(3));
	}

	@Test
	void agentCreatesIssueAndPiiIsEncrypted() throws Exception {
		MvcResult result = mockMvc.perform(post("/api/issues")
				.header("Authorization", "Bearer " + token("agent1"))
				.contentType(MediaType.APPLICATION_JSON)
				.content(body()))
				.andExpect(status().isCreated())
				.andExpect(jsonPath("$.id").isNumber())
				.andExpect(jsonPath("$.status").value("NEW"))
				.andExpect(jsonPath("$.priority").value("URGENT"))
				.andReturn();

		long id = objectMapper.readTree(result.getResponse().getContentAsString()).get("id").asLong();

		byte[] nameEnc = jdbc.queryForObject(
				"SELECT caller_name_enc FROM issues WHERE id = ?", byte[].class, id);
		byte[] phoneEnc = jdbc.queryForObject(
				"SELECT caller_phone_enc FROM issues WHERE id = ?", byte[].class, id);
		String phoneHash = jdbc.queryForObject(
				"SELECT caller_phone_hash FROM issues WHERE id = ?", String.class, id);

		// stored bytes are NOT the plaintext, but decrypt back to it
		assertThat(new String(nameEnc, StandardCharsets.UTF_8)).isNotEqualTo("홍길동");
		assertThat(aesGcmCipher.decrypt(nameEnc)).isEqualTo("홍길동");
		assertThat(aesGcmCipher.decrypt(phoneEnc)).isEqualTo("010-1234-5678");
		assertThat(phoneHash).isEqualTo(hmacHasher.hashPhone("010-1234-5678")).hasSize(64);

		Integer events = jdbc.queryForObject(
				"SELECT COUNT(*) FROM issue_events WHERE issue_id = ? AND event_type = 'CREATED'",
				Integer.class, id);
		assertThat(events).isEqualTo(1);
	}

	@Test
	void fieldRoleIsForbidden() throws Exception {
		mockMvc.perform(post("/api/issues")
				.header("Authorization", "Bearer " + token("field1"))
				.contentType(MediaType.APPLICATION_JSON)
				.content(body()))
				.andExpect(status().isForbidden())
				.andExpect(jsonPath("$.code").value("FORBIDDEN"));
	}

	@Test
	void unauthenticatedIsRejected() throws Exception {
		mockMvc.perform(post("/api/issues")
				.contentType(MediaType.APPLICATION_JSON)
				.content(body()))
				.andExpect(status().isUnauthorized());
	}

	@Test
	void missingRequiredFieldReturns400() throws Exception {
		String invalid = """
				{"callerName":"홍길동","callerPhone":"010-1234-5678",
				 "categoryL1Id":%d,"categoryL2Id":%d,"categoryL3Id":%d,
				 "priority":"URGENT","description":"내용"}"""
				.formatted(categoryId(1), categoryId(2), categoryId(3));
		mockMvc.perform(post("/api/issues")
				.header("Authorization", "Bearer " + token("agent1"))
				.contentType(MediaType.APPLICATION_JSON)
				.content(invalid))
				.andExpect(status().isBadRequest())
				.andExpect(jsonPath("$.code").value("VALIDATION_FAILED"));
	}
}
