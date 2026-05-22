package com.smcs.issue;

import com.smcs.comment.Comment;
import com.smcs.comment.CommentKind;
import com.smcs.comment.CommentRepository;
import com.smcs.crypto.HmacHasher;
import com.smcs.issue.dto.CreateIssueRequest;
import com.smcs.issue.dto.IssueResponse;
import com.smcs.notification.NotificationService;
import com.smcs.user.User;
import com.smcs.user.UserRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class IssueService {

	private final IssueRepository issueRepository;
	private final IssueEventRepository issueEventRepository;
	private final HmacHasher hmacHasher;
	private final UserRepository userRepository;
	private final IssueAccessGuard accessGuard;
	private final NotificationService notificationService;
	private final CommentRepository commentRepository;

	public IssueService(IssueRepository issueRepository, IssueEventRepository issueEventRepository,
			HmacHasher hmacHasher, UserRepository userRepository, IssueAccessGuard accessGuard,
			NotificationService notificationService, CommentRepository commentRepository) {
		this.issueRepository = issueRepository;
		this.issueEventRepository = issueEventRepository;
		this.hmacHasher = hmacHasher;
		this.userRepository = userRepository;
		this.accessGuard = accessGuard;
		this.notificationService = notificationService;
		this.commentRepository = commentRepository;
	}

	/**
	 * Creates a NEW issue and its CREATED event in one transaction.
	 * Caller name/phone are encrypted by the JPA converter on persist;
	 * the phone hash is computed here for exact-match search (Story 2.2).
	 */
	@Transactional
	public IssueResponse create(CreateIssueRequest req, Long currentUserId) {
		String phoneHash = hmacHasher.hashPhone(req.callerPhone());
		Issue issue = new Issue(
				req.title(),
				req.description(),
				req.callerName(),
				req.callerPhone(),
				phoneHash,
				req.categoryL1Id(),
				req.categoryL2Id(),
				req.categoryL3Id(),
				req.priority(),
				currentUserId);
		Issue saved = issueRepository.save(issue);
		issueEventRepository.save(
				new IssueEvent(saved.getId(), currentUserId, IssueEventType.CREATED, null, null));
		return IssueResponse.from(saved);
	}

	/**
	 * Assigns an active FIELD user; a NEW issue auto-transitions to ASSIGNED (AC1, AC2).
	 * Records an ASSIGNED event. Reassignment keeps a non-NEW status (Deviation #7).
	 */
	@Transactional
	public void assign(Long issueId, Long assigneeId, Long actorId) {
		Issue issue = issueRepository.findById(issueId)
				.orElseThrow(() -> new IssueNotFoundException(issueId));
		User assignee = userRepository.findById(assigneeId)
				.orElseThrow(() -> new InvalidAssigneeException(assigneeId));
		if (assignee.getRole() != User.Role.FIELD || !assignee.isActive()) {
			throw new InvalidAssigneeException(assigneeId);
		}
		IssueStatus from = issue.getStatus();
		issue.assign(assigneeId);
		issueRepository.save(issue);
		issueEventRepository.save(new IssueEvent(
				issueId, actorId, IssueEventType.ASSIGNED, from.name(), issue.getStatus().name()));
		notificationService.onAssigned(issue, actorId);
	}

	/**
	 * Transitions an accessible issue along a valid edge (AC3, AC5). Transitioning to DONE
	 * records a RESOLVED event (and stamps resolvedAt); other moves record STATUS_CHANGED.
	 */
	@Transactional
	public void transition(Long issueId, IssueStatus to, Long actorId, boolean privileged, String reason) {
		Issue issue = accessGuard.requireAccessible(issueId, actorId, privileged);
		IssueStatus from = issue.getStatus();
		if (!from.canTransitionTo(to)) {
			throw new IssueTransitionException(from, to);
		}
		// 검수/재오픈(DONE 출발)은 AGENT/ADMIN 전용 (§6.3, Story 2.7); owner-FIELD 허용은 전진 전이만.
		if (from == IssueStatus.DONE && !privileged) {
			throw new IssueForbiddenException(issueId);
		}
		boolean reopen = from == IssueStatus.DONE && to == IssueStatus.IN_PROGRESS;
		if (reopen && (reason == null || reason.isBlank())) {
			throw new ReopenReasonRequiredException(issueId); // AC2
		}

		issue.transitionTo(to);
		issueRepository.save(issue);

		if (reopen) {
			// Record the reason as a comment → comment section + COMMENTED activity-log entry (AC2/AC3).
			commentRepository.save(new Comment(issueId, actorId, "재오픈 사유: " + reason.trim(), CommentKind.NOTE));
			issueEventRepository.save(new IssueEvent(issueId, actorId, IssueEventType.COMMENTED, null, null));
		}
		IssueEventType eventType = to == IssueStatus.DONE
				? IssueEventType.RESOLVED : IssueEventType.STATUS_CHANGED;
		issueEventRepository.save(new IssueEvent(issueId, actorId, eventType, from.name(), to.name()));

		if (reopen) {
			notificationService.onReopened(issue, actorId);
		} else {
			notificationService.onStatusChanged(issue, actorId, to);
		}
	}
}
