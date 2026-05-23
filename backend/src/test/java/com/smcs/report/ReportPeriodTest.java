package com.smcs.report;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.smcs.report.dto.ReportKind;
import com.smcs.stats.StatsRange;
import java.time.DayOfWeek;
import java.time.Duration;
import java.time.LocalDate;
import java.time.ZoneId;
import org.junit.jupiter.api.Test;

/** Pure period-resolution tests (Story 3.3 AC6 — date/week → KST [from, to)). No Spring/DB. */
class ReportPeriodTest {

	private static final ZoneId KST = ZoneId.of("Asia/Seoul");

	@Test
	void forDateCoversTheKstCalendarDay() {
		ReportPeriod p = ReportPeriod.forDate(LocalDate.of(2026, 5, 21));

		assertThat(p.kind()).isEqualTo(ReportKind.DAILY);
		assertThat(p.periodKey()).isEqualTo("2026-05-21");
		assertThat(p.displayPeriod()).isEqualTo("2026-05-21");

		StatsRange r = p.range();
		assertThat(r.from()).isEqualTo(LocalDate.of(2026, 5, 21).atStartOfDay(KST).toInstant());
		assertThat(r.to()).isEqualTo(LocalDate.of(2026, 5, 22).atStartOfDay(KST).toInstant());
	}

	@Test
	void forDateRejectsNull() {
		assertThatThrownBy(() -> ReportPeriod.forDate(null)).isInstanceOf(IllegalArgumentException.class);
	}

	@Test
	void forWeekCoversIsoWeekMondayToSunday() {
		ReportPeriod p = ReportPeriod.forWeek(2026, 21);

		assertThat(p.kind()).isEqualTo(ReportKind.WEEKLY);
		assertThat(p.periodKey()).isEqualTo("2026-W21");

		StatsRange r = p.range();
		assertThat(r.from().atZone(KST).getDayOfWeek()).isEqualTo(DayOfWeek.MONDAY);
		assertThat(Duration.between(r.from(), r.to())).isEqualTo(Duration.ofDays(7));
		assertThat(p.displayPeriod()).contains("~");
	}

	@Test
	void forWeekZeroPadsSingleDigitWeek() {
		ReportPeriod p = ReportPeriod.forWeek(2026, 3);
		assertThat(p.periodKey()).isEqualTo("2026-W03");
	}

	@Test
	void parseWeekAcceptsIsoFormat() {
		ReportPeriod p = ReportPeriod.parseWeek("2026-W21");
		assertThat(p.kind()).isEqualTo(ReportKind.WEEKLY);
		assertThat(p.periodKey()).isEqualTo("2026-W21");
	}

	@Test
	void parseWeekRejectsBadInput() {
		assertThatThrownBy(() -> ReportPeriod.parseWeek(null)).isInstanceOf(IllegalArgumentException.class);
		assertThatThrownBy(() -> ReportPeriod.parseWeek("")).isInstanceOf(IllegalArgumentException.class);
		assertThatThrownBy(() -> ReportPeriod.parseWeek("2026-21")).isInstanceOf(IllegalArgumentException.class);
		assertThatThrownBy(() -> ReportPeriod.parseWeek("2026W21")).isInstanceOf(IllegalArgumentException.class);
		assertThatThrownBy(() -> ReportPeriod.parseWeek("XXXX-Wzz")).isInstanceOf(IllegalArgumentException.class);
		assertThatThrownBy(() -> ReportPeriod.parseWeek("2026-W99")).isInstanceOf(IllegalArgumentException.class);
	}
}
