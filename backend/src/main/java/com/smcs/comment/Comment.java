package com.smcs.comment;

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

/** Maps the {@code comments} table (V2). Story 2.3 writes NOTE comments from the desktop detail. */
@Entity
@Table(name = "comments")
public class Comment {

	@Id
	@GeneratedValue(strategy = GenerationType.IDENTITY)
	private Long id;

	@Column(name = "issue_id", nullable = false)
	private Long issueId;

	@Column(name = "author_id", nullable = false)
	private Long authorId;

	@Column(columnDefinition = "text", nullable = false)
	private String body;

	@Enumerated(EnumType.STRING)
	@Column(nullable = false, length = 15)
	private CommentKind kind;

	@Column(name = "created_at", nullable = false, updatable = false)
	private Instant createdAt;

	protected Comment() {
	}

	public Comment(Long issueId, Long authorId, String body, CommentKind kind) {
		this.issueId = issueId;
		this.authorId = authorId;
		this.body = body;
		this.kind = kind;
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

	public Long getAuthorId() {
		return authorId;
	}

	public String getBody() {
		return body;
	}

	public CommentKind getKind() {
		return kind;
	}

	public Instant getCreatedAt() {
		return createdAt;
	}
}
