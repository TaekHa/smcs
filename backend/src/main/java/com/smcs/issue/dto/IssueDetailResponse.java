package com.smcs.issue.dto;

import com.smcs.attachment.dto.AttachmentResponse;
import com.smcs.comment.dto.CommentResponse;
import com.smcs.issue.IssueStatus;
import com.smcs.issue.Priority;
import java.time.Instant;
import java.util.List;

/**
 * Full issue detail. {@code callerName}/{@code callerPhone} carry decrypted PII and are
 * non-null only for privileged (AGENT/ADMIN) requests; null for FIELD (Deviation #2).
 */
public record IssueDetailResponse(
		Long id,
		String title,
		String description,
		CategoryRef categoryL1,
		CategoryRef categoryL2,
		CategoryRef categoryL3,
		Priority priority,
		IssueStatus status,
		String createdByName,
		String assigneeName,
		Instant resolvedAt,
		Instant createdAt,
		Instant updatedAt,
		String callerName,
		String callerPhone,
		List<CommentResponse> comments,
		List<AttachmentResponse> attachments) {
}
