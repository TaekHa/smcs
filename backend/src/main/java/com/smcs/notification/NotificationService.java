package com.smcs.notification;

import com.smcs.issue.Issue;
import com.smcs.issue.IssueStatus;
import com.smcs.notification.dto.NotificationResponse;
import com.smcs.user.User;
import com.smcs.user.UserRepository;
import java.time.Instant;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class NotificationService {

	private final NotificationRepository notificationRepository;
	private final UserRepository userRepository;

	public NotificationService(NotificationRepository notificationRepository, UserRepository userRepository) {
		this.notificationRepository = notificationRepository;
		this.userRepository = userRepository;
	}

	// ── Creation (participates in the caller's issue/comment transaction) ────────

	/** Assignment → notify the new assignee (Story 2.4 backfill). */
	public void onAssigned(Issue issue, Long actorId) {
		create(issue.getAssignedTo(), NotificationKind.ISSUE_ASSIGNED, issue.getId(), actorId,
				actorName(actorId) + "님이 #" + issue.getId() + " 이슈를 배정했습니다");
	}

	/** Comment → notify stakeholders except the author (Story 2.3 backfill). */
	public void onCommented(Issue issue, Long actorId) {
		fanOut(issue, NotificationKind.ISSUE_COMMENTED, actorId,
				actorName(actorId) + "님이 #" + issue.getId() + " 이슈에 코멘트를 남겼습니다");
	}

	/** Status transition → notify stakeholders except the actor (Story 2.4 backfill). */
	public void onStatusChanged(Issue issue, Long actorId, IssueStatus to) {
		fanOut(issue, NotificationKind.ISSUE_STATUS_CHANGED, actorId,
				actorName(actorId) + "님이 #" + issue.getId() + " 이슈 상태를 " + statusLabel(to) + "(으)로 변경했습니다");
	}

	/** Reopen → notify stakeholders except the actor (Story 2.7). */
	public void onReopened(Issue issue, Long actorId) {
		fanOut(issue, NotificationKind.ISSUE_REOPENED, actorId,
				actorName(actorId) + "님이 #" + issue.getId() + " 이슈를 재오픈했습니다");
	}

	/**
	 * Fan-out a system event to every active ADMIN (Story 3.4 — report archive alerts).
	 * No owning issue, no actor. Participates in the caller's transaction.
	 */
	public void notifyAdmins(NotificationKind kind, String message) {
		List<User> admins = userRepository.findByRoleAndActiveTrue(User.Role.ADMIN);
		for (User admin : admins) {
			notificationRepository.save(new Notification(admin.getId(), kind, message));
		}
	}

	/** Stakeholders = {assignee, creator} − actor (no self-notify, deduped). */
	private void fanOut(Issue issue, NotificationKind kind, Long actorId, String message) {
		Set<Long> recipients = new HashSet<>();
		if (issue.getAssignedTo() != null) {
			recipients.add(issue.getAssignedTo());
		}
		recipients.add(issue.getCreatedBy());
		recipients.forEach(r -> create(r, kind, issue.getId(), actorId, message));
	}

	private void create(Long recipientId, NotificationKind kind, Long issueId, Long actorId, String message) {
		if (recipientId == null || recipientId.equals(actorId)) {
			return; // never notify the actor about their own action
		}
		notificationRepository.save(new Notification(recipientId, kind, issueId, actorId, message));
	}

	// ── Read / mark (own transactions) ──────────────────────────────────────────

	@Transactional(readOnly = true)
	public Page<NotificationResponse> list(Long userId, Pageable pageable) {
		Page<Notification> page = notificationRepository.findByRecipientIdOrderByCreatedAtDesc(userId, pageable);
		List<Long> actorIds = page.getContent().stream()
				.map(Notification::getActorId).filter(Objects::nonNull).distinct().toList();
		Map<Long, String> names = userRepository.findAllById(actorIds).stream()
				.collect(Collectors.toMap(User::getId, User::getDisplayName));
		return page.map(n -> new NotificationResponse(
				n.getId(), n.getKind(), n.getIssueId(),
				n.getActorId() == null ? null : names.get(n.getActorId()),
				n.getMessage(), n.getReadAt(), n.getCreatedAt()));
	}

	@Transactional(readOnly = true)
	public long unreadCount(Long userId) {
		return notificationRepository.countByRecipientIdAndReadAtIsNull(userId);
	}

	@Transactional
	public void markRead(Long userId, Long notificationId) {
		Notification n = notificationRepository.findById(notificationId)
				.orElseThrow(() -> new NotificationNotFoundException(notificationId));
		if (!n.getRecipientId().equals(userId)) {
			throw new NotificationNotFoundException(notificationId); // not owned → 404, no info leak
		}
		n.markRead();
	}

	@Transactional
	public void markAllRead(Long userId) {
		notificationRepository.markAllRead(userId, Instant.now());
	}

	private String actorName(Long actorId) {
		if (actorId == null) {
			return "시스템";
		}
		return userRepository.findById(actorId).map(User::getDisplayName).orElse("시스템");
	}

	private String statusLabel(IssueStatus status) {
		return switch (status) {
			case NEW -> "신규";
			case ASSIGNED -> "배정";
			case IN_PROGRESS -> "진행중";
			case DONE -> "완료";
			case VERIFIED -> "검수";
		};
	}
}
