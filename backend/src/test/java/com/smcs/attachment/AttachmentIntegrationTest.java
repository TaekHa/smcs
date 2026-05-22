package com.smcs.attachment;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.smcs.security.JwtService;
import com.smcs.user.User;
import com.smcs.user.UserRepository;
import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import javax.imageio.ImageIO;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.testcontainers.containers.PostgreSQLContainer;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("local")
class AttachmentIntegrationTest {

	@SuppressWarnings("resource")
	static final PostgreSQLContainer<?> POSTGRES = new PostgreSQLContainer<>("postgres:16-alpine");

	static final Path FILES_DIR;

	static {
		POSTGRES.start();
		try {
			FILES_DIR = Files.createTempDirectory("smcs-files-test");
		} catch (IOException e) {
			throw new IllegalStateException(e);
		}
	}

	@DynamicPropertySource
	static void properties(DynamicPropertyRegistry registry) {
		registry.add("spring.datasource.url", POSTGRES::getJdbcUrl);
		registry.add("spring.datasource.username", POSTGRES::getUsername);
		registry.add("spring.datasource.password", POSTGRES::getPassword);
		registry.add("smcs.files.dir", () -> FILES_DIR.toString());
	}

	@Autowired
	MockMvc mockMvc;
	@Autowired
	JwtService jwtService;
	@Autowired
	UserRepository userRepository;
	@Autowired
	JdbcTemplate jdbc;

	private long issueId;

	private String token(String username) {
		User u = userRepository.findByUsername(username).orElseThrow();
		return jwtService.generate(u.getId(), u.getRole()).token();
	}

	private long userId(String username) {
		return userRepository.findByUsername(username).orElseThrow().getId();
	}

	private long categoryId(int level) {
		return jdbc.queryForObject(
				"SELECT id FROM categories WHERE level = ? ORDER BY sort_order LIMIT 1", Long.class, level);
	}

	private byte[] jpeg() throws IOException {
		BufferedImage img = new BufferedImage(4, 4, BufferedImage.TYPE_INT_RGB);
		ByteArrayOutputStream out = new ByteArrayOutputStream();
		ImageIO.write(img, "jpg", out);
		return out.toByteArray();
	}

	private MockMultipartFile imageFile() throws IOException {
		return new MockMultipartFile("file", "photo.jpg", "image/jpeg", jpeg());
	}

	@BeforeEach
	void seed() throws Exception {
		jdbc.update("DELETE FROM notifications");
		jdbc.update("DELETE FROM attachments");
		jdbc.update("DELETE FROM comments");
		jdbc.update("DELETE FROM issue_events");
		jdbc.update("DELETE FROM issues");
		String body = """
				{"title":"첨부대상","callerName":"홍길동","callerPhone":"010-1234-5678",
				 "categoryL1Id":%d,"categoryL2Id":%d,"categoryL3Id":%d,
				 "priority":"URGENT","description":"본문"}"""
				.formatted(categoryId(1), categoryId(2), categoryId(3));
		mockMvc.perform(post("/api/issues")
				.header("Authorization", "Bearer " + token("agent1"))
				.contentType(MediaType.APPLICATION_JSON).content(body))
				.andExpect(status().isCreated());
		issueId = jdbc.queryForObject("SELECT id FROM issues ORDER BY id DESC LIMIT 1", Long.class);
		// assign to field1 (status → ASSIGNED) without going through the endpoint
		jdbc.update("UPDATE issues SET assigned_to = ?, status = 'ASSIGNED' WHERE id = ?",
				userId("field1"), issueId);
	}

	@Test
	void fieldAssigneeUploadsValidJpeg() throws Exception {
		mockMvc.perform(multipart("/api/issues/" + issueId + "/attachments")
				.file(imageFile())
				.header("Authorization", "Bearer " + token("field1")))
				.andExpect(status().isCreated())
				.andExpect(jsonPath("$.originalName").value("photo.jpg"))
				.andExpect(jsonPath("$.url").value(org.hamcrest.Matchers.startsWith("/files/")));
		Integer rows = jdbc.queryForObject(
				"SELECT COUNT(*) FROM attachments WHERE issue_id = ?", Integer.class, issueId);
		Assertions.assertEquals(1, rows);
	}

