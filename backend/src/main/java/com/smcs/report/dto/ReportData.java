package com.smcs.report.dto;

import com.smcs.issue.Priority;
import com.smcs.stats.dto.DashboardStats;
import java.util.List;

/**
 * Composed input for PDF rendering (Story 3.3). Combines period metadata, the shared
 * {@link DashboardStats} (Story 3.1 §5.9 — single source of aggregates), and the current
 * snapshot of open issues (the list isn't expressible from {@code openCount} alone).
 */
public record ReportData(
		ReportKind kind,
		String periodKey,
		String displayPeriod,
		DashboardStats stats,
		List<OpenIssueRow> openIssues) {

	public record OpenIssueRow(Long id, String title, Priority priority, String assigneeName) {}
}
