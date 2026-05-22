package com.smcs.stats.dto;

import com.smcs.issue.Priority;
import java.time.LocalDate;
import java.util.List;

/**
 * Read-only aggregate shared by the dashboard (Story 3.2) and PDF reports (Story 3.3).
 * Not a persisted entity — {@code StatsService} computes it from issue data (PRD §5.9).
 */
public record DashboardStats(
		Kpi kpi,
		List<CategoryCount> byCategory,
		List<AssigneeCount> byAssignee,
		List<PriorityCount> byPriority,
		List<TrendPoint> trend) {

	/** {@code openCount} is the current snapshot (period-independent); the rest are within the period. */
	public record Kpi(long newCount, long resolvedCount, long openCount, long avgResolveMinutes) {}

	public record CategoryCount(String name, long count) {}

	public record AssigneeCount(String name, long resolved) {}

	public record PriorityCount(Priority priority, long count) {}

	public record TrendPoint(LocalDate date, long newCount, long resolvedCount) {}
}
