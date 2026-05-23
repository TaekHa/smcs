package com.smcs.report;

import com.smcs.issue.Issue;
import com.smcs.issue.IssueRepository;
import com.smcs.issue.IssueStatus;
import com.smcs.issue.Priority;
import com.smcs.report.dto.ReportData;
import com.smcs.report.dto.ReportData.OpenIssueRow;
import com.smcs.report.dto.ReportKind;
import com.smcs.stats.StatsRange;
import com.smcs.stats.StatsService;
import com.smcs.stats.dto.DashboardStats;
import com.smcs.stats.dto.DashboardStats.CategoryCount;
import com.smcs.user.User;
import com.smcs.user.UserRepository;
import jakarta.persistence.criteria.Predicate;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.stream.Collectors;
import org.springframework.core.io.ClassPathResource;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Builds the daily/weekly PDF report on demand (Story 3.3). Time-independent — accepts the
 * date/week as arguments so unit tests bypass the clock (Dev Notes §5.8). Aggregates come from
 * {@link StatsService} (Story 3.1 — single source); the open-issue list is queried separately
 * because {@code openCount} alone can't render rows. No persistence (storage is Story 3.4 / V6).
 */
@Service
public class ReportService {

	/** AC4 — open list cap before overflow footnote ("이하 N건 생략 — 보관함 PDF 참조"). */
	static final int OPEN_LIST_MAX = 30;

	private final StatsService statsService;
	private final IssueRepository issueRepository;
	private final UserRepository userRepository;
	private final ReportPdfRenderer renderer;

	public ReportService(StatsService statsService, IssueRepository issueRepository,
			UserRepository userRepository) {
		this.statsService = statsService;
		this.issueRepository = issueRepository;
		this.userRepository = userRepository;
		this.renderer = new ReportPdfRenderer(loadFont());
	}

	@Transactional(readOnly = true)
	public byte[] generateDaily(LocalDate date) {
		return generate(ReportPeriod.forDate(date));
	}

	@Transactional(readOnly = true)
	public byte[] generateWeekly(int weekBasedYear, int weekOfYear) {
		return generate(ReportPeriod.forWeek(weekBasedYear, weekOfYear));
	}

	private byte[] generate(ReportPeriod period) {
		StatsRange range = period.range();
		DashboardStats stats = statsService.aggregate(range.from(), range.to());
		OpenIssuesPage open = loadOpenIssues();
		ReportData data = new ReportData(period.kind(), period.periodKey(), period.displayPeriod(),
				stats, open.rows(), open.total());
		return renderer.render(data);
	}

	/**
	 * TD-2: load only {@code OPEN_LIST_MAX + 1} rows (memory-safe under operational growth) and
	 * fetch the true total via a separate {@code count} query so the renderer's footnote can
	 * report an accurate "이하 N건 생략".
	 */
	private OpenIssuesPage loadOpenIssues() {
		Specification<Issue> open = (root, query, cb) -> root.get("status").in(openStatuses());
		Specification<Issue> ordered = open.and(severityThenCreatedAsc());

		List<Issue> issues = issueRepository
				.findAll(ordered, PageRequest.of(0, OPEN_LIST_MAX + 1))
				.getContent();
		long total = issueRepository.count(open);

		List<Long> assigneeIds = issues.stream().map(Issue::getAssignedTo).filter(Objects::nonNull).distinct().toList();
		Map<Long, String> names = userRepository.findAllById(assigneeIds).stream()
				.collect(Collectors.toMap(User::getId, User::getDisplayName));

		List<OpenIssueRow> rows = issues.stream()
				.map(i -> new OpenIssueRow(i.getId(), i.getTitle(), i.getPriority(),
						i.getAssignedTo() == null ? null : names.get(i.getAssignedTo())))
				.toList();
		return new OpenIssuesPage(rows, total);
	}

	private record OpenIssuesPage(List<OpenIssueRow> rows, long total) {}

	private static List<IssueStatus> openStatuses() {
		return List.of(IssueStatus.NEW, IssueStatus.ASSIGNED, IssueStatus.IN_PROGRESS);
	}

	/** Severity-first ordering matches the issue list default (§5.2). */
	private static Specification<Issue> severityThenCreatedAsc() {
		return (root, query, cb) -> {
			Class<?> rt = query.getResultType();
			if (rt != Long.class && rt != long.class) {
				query.orderBy(
						cb.asc(cb.selectCase(root.get("priority"))
								.when(Priority.URGENT, 0)
								.when(Priority.HIGH, 1)
								.when(Priority.NORMAL, 2)
								.when(Priority.LOW, 3)
								.otherwise(4)),
						cb.asc(root.get("createdAt")));
			}
			return cb.<Predicate>conjunction();
		};
	}

	static List<CategoryCount> topCategories(List<CategoryCount> all, int n) {
		return all.stream().limit(n).toList();
	}

	private static byte[] loadFont() {
		try (InputStream in = new ClassPathResource("fonts/NanumGothic-Regular.ttf").getInputStream();
				ByteArrayOutputStream out = new ByteArrayOutputStream()) {
			in.transferTo(out);
			return out.toByteArray();
		} catch (IOException e) {
			throw new UncheckedIOException("Failed to load bundled font: fonts/NanumGothic-Regular.ttf", e);
		}
	}
}
