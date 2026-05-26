package com.smcs.issue.export;

import com.smcs.category.Category;
import com.smcs.category.CategoryRepository;
import com.smcs.crypto.HmacHasher;
import com.smcs.issue.Issue;
import com.smcs.issue.IssueRepository;
import com.smcs.issue.IssueSpecifications;
import com.smcs.issue.dto.IssueListFilter;
import com.smcs.user.User;
import com.smcs.user.UserRepository;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.io.Writer;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.stream.Collectors;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Streams the issues-list query result as CSV to a caller-provided {@link Writer}.
 * <p>
 * Bounded at {@link #MAX_ROWS} rows — the controller calls {@code count(spec)} via
 * {@link #count(IssueListFilter)} first and throws {@link ExportTooManyRowsException}
 * before the main {@code SELECT}, so the heavy fetch never runs over the cap (Deviation #3).
 * <p>
 * KST/{@code from..to} and HMAC-phone helpers are intentionally duplicated from
 * {@code IssueQueryService} (Deviation #4 — IssueQueryService keeps them private; a shared
 * helper class is deferred to a follow-up tech-debt story).
 */
@Service
public class IssueExportService {

	public static final int MAX_ROWS = 5_000;
	private static final ZoneId KST = ZoneId.of("Asia/Seoul");
	private static final int PHONE_MIN_DIGITS = 9; // matches IssueQueryService R1

	private final IssueRepository issueRepository;
	private final CategoryRepository categoryRepository;
	private final UserRepository userRepository;
	private final HmacHasher hmacHasher;
	private final IssueCsvExporter csvExporter;

	public IssueExportService(IssueRepository issueRepository, CategoryRepository categoryRepository,
			UserRepository userRepository, HmacHasher hmacHasher, IssueCsvExporter csvExporter) {
		this.issueRepository = issueRepository;
		this.categoryRepository = categoryRepository;
		this.userRepository = userRepository;
		this.hmacHasher = hmacHasher;
		this.csvExporter = csvExporter;
	}

	@Transactional(readOnly = true)
	public void exportCsv(IssueListFilter filter, boolean includePii, Writer out) {
		Specification<Issue> spec = buildSpec(filter);

		long count = issueRepository.count(spec);
		if (count > MAX_ROWS) {
			throw new ExportTooManyRowsException(count);
		}

		List<Issue> issues = issueRepository.findAll(spec.and(IssueSpecifications.defaultOrder()));

		Map<Long, String> categoryNames = categoryRepository.findAll().stream()
				.collect(Collectors.toMap(Category::getId, Category::getName));

		List<Long> assigneeIds = issues.stream()
				.map(Issue::getAssignedTo)
				.filter(Objects::nonNull)
				.distinct()
				.toList();
		Map<Long, String> assigneeNames = userRepository.findAllById(assigneeIds).stream()
				.collect(Collectors.toMap(User::getId, User::getDisplayName));

		try {
			csvExporter.writeHeader(out, includePii);
			for (Issue issue : issues) {
				csvExporter.writeRow(out, issue, categoryNames, assigneeNames, includePii);
			}
		} catch (IOException ex) {
			throw new UncheckedIOException(ex);
		}
	}

	private Specification<Issue> buildSpec(IssueListFilter filter) {
		String phoneHash = phoneHashOrNull(filter.q());
		Instant from = filter.from() == null ? null : startOfDay(filter.from());
		Instant to = filter.to() == null ? null : startOfDay(filter.to().plusDays(1));
		return IssueSpecifications.build(filter, phoneHash, from, to);
	}

	private String phoneHashOrNull(String q) {
		if (q == null) {
			return null;
		}
		String digits = q.replaceAll("\\D", "");
		return digits.length() >= PHONE_MIN_DIGITS ? hmacHasher.hashPhone(q) : null;
	}

	private Instant startOfDay(LocalDate d) {
		return d.atStartOfDay(KST).toInstant();
	}
}
