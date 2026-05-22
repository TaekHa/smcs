package com.smcs.attachment;

/** Thrown when an upload is not a valid JPEG/PNG (MIME or magic-byte mismatch). 400 {@code INVALID_IMAGE}. */
public class InvalidImageException extends RuntimeException {

	public InvalidImageException(String message) {
		super(message);
	}
}
