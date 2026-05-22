package com.smcs.issue;

import com.smcs.attachment.Attachment;
import com.smcs.attachment.AttachmentRepository;
import com.smcs.attachment.dto.AttachmentResponse;
import com.smcs.category.Category;
import com.smcs.category.CategoryRepository;
import com.smcs.comment.Comment;
import com.smcs.comment.CommentRepository;
import com.smcs.comment.dto.CommentResponse;
import com.smcs.crypto.HmacHasher;
import com.smcs.issue.dto.CategoryRef;
import com.smcs.issue.dto.IssueActivityResponse;
import com.smcs.issue.dto.IssueDetailResponse;
import com.smcs.issue.dto.IssueListFilter;
import com.smcs.issue.dto.IssueSummary;
import com.smcs.user.User;
import com.smcs.user.UserRepository;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
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
	private final IssueAccessGuard accessGuard;
	private final IssueEventRepository issueEventRepository;
	private final CommentRepository commentRepository;
	private final AttachmentRepository attachmentRepository;

	public IssueQueryService(IssueRepository issueRepository, CategoryRepository categoryRepository,
			UserRepository userRepository, HmacHasher hmacHasher, IssueAccessGuard accessGuard,
			IssueEventRepository issueEventRepository, CommentRepository commentRepository,
			AttachmentRepository attachmentRepository) {
		this.issueRepository = issueRepository;
		this.categoryRepository = categoryRepository;
		this.userRepository = userRepository;
		this.hmacHasher = hmacHasher;
		this.accessGuard = accessGuard;
		this.issueEventRepository = issueEventRepository;
		this.commentRepository = commentRepository;
		this.attachmentRepository = attachmentRepository;
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

	/**
	 * Full issue detail. Caller PII is decrypted by the JPA converter on read but only
	 * surfaced for privileged (AGENT/ADMIN) requests; FIELD gets null (Deviation #2).
	 */
	@Transactional(readOnly = true)
	public IssueDetailResponse getDetail(Long issueId, Long currentUserId, boolean privileged) {
		Issue issue = accessGuard.requireAccessible(issueId, currentUserId, privileged);

		Map<Long, String> categoryNames = categoryRepository.findAll().stream()
				.collect(Collectors.toMap(Category::getId, Category::getName));

		List<Comment> comments = commentRepository.findByIssueIdOrderByCreatedAtAsc(issueId);
		List<Attachment> attachments = attachmentRepository.findByIssueIdOrderByCreatedAtAsc(issueId);

		Set<Long> userIds = new HashSet<>();
		userIds.add(issue.getCreatedBy());
		if (issue.getAssignedTo() != null) {
			userIds.add(issue.getAssignedTo());
		}
		comments.forEach(c -> userIds.add(c.getAuthorId()));
		Map<Long, String> userNames = userRepository.findAllById(userIds).stream()
				.collect(Collectors.toMap(User::getId, User::getDisplayName));

		List<CommentResponse> commentDtos = comments.stream()
				.map(c -> new CommentResponse(
						c.getId(), userNames.get(c.getAuthorId()), c.getBody(), c.getKind(), c.getCreatedAt()))
				.toList();
		List<AttachmentResponse> attachmentDtos = attachments.stream()
				.map(AttachmentResponse::from).toList();

		return new IssueDetailResponse(
				issue.getId(),
				issue.getTitle(),
				issue.getDescription(),
				new CategoryRef(issue.getCategoryL1Id(), categoryNames.get(issue.getCategoryL1Id())),
				new CategoryRef(issue.getCategoryL2Id(), categoryNames.get(issue.getCategoryL2Id())),
				new CategoryRef(issue.getCategoryL3Id(), categoryNames.get(issue.getCategoryL3Id())),
				issue.getPriority(),
				issue.getStatus(),
				userNames.get(issue.getCreatedBy()),
				issue.getAssignedTo() == null ? null : userNames.get(issue.getAssignedTo()),
				issue.getResolvedAt(),
				issue.getCreatedAt(),
				issue.getUpdatedAt(),
				privileged ? issue.getCallerName() : null,
				privileged ? issue.getCallerPhone() : null,
				commentDtos,
				attachmentDtos);
	}

	/** Activity log (issue_events) for an accessible issue, newest first (AC4). */
	@Transactional(readOnly = true)
	public List<IssueActivityResponse> getActivity(Long issueId, Long currentUserId, boolean privileged) {
		accessGuard.requireAccessible(issueId, currentUserId, privileged);
		List<IssueEvent> events = issueEventRepository.findByIssueIdOrderByCreatedAtDesc(issueId);
		List<Long> actorIds = events.stream().map(IssueEvent::getActorId).distinct().toList();
		Map<Long, String> actorNames = userRepository.findAllById(actorIds).stream()
				.collect(Collectors.toMap(User::getId, User::getDisplayName));
		return events.stream()
				.map(e -> new IssueActivityResponse(
						e.getId(), e.getEventType(), actorNames.get(e.getActorId()),
						e.getFromValue(), e.getToValue(), e.getCreatedAt()))
				.toList();
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
