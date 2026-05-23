package com.smcs.report.dto;

import java.time.Instant;

/**
 * Archive list row (Story 3.5). {@code filePath} is intentionally omitted — clients reach the
 * file only via {@code GET /api/reports/{id}/file}, which prevents path traversal and lets us
 * change the on-disk layout without breaking the API.
 */
public record ReportSummary(
		Long id,
		ReportKind kind,
		String periodKey,
		long sizeBytes,
		Instant createdAt) {
}
