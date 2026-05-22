package com.smcs.issue;

import com.smcs.crypto.EncryptedStringConverter;
import jakarta.persistence.Column;
import jakarta.persistence.Convert;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.PrePersist;
import jakarta.persistence.PreUpdate;
import jakarta.persistence.Table;
import java.time.Instant;

/**
 * Maps the {@code issues} table (V2). Caller PII columns are {@code bytea} and
 * transparently AES-GCM encrypted via {@link EncryptedStringConverter};
 * {@code caller_phone_hash} is set by the service (HMAC, one-way).
 */
@Entity
@Table(name = "issues")
public class Issue {

	@Id
	@GeneratedValue(strategy = GenerationType.IDENTITY)
	private Long id;

	@Column(nullable = false, length = 200)
	private String title;

	@Column(columnDefinition = "text")
	private String description;

	@Convert(converter = EncryptedStringConverter.class)
	@Column(name = "caller_name_enc")
	private String callerName;

	@Convert(converter = EncryptedStringConverter.class)
	@Column(name = "caller_phone_enc")
	private String callerPhone;

	@Column(name = "caller_phone_hash", length = 64)
	private String callerPhoneHash;

	@Column(name = "category_l1_id", nullable = false)
	private Long categoryL1Id;

	@Column(name = "category_l2_id", nullable = false)
	private Long categoryL2Id;

	@Column(name = "category_l3_id", nullable = false)
	private Long categoryL3Id;

	@Enumerated(EnumType.STRING)
	@Column(nullable = false, length = 10)
	private Priority priority;

	@Enumerated(EnumType.STRING)
	@Column(nullable = false, length = 15)
	private IssueStatus status;

	@Column(name = "created_by", nullable = false)
	private Long createdBy;

	@Column(name = "assigned_to")
	private Long assignedTo;

	@Column(name = "resolved_at")
	private Instant resolvedAt;

	@Column(name = "created_at", nullable = false, updatable = false)
	private Instant createdAt;

	@Column(name = "updated_at", nullable = false)
	private Instant updatedAt;

	protected Issue() {
	}

	/** Creates a NEW issue (no assignee — assignment is Story 2.4). */
	public Issue(String title, String description, String callerName, String callerPhone,
			String callerPhoneHash, Long categoryL1Id, Long categoryL2Id, Long categoryL3Id,
			Priority priority, Long createdBy) {
		this.title = title;
		this.description = description;
		this.callerName = callerName;
		this.callerPhone = callerPhone;
		this.callerPhoneHash = callerPhoneHash;
		this.categoryL1Id = categoryL1Id;
		this.categoryL2Id = categoryL2Id;
		this.categoryL3Id = categoryL3Id;
		this.priority = priority;
		this.status = IssueStatus.NEW;
		this.createdBy = createdBy;
	}

	@PrePersist
	void onCreate() {
		Instant now = Instant.now();
		this.createdAt = now;
		this.updatedAt = now;
		if (this.status == null) {
			this.status = IssueStatus.NEW;
		}
	}

	@PreUpdate
	void onUpdate() {
		this.updatedAt = Instant.now();
	}

	/** Assigns a field worker; a NEW issue auto-transitions to ASSIGNED (Story 2.4 AC2). */
	public void assign(Long assigneeId) {
		this.assignedTo = assigneeId;
		if (this.status == IssueStatus.NEW) {
			this.status = IssueStatus.ASSIGNED;
		}
	}

	/**
	 * Moves to {@code next}. DONE stamps {@code resolvedAt} (Story 2.4); reopening to
	 * IN_PROGRESS clears it — no longer resolved (Story 2.7).
	 */
	public void transitionTo(IssueStatus next) {
		this.status = next;
		if (next == IssueStatus.DONE) {
			this.resolvedAt = Instant.now();
		} else if (next == IssueStatus.IN_PROGRESS) {
			this.resolvedAt = null;
		}
	}

	public Long getId() {
		return id;
	}

	public String getTitle() {
		return title;
	}

	public String getDescription() {
		return description;
	}

	public String getCallerName() {
		return callerName;
	}

	public String getCallerPhone() {
		return callerPhone;
	}

	public String getCallerPhoneHash() {
		return callerPhoneHash;
	}

	public Long getCategoryL1Id() {
		return categoryL1Id;
	}

	public Long getCategoryL2Id() {
		return categoryL2Id;
	}

	public Long getCategoryL3Id() {
		return categoryL3Id;
	}

	public Priority getPriority() {
		return priority;
	}

	public IssueStatus getStatus() {
		return status;
	}

	public Long getCreatedBy() {
		return createdBy;
	}

	public Long getAssignedTo() {
		return assignedTo;
	}

	public Instant getResolvedAt() {
		return resolvedAt;
	}

	public Instant getCreatedAt() {
		return createdAt;
	}

	public Instant getUpdatedAt() {
		return updatedAt;
	}
}
