package com.smcs.notification.dto;

import com.smcs.notification.NotificationKind;
import java.time.Instant;

/** Notification row. {@code actorName} = display name only (no PII); null if actor unknown. */
public record NotificationResponse(
		Long id,
		NotificationKind kind,
		Long issueId,
		String actorName,
		String message,
		Instant readAt,
		Instant createdAt) {
}
