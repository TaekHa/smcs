package com.smcs.issue.dto;

import com.smcs.issue.IssueStatus;
import com.smcs.issue.Priority;
import java.time.Instant;

/** List-row projection. Caller PII (name/phone) is intentionally excluded (§6.9). */
public record IssueSummary(
		Long id,
		String title,
		String categoryL1Name,
		String categoryL2Name,
		String categoryL3Name,
		Priority priority,
		IssueStatus status,
		String assigneeName,
		Instant createdAt) {
}
