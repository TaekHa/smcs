package com.smcs.report;

import java.time.LocalDate;
import java.time.ZoneId;
import java.time.temporal.IsoFields;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/**
 * Triggers the daily/weekly archive jobs (Story 3.4) and the daily cleanup job (Story 3.5).
 * Time-aware shell — all logic lives in {@link ReportArchiveService} / {@link ReportCleanupService}
 * so the heavy lifting is unit-testable without a clock. Cron expressions are configurable.
 */
@Component
public class ReportScheduler {

	private static final ZoneId KST = ZoneId.of("Asia/Seoul");

	private final ReportArchiveService archiveService;
	private final ReportCleanupService cleanupService;
	private final int retentionDays;

	public ReportScheduler(ReportArchiveService archiveService, ReportCleanupService cleanupService,
			@Value("${smcs.reports.retention-days:90}") int retentionDays) {
		this.archiveService = archiveService;
		this.cleanupService = cleanupService;
		this.retentionDays = retentionDays;
	}

	/** 08:00 KST every day → yesterday's daily report. */
	@Scheduled(cron = "${smcs.reports.daily-cron:0 0 8 * * *}", zone = "Asia/Seoul")
	public void dailyJob() {
		LocalDate yesterday = LocalDate.now(KST).minusDays(1);
		archiveService.generateAndStoreDaily(yesterday);
	}

	/** 08:00 KST every Monday → last week's weekly report (ISO week). */
	@Scheduled(cron = "${smcs.reports.weekly-cron:0 0 8 * * MON}", zone = "Asia/Seoul")
	public void weeklyJob() {
		LocalDate lastWeek = LocalDate.now(KST).minusWeeks(1);
		int year = lastWeek.get(IsoFields.WEEK_BASED_YEAR);
		int week = lastWeek.get(IsoFields.WEEK_OF_WEEK_BASED_YEAR);
		archiveService.generateAndStoreWeekly(year, week);
	}

	/** 03:00 KST every day → delete reports older than {@code retentionDays} (Story 3.5 AC5). */
	@Scheduled(cron = "${smcs.reports.cleanup-cron:0 0 3 * * *}", zone = "Asia/Seoul")
	public void cleanupJob() {
		java.time.Instant cutoff = LocalDate.now(KST).minusDays(retentionDays)
				.atStartOfDay(KST).toInstant();
		cleanupService.cleanupExpired(cutoff);
	}
}
