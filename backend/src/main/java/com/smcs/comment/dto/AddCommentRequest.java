package com.smcs.comment.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/** Comment creation payload. Bean Validation; 4000-char ceiling (TEXT column, no DB limit). */
public record AddCommentRequest(
		@NotBlank @Size(max = 4000) String body) {
}
