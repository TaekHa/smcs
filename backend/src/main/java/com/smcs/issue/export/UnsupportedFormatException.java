package com.smcs.issue.export;

/** Thrown when {@code GET /api/issues/export?format=} is anything other than {@code csv} (Deviation #9). */
public class UnsupportedFormatException extends RuntimeException {

	private final String format;

	public UnsupportedFormatException(String format) {
		super("Unsupported export format: " + format);
		this.format = format;
	}

	public String getFormat() {
		return format;
	}
}
