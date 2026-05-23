package com.smcs.report;

import java.time.LocalDate;
import java.time.ZoneId;
import java.time.temporal.IsoFields;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/**
 * Triggers the daily/weekly report archive jobs (Story 3.4 AC1, AC2). Time-aware shell — all
 * logic lives in {@link ReportArchiveService} so the heavy lifting is unit-testable without a
 * clock. Cron expressions are configurable (AC5) via {@code smcs.reports.daily-cron} /
 * {@code smcs.reports.weekly-cron}; both default to 08:00 KST.
 */
@Component
public class ReportScheduler {

	private static final ZoneId KST = ZoneId.of("Asia/Seoul");

	private final ReportArchiveService archiveService;

	public ReportScheduler(ReportArchiveService archiveService) {
		this.archiveService = archiveService;
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
}
