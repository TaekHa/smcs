package com.smcs.attachment;

/** Thrown when a requested file path has no attachment row. 404 {@code ATTACHMENT_NOT_FOUND}. */
public class AttachmentNotFoundException extends RuntimeException {

	public AttachmentNotFoundException(String filename) {
		super("attachment not found: " + filename);
	}
}
