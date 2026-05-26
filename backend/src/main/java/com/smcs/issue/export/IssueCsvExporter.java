package com.smcs.issue.export;

import com.smcs.issue.Issue;
import java.io.IOException;
import java.io.Writer;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.Map;
import org.springframework.stereotype.Component;

/**
 * Writes issue rows to a CSV {@link Writer} per RFC 4180. Korean headers per Story 4.3 AC3;
 * PII columns appended last when {@code includePii} (Deviation #5 — preserve default column
 * indexes for downstream Excel macros). Caller controls the {@link Writer} so the controller
 * owns BOM emission and Content-Disposition.
 */
@Component
public class IssueCsvExporter {

	private static final ZoneId KST = ZoneId.of("Asia/Seoul");
	private static final DateTimeFormatter KST_DATETIME =
			DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm").withZone(KST);

	private static final String CATEGORY_SEPARATOR = " > ";
	private static final String EOL = "\r\n"; // RFC 4180

	private static final String[] BASE_HEADER = {
			"ID", "제목", "카테고리", "우선순위", "상태", "담당자", "접수일", "처리일", "처리 시간(분)"
	};
	private static final String[] PII_HEADER = { "발신자명", "발신자전화번호" };

	public void writeHeader(Writer out, boolean includePii) throws IOException {
		writeLine(out, includePii ? concat(BASE_HEADER, PII_HEADER) : BASE_HEADER);
	}

	public void writeRow(Writer out, Issue issue,
			Map<Long, String> categoryNames,
			Map<Long, String> assigneeNames,
			boolean includePii) throws IOException {
		String[] base = {
				String.valueOf(issue.getId()),
				nullToEmpty(issue.getTitle()),
				joinCategory(issue, categoryNames),
				issue.getPriority() == null ? "" : issue.getPriority().name(),
				issue.getStatus() == null ? "" : issue.getStatus().name(),
				issue.getAssignedTo() == null ? "" : nullToEmpty(assigneeNames.get(issue.getAssignedTo())),
				formatKst(issue.getCreatedAt()),
				formatKst(issue.getResolvedAt()),
				resolveMinutes(issue.getCreatedAt(), issue.getResolvedAt()),
		};
		writeLine(out, includePii
				? concat(base, new String[] { nullToEmpty(issue.getCallerName()), nullToEmpty(issue.getCallerPhone()) })
				: base);
	}

	private void writeLine(Writer out, String[] cells) throws IOException {
		StringBuilder sb = new StringBuilder();
		for (int i = 0; i < cells.length; i++) {
			if (i > 0) {
				sb.append(',');
			}
			sb.append(escape(cells[i]));
		}
		sb.append(EOL);
		out.write(sb.toString());
	}

	/** RFC 4180: quote if value contains comma, quote, CR, or LF; double internal quotes. */
	private String escape(String value) {
		if (value == null || value.isEmpty()) {
			return "";
		}
		boolean needsQuote = value.indexOf(',') >= 0
				|| value.indexOf('"') >= 0
				|| value.indexOf('\r') >= 0
				|| value.indexOf('\n') >= 0;
		if (!needsQuote) {
			return value;
		}
		return "\"" + value.replace("\"", "\"\"") + "\"";
	}

	private String joinCategory(Issue issue, Map<Long, String> names) {
		return nullToEmpty(names.get(issue.getCategoryL1Id()))
				+ CATEGORY_SEPARATOR + nullToEmpty(names.get(issue.getCategoryL2Id()))
				+ CATEGORY_SEPARATOR + nullToEmpty(names.get(issue.getCategoryL3Id()));
	}

	private String formatKst(Instant ts) {
		return ts == null ? "" : KST_DATETIME.format(ts);
	}

	private String resolveMinutes(Instant createdAt, Instant resolvedAt) {
		if (resolvedAt == null || createdAt == null) {
			return "";
		}
		return String.valueOf(Duration.between(createdAt, resolvedAt).toMinutes());
	}

	private String nullToEmpty(String v) {
		return v == null ? "" : v;
	}

	private String[] concat(String[] a, String[] b) {
		String[] r = new String[a.length + b.length];
		System.arraycopy(a, 0, r, 0, a.length);
		System.arraycopy(b, 0, r, a.length, b.length);
		return r;
	}
}
