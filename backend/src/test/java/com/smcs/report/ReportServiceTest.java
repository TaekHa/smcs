package com.smcs.report;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.smcs.issue.Issue;
import com.smcs.issue.IssueRepository;
import com.smcs.issue.Priority;
import com.smcs.stats.StatsService;
import com.smcs.stats.dto.DashboardStats;
import com.smcs.stats.dto.DashboardStats.AssigneeCount;
import com.smcs.stats.dto.DashboardStats.CategoryCount;
import com.smcs.stats.dto.DashboardStats.Kpi;
import com.smcs.stats.dto.DashboardStats.PriorityCount;
import com.smcs.stats.dto.DashboardStats.TrendPoint;
import com.smcs.user.User;
import com.smcs.user.UserRepository;
import java.io.ByteArrayInputStream;
import java.time.Instant;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import org.apache.pdfbox.Loader;
import org.apache.pdfbox.io.RandomAccessReadBuffer;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.text.PDFTextStripper;
import org.junit.jupiter.api.Test;
import org.springframework.data.jpa.domain.Specification;

/**
 * Unit tests (AC6). StatsService / IssueRepository / UserRepository are mocked so generation is
 * time-independent and DB-free. PDF correctness is verified by re-loading the bytes through
 * PDFBox and asserting both layout (≤2 pages, AC4) and content (Korean text → font embedded, AC5).
 */
class ReportServiceTest {

	private final StatsService statsService = mock(StatsService.class);
	private final IssueRepository issueRepository = mock(IssueRepository.class);
	private final UserRepository userRepository = mock(UserRepository.class);

	private final ReportService service = new ReportService(statsService, issueRepository, userRepository);

	@Test
	void generateDailyEmbedsKoreanAndStaysWithinTwoPages() throws Exception {
		stubStats(sampleStats());
		Issue i1 = issue(1L, "전원 차단됨", Priority.URGENT, 100L);
		Issue i2 = issue(2L, "VoIP 끊김", Priority.HIGH, null);
		User u = user(100L, "김현장");
		when(issueRepository.findAll(any(Specification.class))).thenReturn(List.of(i1, i2));
		when(userRepository.findAllById(any())).thenReturn(List.of(u));

		byte[] pdf = service.generateDaily(LocalDate.of(2026, 5, 21));

		String text = textOf(pdf);
		assertThat(pageCountOf(pdf)).isBetween(1, 2);
		assertThat(text).contains("일간 보고서");
		assertThat(text).contains("기간: 2026-05-21");
		assertThat(text).contains("요약 KPI");
		assertThat(text).contains("신규: 4");
		assertThat(text).contains("처리: 2");
		assertThat(text).contains("미처리(현재): 3");
		assertThat(text).contains("카테고리 분포");
		assertThat(text).contains("담당자별 처리량");
		assertThat(text).contains("미처리 리스트");
		assertThat(text).contains("전원 차단됨");
		assertThat(text).contains("VoIP 끊김");
		assertThat(text).contains("김현장");
		assertThat(text).contains("(미배정)");
	}

	@Test
	void generateWeeklyRendersWeeklyTitleAndPeriod() throws Exception {
		stubStats(sampleStats());
		when(issueRepository.findAll(any(Specification.class))).thenReturn(List.of());
		when(userRepository.findAllById(any())).thenReturn(List.of());

		byte[] pdf = service.generateWeekly(2026, 21);

		String text = textOf(pdf);
		assertThat(text).contains("주간 보고서");
		assertThat(text).contains("~"); // 표시 기간 (Mon ~ Sun)
		assertThat(text).contains("데이터 없음"); // 빈 미처리 리스트
	}

	@Test
	void overflowFootnoteShownWhenOpenListExceedsCap() throws Exception {
		stubStats(sampleStats());
		List<Issue> many = new ArrayList<>();
		for (int i = 1; i <= ReportService.OPEN_LIST_MAX + 5; i++) {
			many.add(issue((long) i, "이슈 " + i, Priority.NORMAL, 100L));
		}
		User u = user(100L, "담당자A");
		when(issueRepository.findAll(any(Specification.class))).thenReturn(many);
		when(userRepository.findAllById(any())).thenReturn(List.of(u));

		byte[] pdf = service.generateDaily(LocalDate.of(2026, 5, 21));

		String text = textOf(pdf);
		assertThat(text).contains("이하 5건 생략 — 보관함 PDF 참조");
	}

	@Test
	void emptyOpenListRendersDataAbsentLine() throws Exception {
		stubStats(emptyStats());
		when(issueRepository.findAll(any(Specification.class))).thenReturn(List.of());
		when(userRepository.findAllById(any())).thenReturn(List.of());

		byte[] pdf = service.generateDaily(LocalDate.of(2026, 5, 21));

		String text = textOf(pdf);
		assertThat(text).contains("데이터 없음");
		assertThat(text).contains("신규: 0");
		assertThat(text).contains("평균 처리시간: 0분");
	}

	// ---------- helpers ----------

	private void stubStats(DashboardStats stats) {
		when(statsService.aggregate(any(Instant.class), any(Instant.class))).thenReturn(stats);
	}

	private static DashboardStats sampleStats() {
		return new DashboardStats(
				new Kpi(4, 2, 3, 90),
				List.of(new CategoryCount("관리자웹", 3), new CategoryCount("입주민앱", 1)),
				List.of(new AssigneeCount("김현장", 2)),
				List.of(new PriorityCount(Priority.URGENT, 1), new PriorityCount(Priority.HIGH, 3)),
				List.of(new TrendPoint(LocalDate.of(2026, 5, 21), 4, 2)));
	}

	private static DashboardStats emptyStats() {
		return new DashboardStats(
				new Kpi(0, 0, 0, 0), List.of(), List.of(), List.of(), List.of());
	}

	private static Issue issue(long id, String title, Priority priority, Long assignedTo) {
		Issue i = mock(Issue.class);
		when(i.getId()).thenReturn(id);
		when(i.getTitle()).thenReturn(title);
		when(i.getPriority()).thenReturn(priority);
		when(i.getAssignedTo()).thenReturn(assignedTo);
		return i;
	}

	private static User user(long id, String displayName) {
		User u = mock(User.class);
		when(u.getId()).thenReturn(id);
		when(u.getDisplayName()).thenReturn(displayName);
		return u;
	}

	private static int pageCountOf(byte[] pdf) throws Exception {
		try (PDDocument doc = Loader.loadPDF(new RandomAccessReadBuffer(new ByteArrayInputStream(pdf)))) {
			return doc.getNumberOfPages();
		}
	}

	private static String textOf(byte[] pdf) throws Exception {
		try (PDDocument doc = Loader.loadPDF(new RandomAccessReadBuffer(new ByteArrayInputStream(pdf)))) {
			return new PDFTextStripper().getText(doc);
		}
	}
}
