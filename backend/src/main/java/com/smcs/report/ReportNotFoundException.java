package com.smcs.report;

/** Thrown when a report id (Story 3.5 file/archive endpoints) doesn't exist. */
public class ReportNotFoundException extends RuntimeException {

	public ReportNotFoundException(Long id) {
		super("report not found: " + id);
	}
}
