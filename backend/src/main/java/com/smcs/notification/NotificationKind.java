package com.smcs.notification;

/**
 * In-app notification type (matches the {@code notifications.kind} CHECK constraint, V2).
 * {@link #ISSUE_REOPENED} is produced by the reopen transition (Story 2.7).
 */
public enum NotificationKind {
	ISSUE_ASSIGNED, ISSUE_COMMENTED, ISSUE_STATUS_CHANGED, ISSUE_REOPENED
}
