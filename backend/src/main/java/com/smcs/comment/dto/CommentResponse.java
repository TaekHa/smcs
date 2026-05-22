package com.smcs.comment.dto;

import com.smcs.comment.CommentKind;
import java.time.Instant;

/** Comment row for the issue detail. {@code authorName} = display name only (no PII). */
public record CommentResponse(
		Long id,
		String authorName,
		String body,
		CommentKind kind,
		Instant createdAt) {
}
