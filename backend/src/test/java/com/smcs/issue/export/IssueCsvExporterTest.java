package com.smcs.issue.export;

import static org.assertj.core.api.Assertions.assertThat;

import com.smcs.issue.Issue;
import com.smcs.issue.IssueStatus;
import com.smcs.issue.Priority;
import java.io.StringWriter;
import java.lang.reflect.Field;
import java.time.Instant;
import java.util.Map;
import org.junit.jupiter.api.Test;

class IssueCsvExporterTest {

	private final IssueCsvExporter exporter = new IssueCsvExporter();

	@Test
	void headerWithoutPiiHas9Columns() throws Exception {
		StringWriter out = new StringWriter();
		exporter.writeHeader(out, false);
		assertThat(out.toString())
				.isEqualTo("ID,제목,카테고리,우선순위,상태,담당자,접수일,처리일,처리 시간(분)\r\n");
	}

	@Test
	void headerWithPiiAppendsTwoColumns() throws Exception {
		StringWriter out = new StringWriter();
		exporter.writeHeader(out, true);
		assertThat(out.toString())
				.isEqualTo("ID,제목,카테고리,우선순위,상태,담당자,접수일,처리일,처리 시간(분),발신자명,발신자전화번호\r\n");
	}

	@Test
	void rowEscapesCommaInTitle() throws Exception {
		Issue issue = newIssue(1L, "제목, with comma");
		StringWriter out = new StringWriter();
		exporter.writeRow(out, issue, categories(), assignees(), false);
		assertThat(out.toString()).startsWith("1,\"제목, with comma\",");
	}

	@Test
	void rowEscapesEmbeddedDoubleQuote() throws Exception {
		Issue issue = newIssue(2L, "say \"hi\"");
		StringWriter out = new StringWriter();
		exporter.writeRow(out, issue, categories(), assignees(), false);
		assertThat(out.toString()).contains(",\"say \"\"hi\"\"\",");
	}

	@Test
	void rowEscapesEmbeddedNewline() throws Exception {
		Issue issue = newIssue(3L, "line1\nline2");
		StringWriter out = new StringWriter();
		exporter.writeRow(out, issue, categories(), assignees(), false);
		assertThat(out.toString()).contains(",\"line1\nline2\",");
	}

	@Test
	void categoryCollapsesIntoSingleColumn() throws Exception {
		Issue issue = newIssue(4L, "title");
		StringWriter out = new StringWriter();
		exporter.writeRow(out, issue, categories(), assignees(), false);
		assertThat(out.toString()).contains(",대분류 > 중분류 > 소분류,");
	}

	@Test
	void resolveMinutesIsEmptyWhenUnresolved() throws Exception {
		Issue issue = newIssue(5L, "open");
		StringWriter out = new StringWriter();
		exporter.writeRow(out, issue, categories(), assignees(), false);
		// last two columns (resolvedAt + minutes) both empty → ends with ",,\r\n"
		assertThat(out.toString()).endsWith(",,\r\n");
	}

	@Test
	void resolveMinutesIsComputedWhenResolved() throws Exception {
		Issue issue = newIssue(6L, "closed");
		Instant created = Instant.parse("2026-05-26T00:00:00Z");
		set(issue, "createdAt", created);
		set(issue, "resolvedAt", created.plusSeconds(125 * 60));
		StringWriter out = new StringWriter();
		exporter.writeRow(out, issue, categories(), assignees(), false);
		assertThat(out.toString()).endsWith(",125\r\n");
	}

	@Test
	void piiColumnsAppendedAtTail() throws Exception {
		Issue issue = newIssue(7L, "title");
		set(issue, "callerName", "홍길동");
		set(issue, "callerPhone", "010-1234-5678");
		StringWriter out = new StringWriter();
		exporter.writeRow(out, issue, categories(), assignees(), true);
		assertThat(out.toString()).endsWith(",홍길동,010-1234-5678\r\n");
	}

	@Test
	void assigneeNameRenderedWhenPresent() throws Exception {
		Issue issue = newIssue(8L, "title");
		set(issue, "assignedTo", 99L);
		StringWriter out = new StringWriter();
		exporter.writeRow(out, issue, categories(), Map.of(99L, "필드맨"), false);
		assertThat(out.toString()).contains(",필드맨,");
	}

	@Test
	void emptyValueProducesEmptyCellNotQuoted() throws Exception {
		Issue issue = newIssue(9L, "");
		StringWriter out = new StringWriter();
		exporter.writeRow(out, issue, categories(), assignees(), false);
		assertThat(out.toString()).startsWith("9,,"); // title empty → bare comma
	}

	// --- helpers ---

	private Issue newIssue(Long id, String title) throws Exception {
		Issue i = new Issue(title, "desc", null, null, null, 10L, 20L, 30L, Priority.NORMAL, 1L);
		set(i, "id", id);
		set(i, "status", IssueStatus.NEW);
		set(i, "createdAt", Instant.parse("2026-05-26T00:00:00Z"));
		return i;
	}

	private Map<Long, String> categories() {
		return Map.of(10L, "대분류", 20L, "중분류", 30L, "소분류");
	}

	private Map<Long, String> assignees() {
		return Map.of();
	}

	private static void set(Object target, String field, Object value) throws Exception {
		Field f = target.getClass().getDeclaredField(field);
		f.setAccessible(true);
		f.set(target, value);
	}
}
