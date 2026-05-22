package com.smcs.issue;

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

/** Maps the {@code issue_events} table (V2). Story 2.1 records CREATED only. */
@Entity
@Table(name = "issue_events")
public class IssueEvent {

	@Id
	@GeneratedValue(strategy = GenerationType.IDENTITY)
	private Long id;

	@Column(name = "issue_id", nullable = false)
	private Long issueId;

	@Column(name = "actor_id", nullable = false)
	private Long actorId;

	@Enumerated(EnumType.STRING)
	@Column(name = "event_type", nullable = false, length = 30)
	private IssueEventType eventType;

	@Column(name = "from_value", length = 50)
	private String fromValue;

	@Column(name = "to_value", length = 50)
	private String toValue;

	@Column(name = "created_at", nullable = false, updatable = false)
	private Instant createdAt;

	protected IssueEvent() {
	}

	public IssueEvent(Long issueId, Long actorId, IssueEventType eventType, String fromValue, String toValue) {
		this.issueId = issueId;
		this.actorId = actorId;
		this.eventType = eventType;
		this.fromValue = fromValue;
		this.toValue = toValue;
	}

	@PrePersist
	void onCreate() {
		this.createdAt = Instant.now();
	}

	public Long getId() {
		return id;
	}

	public Long getIssueId() {
		return issueId;
	}

	public Long getActorId() {
		return actorId;
	}

	public IssueEventType getEventType() {
		return eventType;
	}

	public String getFromValue() {
		return fromValue;
	}

	public String getToValue() {
		return toValue;
	}

	public Instant getCreatedAt() {
		return createdAt;
	}
}
