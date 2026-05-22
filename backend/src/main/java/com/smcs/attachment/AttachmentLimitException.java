package com.smcs.attachment;

/** Thrown when an issue already has the max attachments (10). 400 {@code ATTACHMENT_LIMIT}. */
public class AttachmentLimitException extends RuntimeException {

	public AttachmentLimitException(Long issueId) {
		super("attachment limit reached for issue: " + issueId);
	}
}
