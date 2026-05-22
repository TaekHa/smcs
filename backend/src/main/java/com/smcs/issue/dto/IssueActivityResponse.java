package com.smcs.issue.dto;

import com.smcs.issue.IssueEventType;
import java.time.Instant;

/** Activity-log row (issue_events). {@code actorName} = display name only. */
public record IssueActivityResponse(
		Long id,
		IssueEventType eventType,
		String actorName,
		String fromValue,
		String toValue,
		Instant createdAt) {
}
