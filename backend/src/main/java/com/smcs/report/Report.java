package com.smcs.report;

import com.smcs.report.dto.ReportKind;
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

/**
 * Maps the {@code reports} table (V6). Metadata for an auto-generated PDF archived under
 * {@code smcs.files.dir/reports/{kind}/{period_key}.pdf}. {@code (kind, period_key)} is unique —
 * re-runs of the same period upsert (file is replaced, metadata updated; {@code createdAt} kept).
 */
@Entity
@Table(name = "reports")
public class Report {

	@Id
	@GeneratedValue(strategy = GenerationType.IDENTITY)
	private Long id;

	@Enumerated(EnumType.STRING)
	@Column(nullable = false, length = 10)
	private ReportKind kind;

	@Column(name = "period_key", nullable = false, length = 10)
	private String periodKey;

	@Column(name = "file_path", nullable = false, length = 200)
	private String filePath;

	@Column(name = "size_bytes", nullable = false)
	private long sizeBytes;

	@Column(name = "created_at", nullable = false, updatable = false)
	private Instant createdAt;

	protected Report() {
	}

	public Report(ReportKind kind, String periodKey, String filePath, long sizeBytes) {
		this.kind = kind;
		this.periodKey = periodKey;
		this.filePath = filePath;
		this.sizeBytes = sizeBytes;
	}

	@PrePersist
	void onCreate() {
		this.createdAt = Instant.now();
	}

	/** Re-runs of the same period replace the file but keep the original {@code createdAt}. */
	public void replaceFile(String filePath, long sizeBytes) {
		this.filePath = filePath;
		this.sizeBytes = sizeBytes;
	}

	public Long getId() {
		return id;
	}

	public ReportKind getKind() {
		return kind;
	}

	public String getPeriodKey() {
		return periodKey;
	}

	public String getFilePath() {
		return filePath;
	}

	public long getSizeBytes() {
		return sizeBytes;
	}

	public Instant getCreatedAt() {
		return createdAt;
	}
}
