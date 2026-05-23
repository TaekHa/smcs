package com.smcs.report;

import com.smcs.report.dto.ReportData;
import com.smcs.report.dto.ReportData.OpenIssueRow;
import com.smcs.report.dto.ReportKind;
import com.smcs.stats.dto.DashboardStats;
import com.smcs.stats.dto.DashboardStats.AssigneeCount;
import com.smcs.stats.dto.DashboardStats.CategoryCount;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.util.List;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.pdmodel.PDPage;
import org.apache.pdfbox.pdmodel.PDPageContentStream;
import org.apache.pdfbox.pdmodel.common.PDRectangle;
import org.apache.pdfbox.pdmodel.font.PDType0Font;

/**
 * Renders {@link ReportData} to a {@code byte[]} PDF (Story 3.3, AC3-5). A4, single bundled
 * Nanum Gothic font (subset-embedded for Korean — AC5). Layout is single-column text; the open
 * list paginates at {@link ReportService#OPEN_LIST_MAX} rows with an overflow footnote (AC4).
 */
final class ReportPdfRenderer {

	private static final float MARGIN = 50f;
	private static final float TITLE_SIZE = 20f;
	private static final float HEADING_SIZE = 14f;
	private static final float BODY_SIZE = 11f;
	private static final float LINE_GAP = 6f;
	private static final int TOP_N_CATEGORIES = 5;

	private final byte[] fontBytes;

	ReportPdfRenderer(byte[] fontBytes) {
		this.fontBytes = fontBytes;
	}

	byte[] render(ReportData data) {
		try (PDDocument doc = new PDDocument(); ByteArrayOutputStream out = new ByteArrayOutputStream()) {
			PDType0Font font;
			try (ByteArrayInputStream fontIn = new ByteArrayInputStream(fontBytes)) {
				font = PDType0Font.load(doc, fontIn, true); // subset-embed
			}

			PageCursor cursor = new PageCursor(doc, font);
			cursor.writeTitle("SMCS");
			cursor.writeTitle(data.kind() == ReportKind.DAILY ? "일간 보고서" : "주간 보고서");
			cursor.writeBody("기간: " + data.displayPeriod());
			cursor.gap();

			DashboardStats stats = data.stats();
			cursor.writeHeading("요약 KPI");
			cursor.writeBody("신규: " + stats.kpi().newCount() + "건");
			cursor.writeBody("처리: " + stats.kpi().resolvedCount() + "건");
			cursor.writeBody("미처리(현재): " + stats.kpi().openCount() + "건");
			cursor.writeBody("평균 처리시간: " + formatMinutes(stats.kpi().avgResolveMinutes()));
			cursor.gap();

			cursor.writeHeading("카테고리 분포");
			writeCategoryRows(cursor, stats.byCategory());
			cursor.gap();

			cursor.writeHeading("TOP " + TOP_N_CATEGORIES + " 카테고리");
			writeCategoryRows(cursor, ReportService.topCategories(stats.byCategory(), TOP_N_CATEGORIES));
			cursor.gap();

			cursor.writeHeading("담당자별 처리량");
			writeAssigneeRows(cursor, stats.byAssignee());
			cursor.gap();

			cursor.writeHeading("미처리 리스트");
			writeOpenIssueRows(cursor, data.openIssues(), data.totalOpenCount());

			cursor.close();
			doc.save(out);
			return out.toByteArray();
		} catch (IOException e) {
			throw new UncheckedIOException("Failed to render PDF report", e);
		}
	}

	private static void writeCategoryRows(PageCursor cursor, List<CategoryCount> rows) throws IOException {
		if (rows.isEmpty()) {
			cursor.writeBody("데이터 없음");
			return;
		}
		for (CategoryCount r : rows) {
			cursor.writeBody("- " + (r.name() == null ? "(미지정)" : r.name()) + ": " + r.count() + "건");
		}
	}

	private static void writeAssigneeRows(PageCursor cursor, List<AssigneeCount> rows) throws IOException {
		if (rows.isEmpty()) {
			cursor.writeBody("데이터 없음");
			return;
		}
		for (AssigneeCount r : rows) {
			cursor.writeBody("- " + (r.name() == null ? "(미지정)" : r.name()) + ": " + r.resolved() + "건");
		}
	}

	private static void writeOpenIssueRows(PageCursor cursor, List<OpenIssueRow> rows, long totalOpenCount)
			throws IOException {
		if (totalOpenCount <= 0) {
			cursor.writeBody("데이터 없음");
			return;
		}
		// 컬럼: ID / 제목 / 우선순위 / 담당자 (AC3 — PO 정제 2026-05-23)
		cursor.writeBody("ID | 제목 | 우선순위 | 담당자");
		int shown = (int) Math.min(rows.size(), ReportService.OPEN_LIST_MAX);
		for (int i = 0; i < shown; i++) {
			OpenIssueRow r = rows.get(i);
			cursor.writeBody("#" + r.id() + " | " + r.title()
					+ " | " + r.priority()
					+ " | " + (r.assigneeName() == null ? "(미배정)" : r.assigneeName()));
		}
		// TD-2: footnote uses the true total, not the paged slice size, so "이하 N건 생략" is accurate.
		long hidden = totalOpenCount - ReportService.OPEN_LIST_MAX;
		if (hidden > 0) {
			cursor.writeBody("... 이하 " + hidden + "건 생략 — 보관함 PDF 참조");
		}
	}

	private static String formatMinutes(long minutes) {
		if (minutes <= 0) {
			return "0분";
		}
		long hours = minutes / 60;
		long mins = minutes % 60;
		if (hours == 0) {
			return mins + "분";
		}
		return hours + "시간 " + mins + "분";
	}

	/**
	 * Tracks the active page + caret. New pages are opened automatically when the next line would
	 * cross the bottom margin — keeps callers free of layout math.
	 */
	private static final class PageCursor {
		private final PDDocument doc;
		private final PDType0Font font;
		private PDPage page;
		private PDPageContentStream stream;
		private float y;

		PageCursor(PDDocument doc, PDType0Font font) throws IOException {
			this.doc = doc;
			this.font = font;
			newPage();
		}

		void writeTitle(String text) throws IOException {
			ensureRoomFor(TITLE_SIZE);
			stream.beginText();
			stream.setFont(font, TITLE_SIZE);
			stream.newLineAtOffset(MARGIN, y);
			stream.showText(text);
			stream.endText();
			y -= TITLE_SIZE + LINE_GAP;
		}

		void writeHeading(String text) throws IOException {
			ensureRoomFor(HEADING_SIZE);
			stream.beginText();
			stream.setFont(font, HEADING_SIZE);
			stream.newLineAtOffset(MARGIN, y);
			stream.showText(text);
			stream.endText();
			y -= HEADING_SIZE + LINE_GAP;
		}

		void writeBody(String text) throws IOException {
			ensureRoomFor(BODY_SIZE);
			stream.beginText();
			stream.setFont(font, BODY_SIZE);
			stream.newLineAtOffset(MARGIN, y);
			stream.showText(text);
			stream.endText();
			y -= BODY_SIZE + LINE_GAP;
		}

		void gap() {
			y -= LINE_GAP * 2;
		}

		void close() throws IOException {
			stream.close();
		}

		private void ensureRoomFor(float lineHeight) throws IOException {
			if (y - lineHeight < MARGIN) {
				stream.close();
				newPage();
			}
		}

		private void newPage() throws IOException {
			page = new PDPage(PDRectangle.A4);
			doc.addPage(page);
			stream = new PDPageContentStream(doc, page);
			y = page.getMediaBox().getHeight() - MARGIN;
		}
	}
}
