package com.smcs.attachment.dto;

import com.smcs.attachment.Attachment;
import java.time.Instant;

/**
 * Attachment row for the issue detail. {@code url} follows the secure-serving path
 * convention ({@code /files/{filename}}); the actual X-Accel-Redirect endpoint is Story 2.6.
 */
public record AttachmentResponse(
		Long id,
		String originalName,
		String url,
		String mimeType,
		Long sizeBytes,
		Instant createdAt) {

	public static AttachmentResponse from(Attachment a) {
		return new AttachmentResponse(
				a.getId(),
				a.getOriginalName(),
				"/files/" + a.getFilename(),
				a.getMimeType(),
				a.getSizeBytes(),
				a.getCreatedAt());
	}
}
