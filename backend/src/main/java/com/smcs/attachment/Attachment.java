package com.smcs.attachment;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.PrePersist;
import jakarta.persistence.Table;
import java.time.Instant;

/**
 * Maps the {@code attachments} table (V2). Story 2.3 is read-only (detail display);
 * upload, EXIF strip, and X-Accel serving are Story 2.6.
 */
@Entity
@Table(name = "attachments")
public class Attachment {

	@Id
	@GeneratedValue(strategy = GenerationType.IDENTITY)
	private Long id;

	@Column(name = "issue_id", nullable = false)
	private Long issueId;

	@Column(name = "uploader_id", nullable = false)
	private Long uploaderId;

	@Column(nullable = false, length = 100)
	private String filename;

	@Column(name = "original_name", nullable = false, length = 255)
	private String originalName;

	@Column(name = "mime_type", nullable = false, length = 50)
	private String mimeType;

	@Column(name = "size_bytes", nullable = false)
	private Long sizeBytes;

	@Column(name = "created_at", nullable = false, updatable = false)
	private Instant createdAt;

	protected Attachment() {
	}

	/** Creates a new attachment (Story 2.6 upload). {@code filename} = relative path {@code yyyy/MM/{uuid}.{ext}}. */
	public Attachment(Long issueId, Long uploaderId, String filename, String originalName,
			String mimeType, Long sizeBytes) {
		this.issueId = issueId;
		this.uploaderId = uploaderId;
		this.filename = filename;
		this.originalName = originalName;
		this.mimeType = mimeType;
		this.sizeBytes = sizeBytes;
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

	public Long getUploaderId() {
		return uploaderId;
	}

	public String getFilename() {
		return filename;
	}

	public String getOriginalName() {
		return originalName;
	}

	public String getMimeType() {
		return mimeType;
	}

	public Long getSizeBytes() {
		return sizeBytes;
	}

	public Instant getCreatedAt() {
		return createdAt;
	}
}
