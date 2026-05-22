package com.smcs.notification;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.PrePersist;
import jakarta.persistence.Table;
import java.time.Instant;

/** Maps the {@code notifications} table (V2). Created in-transaction by issue activity (Story 2.8). */
@Entity
@Table(name = "notifications")
public class Notification {

	@Id
	@GeneratedValue(strategy = GenerationType.IDENTITY)
	private Long id;

	@Column(name = "recipient_id", nullable = false)
	private Long recipientId;

	@Enumerated(EnumType.STRING)
	@Column(nullable = false, length = 30)
	private NotificationKind kind;

	@Column(name = "issue_id", nullable = false)
	private Long issueId;

	@Column(name = "actor_id")
	private Long actorId;

	@Column(nullable = false, length = 255)
	private String message;

	@Column(name = "read_at")
	private Instant readAt;

	@Column(name = "created_at", nullable = false, updatable = false)
	private Instant createdAt;

	protected Notification() {
	}

	public Notification(Long recipientId, NotificationKind kind, Long issueId, Long actorId, String message) {
		this.recipientId = recipientId;
		this.kind = kind;
		this.issueId = issueId;
		this.actorId = actorId;
		this.message = message;
	}

	@PrePersist
	void onCreate() {
		this.createdAt = Instant.now();
	}

	/** Marks the notification read (idempotent — keeps the first read time). */
	public void markRead() {
		if (this.readAt == null) {
			this.readAt = Instant.now();
		}
	}

	public Long getId() {
		return id;
	}

	public Long getRecipientId() {
		return recipientId;
	}

	public NotificationKind getKind() {
		return kind;
	}

	public Long getIssueId() {
		return issueId;
	}

	public Long getActorId() {
		return actorId;
	}

	public String getMessage() {
		return message;
	}

	public Instant getReadAt() {
		return readAt;
	}

	public Instant getCreatedAt() {
		return createdAt;
	}
}
