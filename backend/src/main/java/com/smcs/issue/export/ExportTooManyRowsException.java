package com.smcs.issue.export;

/**
 * Thrown when an export request would return more than {@link IssueExportService#MAX_ROWS} rows.
 * Story 4.3 Deviation #3 (PO biased v0.2): explicit 400 over silent truncation — users must
 * narrow the filter so they never lose rows without knowing.
 */
public class ExportTooManyRowsException extends RuntimeException {

	private final long actualCount;

	public ExportTooManyRowsException(long actualCount) {
		super("Export would return " + actualCount + " rows (max " + IssueExportService.MAX_ROWS + ").");
		this.actualCount = actualCount;
	}

	public long getActualCount() {
		return actualCount;
	}
}
