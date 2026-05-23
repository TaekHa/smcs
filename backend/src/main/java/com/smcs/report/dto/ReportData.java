package com.smcs.report.dto;

import com.smcs.issue.Priority;
import com.smcs.stats.dto.DashboardStats;
import java.util.List;

/**
 * Composed input for PDF rendering (Story 3.3). Combines period metadata, the shared
 * {@link DashboardStats} (Story 3.1 §5.9 — single source of aggregates), and the current
 * snapshot of open issues (the list isn't expressible from {@code openCount} alone).
 *
 * <p>TD-2: {@code openIssues} carries at most {@code OPEN_LIST_MAX + 1} rows (paged), while
 * {@code totalOpenCount} is the true total — the renderer uses it for an accurate footnote
 * ("이하 N건 생략") regardless of the paged slice size.
 */
public record ReportData(
		ReportKind kind,
		String periodKey,
		String displayPeriod,
		DashboardStats stats,
		List<OpenIssueRow> openIssues,
		long totalOpenCount) {

	public record OpenIssueRow(Long id, String title, Priority priority, String assigneeName) {}
}
