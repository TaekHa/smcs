package com.smcs.notification;

/**
 * In-app notification type (matches the {@code notifications.kind} CHECK constraint).
 * Issue-scoped: {@link #ISSUE_ASSIGNED}/{@link #ISSUE_COMMENTED}/{@link #ISSUE_STATUS_CHANGED}/{@link #ISSUE_REOPENED} (V2; reopen from Story 2.7).
 * Report-scoped: {@link #REPORT_READY}/{@link #REPORT_FAILED} (V7 — Story 3.4, system events, issue_id is NULL).
 */
public enum NotificationKind {
	ISSUE_ASSIGNED, ISSUE_COMMENTED, ISSUE_STATUS_CHANGED, ISSUE_REOPENED,
	REPORT_READY, REPORT_FAILED
}
