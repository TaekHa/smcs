package com.smcs.report;

import com.smcs.report.dto.ReportKind;
import com.smcs.stats.StatsRange;
import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.ZoneId;
import java.time.temporal.IsoFields;
import java.time.temporal.TemporalAdjusters;

/**
 * Resolves a report's {@code date}/{@code week} parameter to a KST {@code [from, to)} instant
 * range, a stable {@code periodKey} (idempotent file naming, Story 3.4 V6), and a human-readable
 * {@code displayPeriod}. Pure logic — unit tested without a DB (Story 3.3 AC6).
 */
public final class ReportPeriod {

	private static final ZoneId KST = ZoneId.of("Asia/Seoul");

	private final ReportKind kind;
	private final String periodKey;
	private final String displayPeriod;
	private final StatsRange range;
	private final int weekBasedYear;
	private final int weekOfYear;

	private ReportPeriod(ReportKind kind, String periodKey, String displayPeriod, StatsRange range,
			int weekBasedYear, int weekOfYear) {
		this.kind = kind;
		this.periodKey = periodKey;
		this.displayPeriod = displayPeriod;
		this.range = range;
		this.weekBasedYear = weekBasedYear;
		this.weekOfYear = weekOfYear;
	}

	public static ReportPeriod forDate(LocalDate date) {
		if (date == null) {
			throw new IllegalArgumentException("date is required");
		}
		StatsRange range = new StatsRange(
				date.atStartOfDay(KST).toInstant(),
				date.plusDays(1).atStartOfDay(KST).toInstant());
		return new ReportPeriod(ReportKind.DAILY, date.toString(), date.toString(), range, 0, 0);
	}

	public static ReportPeriod forWeek(int weekBasedYear, int weekOfYear) {
		LocalDate monday;
		try {
			monday = LocalDate.now(KST)
					.with(IsoFields.WEEK_BASED_YEAR, weekBasedYear)
					.with(IsoFields.WEEK_OF_WEEK_BASED_YEAR, weekOfYear)
					.with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY));
		} catch (java.time.DateTimeException e) {
			throw new IllegalArgumentException("invalid ISO week: " + weekBasedYear + "-W" + weekOfYear, e);
		}
		LocalDate sunday = monday.plusDays(6);
		StatsRange range = new StatsRange(
				monday.atStartOfDay(KST).toInstant(),
				monday.plusWeeks(1).atStartOfDay(KST).toInstant());
		String key = String.format("%d-W%02d", weekBasedYear, weekOfYear);
		String display = monday + " ~ " + sunday;
		return new ReportPeriod(ReportKind.WEEKLY, key, display, range, weekBasedYear, weekOfYear);
	}

	/** Parses the ISO week query value {@code YYYY-Www} (e.g. {@code 2026-W21}). */
	public static ReportPeriod parseWeek(String value) {
		if (value == null || value.isBlank()) {
			throw new IllegalArgumentException("week is required");
		}
		int dash = value.indexOf('-');
		if (dash <= 0 || dash + 2 >= value.length() || value.charAt(dash + 1) != 'W') {
			throw new IllegalArgumentException("invalid week format: " + value + " (expected YYYY-Www)");
		}
		try {
			int year = Integer.parseInt(value.substring(0, dash));
			int week = Integer.parseInt(value.substring(dash + 2));
			return forWeek(year, week);
		} catch (NumberFormatException e) {
			throw new IllegalArgumentException("invalid week format: " + value, e);
		}
	}

	public ReportKind kind() {
		return kind;
	}

	public String periodKey() {
		return periodKey;
	}

	public String displayPeriod() {
		return displayPeriod;
	}

	public StatsRange range() {
		return range;
	}

	/** Only meaningful when {@link #kind()} is {@code WEEKLY}; otherwise {@code 0}. */
	public int weekBasedYear() {
		return weekBasedYear;
	}

	/** Only meaningful when {@link #kind()} is {@code WEEKLY}; otherwise {@code 0}. */
	public int weekOfYear() {
		return weekOfYear;
	}
}
