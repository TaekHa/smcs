package com.smcs.comment.dto;

import com.smcs.comment.CommentKind;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/**
 * Comment creation payload. Bean Validation; 4000-char ceiling (TEXT column, no DB limit).
 * {@code kind} is optional (default NOTE); mobile field action sends FIELD_ACTION (Story 2.6).
 */
public record AddCommentRequest(
		@NotBlank @Size(max = 4000) String body,
		CommentKind kind) {

	/** Client-settable kind: FIELD_ACTION is honored; anything else (null/SYSTEM) → NOTE (SYSTEM is server-only). */
	public CommentKind resolvedKind() {
		return kind == CommentKind.FIELD_ACTION ? CommentKind.FIELD_ACTION : CommentKind.NOTE;
	}
}
