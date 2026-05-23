package com.smcs.report;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.smcs.notification.NotificationKind;
import com.smcs.notification.NotificationService;
import com.smcs.report.dto.ReportKind;
import java.time.LocalDate;
import java.util.Optional;
import org.junit.jupiter.api.Test;

/**
 * Unit tests for the archive orchestration (Story 3.4). Dependencies are mocked so the flow can
 * be exercised without a clock, DB, or filesystem. Three scenarios cover the AC surface:
 * fresh insert, idempotent upsert (AC4), and failure → REPORT_FAILED alert (AC7) without re-throw.
 */
class ReportArchiveServiceTest {

	private final ReportService reportService = mock(ReportService.class);
	private final ReportStorageService storageService = mock(ReportStorageService.class);
	private final ReportRepository reportRepository = mock(ReportRepository.class);
	private final NotificationService notificationService = mock(NotificationService.class);

	private final ReportArchiveService service = new ReportArchiveService(
			reportService, storageService, reportRepository, notificationService);

	@Test
	void freshDailyInsertsMetadataAndNotifiesAdmins() {
		LocalDate date = LocalDate.of(2026, 5, 21);
		byte[] pdf = "%PDF-fake".getBytes();
		when(reportService.generateDaily(date)).thenReturn(pdf);
		when(storageService.storeOrReplace(ReportKind.DAILY, "2026-05-21", pdf))
				.thenReturn("reports/DAILY/2026-05-21.pdf");
		when(reportRepository.findByKindAndPeriodKey(ReportKind.DAILY, "2026-05-21"))
				.thenReturn(Optional.empty());

		service.generateAndStoreDaily(date);

		verify(reportRepository, times(1)).save(any(Report.class));
		verify(notificationService).notifyAdmins(eq(NotificationKind.REPORT_READY),
				eq("어제 일간 보고서(2026-05-21)가 준비되었습니다"));
		verify(notificationService, never()).notifyAdmins(eq(NotificationKind.REPORT_FAILED), anyString());
	}

	@Test
	void rerunReplacesExistingMetadataAndFile() {
		LocalDate date = LocalDate.of(2026, 5, 21);
		byte[] pdf = "%PDF-fake-v2".getBytes();
		Report existing = mock(Report.class);
		when(reportService.generateDaily(date)).thenReturn(pdf);
		when(storageService.storeOrReplace(ReportKind.DAILY, "2026-05-21", pdf))
				.thenReturn("reports/DAILY/2026-05-21.pdf");
		when(reportRepository.findByKindAndPeriodKey(ReportKind.DAILY, "2026-05-21"))
				.thenReturn(Optional.of(existing));

		service.generateAndStoreDaily(date);

		verify(existing).replaceFile("reports/DAILY/2026-05-21.pdf", (long) pdf.length);
		verify(reportRepository, never()).save(any(Report.class));
		verify(notificationService).notifyAdmins(eq(NotificationKind.REPORT_READY), anyString());
	}

	@Test
	void weeklyJobUsesIsoWeekKey() {
		byte[] pdf = "%PDF-week".getBytes();
		when(reportService.generateWeekly(2026, 21)).thenReturn(pdf);
		when(storageService.storeOrReplace(eq(ReportKind.WEEKLY), eq("2026-W21"), any(byte[].class)))
				.thenReturn("reports/WEEKLY/2026-W21.pdf");
		when(reportRepository.findByKindAndPeriodKey(ReportKind.WEEKLY, "2026-W21"))
				.thenReturn(Optional.empty());

		service.generateAndStoreWeekly(2026, 21);

		verify(reportRepository).save(any(Report.class));
		verify(notificationService).notifyAdmins(NotificationKind.REPORT_READY,
				"지난 주 주간 보고서(2026-W21)가 준비되었습니다");
	}

	@Test
	void failureAlertsAdminsAndSwallowsToKeepSchedulerAlive() {
		LocalDate date = LocalDate.of(2026, 5, 21);
		when(reportService.generateDaily(date)).thenThrow(new IllegalStateException("font load failed"));

		assertThatCode(() -> service.generateAndStoreDaily(date)).doesNotThrowAnyException();

		verify(storageService, never()).storeOrReplace(any(), anyString(), any());
		verify(reportRepository, never()).save(any(Report.class));
		verify(reportRepository, never()).findByKindAndPeriodKey(any(), anyString());
		verify(notificationService).notifyAdmins(NotificationKind.REPORT_FAILED,
				"일간 보고서 생성에 실패했습니다 (기간: 2026-05-21)");
		verify(notificationService, never()).notifyAdmins(eq(NotificationKind.REPORT_READY), anyString());
	}

	@Test
	void weeklyFailureUsesWeeklyMessage() {
		when(reportService.generateWeekly(2026, 21)).thenThrow(new RuntimeException("boom"));

		service.generateAndStoreWeekly(2026, 21);

		verify(notificationService).notifyAdmins(NotificationKind.REPORT_FAILED,
				"주간 보고서 생성에 실패했습니다 (기간: 2026-W21)");
	}

	// silence unused-import warnings if Mockito-generated lambdas drop them
	@SuppressWarnings("unused")
	private static void touch(long ignored) {}
}
