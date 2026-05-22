package com.smcs.comment;

import com.smcs.comment.dto.AddCommentRequest;
import com.smcs.comment.dto.CommentResponse;
import com.smcs.issue.Issue;
import com.smcs.issue.IssueAccessGuard;
import com.smcs.issue.IssueEvent;
import com.smcs.issue.IssueEventRepository;
import com.smcs.issue.IssueEventType;
import com.smcs.notification.NotificationService;
import com.smcs.user.User;
import com.smcs.user.UserRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class CommentService {

	private final IssueAccessGuard accessGuard;
	private final CommentRepository commentRepository;
	private final IssueEventRepository issueEventRepository;
	private final UserRepository userRepository;
	private final NotificationService notificationService;

	public CommentService(IssueAccessGuard accessGuard, CommentRepository commentRepository,
			IssueEventRepository issueEventRepository, UserRepository userRepository,
			NotificationService notificationService) {
		this.accessGuard = accessGuard;
		this.commentRepository = commentRepository;
		this.issueEventRepository = issueEventRepository;
		this.userRepository = userRepository;
		this.notificationService = notificationService;
	}

	/**
	 * Adds a NOTE comment and records a COMMENTED event in one transaction (AC3 — the
	 * comment is auto-reflected in the activity log). Ownership is checked first.
	 */
	@Transactional
	public CommentResponse addComment(Long issueId, Long currentUserId, boolean privileged, AddCommentRequest req) {
		Issue issue = accessGuard.requireAccessible(issueId, currentUserId, privileged);
		Comment saved = commentRepository.save(
				new Comment(issueId, currentUserId, req.body(), req.resolvedKind()));
		issueEventRepository.save(
				new IssueEvent(issueId, currentUserId, IssueEventType.COMMENTED, null, null));
		notificationService.onCommented(issue, currentUserId);
		String authorName = userRepository.findById(currentUserId).map(User::getDisplayName).orElse(null);
		return new CommentResponse(
				saved.getId(), authorName, saved.getBody(), saved.getKind(), saved.getCreatedAt());
	}
}
