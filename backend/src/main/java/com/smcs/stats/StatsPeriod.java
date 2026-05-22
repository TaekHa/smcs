package com.smcs.stats;

import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.ZoneId;
import java.time.temporal.TemporalAdjusters;

/**
 * Dashboard period filter. Resolves to a KST {@code [from, to)} instant range so UTC-stored
 * timestamps are filtered on KST day/week/month boundaries (PRD §5.9, §4.5).
 * Range resolution is pure (takes the reference KST date) — unit tested without a DB (AC5).
 */
public enum StatsPeriod {
	TODAY,
	WEEK,
	MONTH;

	private static final ZoneId KST = ZoneId.of("Asia/Seoul");

	/** Case-insensitive parse of the {@code today|week|month} query value. */
	public static StatsPeriod from(String value) {
		if (value == null) {
			throw new IllegalArgumentException("period is required");
		}
		return StatsPeriod.valueOf(value.trim().toUpperCase());
	}

	/** KST {@code [from, to)} for this period relative to {@code todayKst}. */
	public StatsRange rangeFor(LocalDate todayKst) {
		LocalDate fromDate;
		LocalDate toDate;
		switch (this) {
			case TODAY -> {
				fromDate = todayKst;
				toDate = todayKst.plusDays(1);
			}
			case WEEK -> {
				fromDate = todayKst.with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY));
				toDate = fromDate.plusWeeks(1);
			}
			case MONTH -> {
				fromDate = todayKst.withDayOfMonth(1);
				toDate = fromDate.plusMonths(1);
			}
			default -> throw new IllegalStateException("unreachable");
		}
		return new StatsRange(fromDate.atStartOfDay(KST).toInstant(), toDate.atStartOfDay(KST).toInstant());
	}
}
