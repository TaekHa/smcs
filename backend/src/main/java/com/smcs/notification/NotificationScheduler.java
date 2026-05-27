package com.smcs.notification;

import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/**
 * Triggers the daily notification cleanup job (Story 4.1 AC8 — 90-day retention per
 * architecture §5.5). Time-aware shell — the heavy lifting lives in
 * {@link NotificationCleanupService}. Cron expression and retention window are configurable.
 *
 * <p>Default: 03:30 KST every day — 30 minutes after {@code ReportScheduler.cleanupJob} (03:00 KST)
 * so the two daily cleanup jobs do not overlap on shared DB/disk capacity.
 */
@Component
public class NotificationScheduler {

	private static final ZoneId KST = ZoneId.of("Asia/Seoul");

	private final NotificationCleanupService cleanupService;
	private final int retentionDays;

	public NotificationScheduler(NotificationCleanupService cleanupService,
			@Value("${smcs.notifications.retention-days:90}") int retentionDays) {
		this.cleanupService = cleanupService;
		this.retentionDays = retentionDays;
	}

	@Scheduled(cron = "${smcs.notifications.cleanup-cron:0 30 3 * * *}", zone = "Asia/Seoul")
	public void cleanupJob() {
		Instant cutoff = LocalDate.now(KST).minusDays(retentionDays).atStartOfDay(KST).toInstant();
		cleanupService.cleanupExpired(cutoff);
	}
}
