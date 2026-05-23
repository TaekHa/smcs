import type { Priority } from './issue';

export type StatsPeriod = 'today' | 'week' | 'month';

/**
 * Read-only aggregate shared by the dashboard (Story 3.2) and PDF reports (Story 3.3).
 * Mirrors backend {@code com.smcs.stats.dto.DashboardStats} (PRD §5.9 — single SoT).
 * {@code openCount} is the current snapshot (period-independent); the rest are within the period.
 */
export interface DashboardStats {
  kpi: {
    newCount: number;
    resolvedCount: number;
    openCount: number;
    avgResolveMinutes: number;
  };
  byCategory: { name: string; count: number }[];
  byAssignee: { name: string; resolved: number }[];
  byPriority: { priority: Priority; count: number }[];
  trend: { date: string; newCount: number; resolvedCount: number }[];
}
