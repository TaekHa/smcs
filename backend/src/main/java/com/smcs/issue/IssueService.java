package com.smcs.issue;

import com.smcs.crypto.HmacHasher;
import com.smcs.issue.dto.CreateIssueRequest;
import com.smcs.issue.dto.IssueResponse;
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

	public IssueService(IssueRepository issueRepository, IssueEventRepository issueEventRepository,
			HmacHasher hmacHasher, UserRepository userRepository, IssueAccessGuard accessGuard) {
		this.issueRepository = issueRepository;
		this.issueEventRepository = issueEventRepository;
		this.hmacHasher = hmacHasher;
		this.userRepository = userRepository;
		this.accessGuard = accessGuard;
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
		issue.transitionTo(to);
		issueRepository.save(issue);
		IssueEventType eventType = to == IssueStatus.DONE
				? IssueEventType.RESOLVED : IssueEventType.STATUS_CHANGED;
		issueEventRepository.save(new IssueEvent(issueId, actorId, eventType, from.name(), to.name()));
	}
}
