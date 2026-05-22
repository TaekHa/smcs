package com.smcs.notification;

/**
 * Thrown when a notification does not exist or is not owned by the caller (no info leak).
 * Mapped to 404 {@code NOTIFICATION_NOT_FOUND}.
 */
public class NotificationNotFoundException extends RuntimeException {

	public NotificationNotFoundException(Long id) {
		super("notification not found: " + id);
	}
}
