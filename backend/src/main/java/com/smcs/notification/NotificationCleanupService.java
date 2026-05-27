package com.smcs.notification;

import java.time.Instant;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Deletes notifications older than the retention cutoff (Story 4.1 AC8 / architecture §5.5 — 90 days).
 * Time-independent — the scheduler computes {@code Instant.now() - retentionDays} and passes it in
 * so the algorithm can be unit-tested without a clock (3.5 {@code ReportCleanupService} precedent).
 */
@Service
public class NotificationCleanupService {

	private static final Logger log = LoggerFactory.getLogger(NotificationCleanupService.class);

	private final NotificationRepository notificationRepository;

	public NotificationCleanupService(NotificationRepository notificationRepository) {
		this.notificationRepository = notificationRepository;
	}

	/** Bulk-deletes every notification with {@code createdAt < cutoff}. Returns the number of rows deleted. */
	@Transactional
	public int cleanupExpired(Instant cutoff) {
		int deleted = notificationRepository.deleteByCreatedAtBefore(cutoff);
		if (deleted > 0) {
			log.info("notification cleanup removed {} row(s) older than {}", deleted, cutoff);
		}
		return deleted;
	}
}
