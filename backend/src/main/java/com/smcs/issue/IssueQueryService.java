package com.smcs.issue;

import com.smcs.category.Category;
import com.smcs.category.CategoryRepository;
import com.smcs.crypto.HmacHasher;
import com.smcs.issue.dto.IssueListFilter;
import com.smcs.issue.dto.IssueSummary;
import com.smcs.user.User;
import com.smcs.user.UserRepository;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.stream.Collectors;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class IssueQueryService {

	private static final ZoneId KST = ZoneId.of("Asia/Seoul");
	private static final int PHONE_MIN_DIGITS = 9; // PO refinement R1

	private final IssueRepository issueRepository;
	private final CategoryRepository categoryRepository;
	private final UserRepository userRepository;
	private final HmacHasher hmacHasher;

	public IssueQueryService(IssueRepository issueRepository, CategoryRepository categoryRepository,
			UserRepository userRepository, HmacHasher hmacHasher) {
		this.issueRepository = issueRepository;
		this.categoryRepository = categoryRepository;
		this.userRepository = userRepository;
		this.hmacHasher = hmacHasher;
	}

	@Transactional(readOnly = true)
	public Page<IssueSummary> list(IssueListFilter filter, Pageable pageable) {
		String phoneHash = phoneHashOrNull(filter.q());
		Instant from = filter.from() == null ? null : startOfDay(filter.from());
		Instant to = filter.to() == null ? null : startOfDay(filter.to().plusDays(1)); // exclusive

		Specification<Issue> spec = IssueSpecifications.build(filter, phoneHash, from, to);

		Page<Issue> page;
		if (pageable.getSort().isUnsorted()) {
			page = issueRepository.findAll(
					spec.and(IssueSpecifications.defaultOrder()),
					PageRequest.of(pageable.getPageNumber(), pageable.getPageSize()));
		} else {
			// R2: 'priority' is a VARCHAR enum — lexicographic sort != severity.
			// It is not a user-sortable column; strip it if the client sends it.
			Sort safe = Sort.by(pageable.getSort().stream()
					.filter(o -> !"priority".equalsIgnoreCase(o.getProperty()))
					.toList());
			Pageable effective = safe.isSorted()
					? PageRequest.of(pageable.getPageNumber(), pageable.getPageSize(), safe)
					: PageRequest.of(pageable.getPageNumber(), pageable.getPageSize());
			Specification<Issue> ordered = safe.isSorted() ? spec : spec.and(IssueSpecifications.defaultOrder());
			page = issueRepository.findAll(ordered, effective);
		}

		Map<Long, String> categoryNames = categoryRepository.findAll().stream()
				.collect(Collectors.toMap(Category::getId, Category::getName));
		List<Long> assigneeIds = page.getContent().stream()
				.map(Issue::getAssignedTo).filter(Objects::nonNull).distinct().toList();
		Map<Long, String> assigneeNames = userRepository.findAllById(assigneeIds).stream()
				.collect(Collectors.toMap(User::getId, User::getDisplayName));

		return page.map(i -> new IssueSummary(
				i.getId(),
				i.getTitle(),
				categoryNames.get(i.getCategoryL1Id()),
				categoryNames.get(i.getCategoryL2Id()),
				categoryNames.get(i.getCategoryL3Id()),
				i.getPriority(),
				i.getStatus(),
				i.getAssignedTo() == null ? null : assigneeNames.get(i.getAssignedTo()),
				i.getCreatedAt()));
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
