package com.smcs.issue.dto;

import com.smcs.issue.Issue;
import com.smcs.issue.IssueStatus;
import com.smcs.issue.Priority;
import java.time.Instant;

/** Minimal echo for post-create navigation (no caller plaintext — see Story 2.3 for detail). */
public record IssueResponse(
		Long id,
		String title,
		Priority priority,
		IssueStatus status,
		Instant createdAt) {

	public static IssueResponse from(Issue issue) {
		return new IssueResponse(
				issue.getId(),
				issue.getTitle(),
				issue.getPriority(),
				issue.getStatus(),
				issue.getCreatedAt());
	}
}