	@Test
	void rejectsNonImage() throws Exception {
		MockMultipartFile bad = new MockMultipartFile("file", "x.jpg", "image/jpeg", "not an image".getBytes());
		mockMvc.perform(multipart("/api/issues/" + issueId + "/attachments")
				.file(bad)
				.header("Authorization", "Bearer " + token("field1")))
				.andExpect(status().isBadRequest())
				.andExpect(jsonPath("$.code").value("INVALID_IMAGE"));
	}

	@Test
	void rejectsBeyondTenAttachments() throws Exception {
		for (int i = 0; i < 10; i++) {
			jdbc.update("INSERT INTO attachments(issue_id, uploader_id, filename, original_name, mime_type, size_bytes)"
					+ " VALUES (?,?,?,?,?,?)", issueId, userId("field1"), "2026/05/seed-" + i + ".jpg",
					"seed" + i + ".jpg", "image/jpeg", 10L);
		}
		mockMvc.perform(multipart("/api/issues/" + issueId + "/attachments")
				.file(imageFile())
				.header("Authorization", "Bearer " + token("field1")))
				.andExpect(status().isBadRequest())
				.andExpect(jsonPath("$.code").value("ATTACHMENT_LIMIT"));
	}

	@Test
	void nonAssigneeFieldCannotUpload() throws Exception {
		mockMvc.perform(multipart("/api/issues/" + issueId + "/attachments")
				.file(imageFile())
				.header("Authorization", "Bearer " + token("field2")))
				.andExpect(status().isForbidden());
	}

	@Test
	void servesOwnAttachmentAndBlocksOthers() throws Exception {
		mockMvc.perform(multipart("/api/issues/" + issueId + "/attachments")
				.file(imageFile())
				.header("Authorization", "Bearer " + token("field1")))
				.andExpect(status().isCreated());
		String filename = jdbc.queryForObject(
				"SELECT filename FROM attachments WHERE issue_id = ? LIMIT 1", String.class, issueId);

		mockMvc.perform(get("/files/" + filename).header("Authorization", "Bearer " + token("field1")))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$").doesNotExist()); // binary body, not JSON
		mockMvc.perform(get("/files/" + filename).header("Authorization", "Bearer " + token("field2")))
				.andExpect(status().isForbidden());
		mockMvc.perform(get("/files/2026/05/missing.jpg").header("Authorization", "Bearer " + token("field1")))
				.andExpect(status().isNotFound());
	}

	@Test
	void fieldActionCommentAndComplete() throws Exception {
		jdbc.update("UPDATE issues SET status = 'IN_PROGRESS' WHERE id = ?", issueId);
		mockMvc.perform(post("/api/issues/" + issueId + "/comments")
				.header("Authorization", "Bearer " + token("field1"))
				.contentType(MediaType.APPLICATION_JSON)
				.content("{\"body\":\"부품 교체 완료\",\"kind\":\"FIELD_ACTION\"}"))
				.andExpect(status().isCreated())
				.andExpect(jsonPath("$.kind").value("FIELD_ACTION"));
		mockMvc.perform(post("/api/issues/" + issueId + "/transition")
				.header("Authorization", "Bearer " + token("field1"))
				.contentType(MediaType.APPLICATION_JSON).content("{\"to\":\"DONE\"}"))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.status").value("DONE"))
				.andExpect(jsonPath("$.resolvedAt").isNotEmpty());
		Integer resolved = jdbc.queryForObject(
				"SELECT COUNT(*) FROM issue_events WHERE issue_id = ? AND event_type = 'RESOLVED'",
				Integer.class, issueId);
		Assertions.assertEquals(1, resolved);
	}

	@Test
	void unauthenticatedUploadRejected() throws Exception {
		mockMvc.perform(multipart("/api/issues/" + issueId + "/attachments").file(imageFile()))
				.andExpect(status().isUnauthorized());
	}
}
