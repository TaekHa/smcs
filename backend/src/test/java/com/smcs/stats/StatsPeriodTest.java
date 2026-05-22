package com.smcs.stats;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.DayOfWeek;
import java.time.Duration;
import java.time.LocalDate;
import java.time.ZoneId;
import org.junit.jupiter.api.Test;

/** Pure range-resolution + parsing tests (AC5) — no Spring/DB. */
class StatsPeriodTest {

	private static final ZoneId KST = ZoneId.of("Asia/Seoul");

	@Test
	void todayIsTheKstCalendarDay() {
		LocalDate today = LocalDate.of(2026, 5, 22);
		StatsRange r = StatsPeriod.TODAY.rangeFor(today);

		assertThat(r.from()).isEqualTo(LocalDate.of(2026, 5, 22).atStartOfDay(KST).toInstant());
		assertThat(r.to()).isEqualTo(LocalDate.of(2026, 5, 23).atStartOfDay(KST).toInstant());
	}

	@Test
	void monthIsTheKstCalendarMonth() {
		LocalDate today = LocalDate.of(2026, 5, 22);
		StatsRange r = StatsPeriod.MONTH.rangeFor(today);

		assertThat(r.from()).isEqualTo(LocalDate.of(2026, 5, 1).atStartOfDay(KST).toInstant());
		assertThat(r.to()).isEqualTo(LocalDate.of(2026, 6, 1).atStartOfDay(KST).toInstant());
	}

	@Test
	void weekStartsOnIsoMondayAndSpansSevenKstDays() {
		LocalDate today = LocalDate.of(2026, 5, 22);
		StatsRange r = StatsPeriod.WEEK.rangeFor(today);

		assertThat(r.from().atZone(KST).getDayOfWeek()).isEqualTo(DayOfWeek.MONDAY);
		assertThat(r.from()).isBeforeOrEqualTo(today.atStartOfDay(KST).toInstant());
		assertThat(today.atStartOfDay(KST).toInstant()).isBefore(r.to());
		assertThat(Duration.between(r.from(), r.to())).isEqualTo(Duration.ofDays(7));
	}

	@Test
	void fromParsesCaseInsensitively() {
		assertThat(StatsPeriod.from("today")).isEqualTo(StatsPeriod.TODAY);
		assertThat(StatsPeriod.from("WEEK")).isEqualTo(StatsPeriod.WEEK);
		assertThat(StatsPeriod.from(" Month ")).isEqualTo(StatsPeriod.MONTH);
	}

	@Test
	void fromRejectsInvalidValues() {
		assertThatThrownBy(() -> StatsPeriod.from("year")).isInstanceOf(IllegalArgumentException.class);
		assertThatThrownBy(() -> StatsPeriod.from(null)).isInstanceOf(IllegalArgumentException.class);
	}
}
