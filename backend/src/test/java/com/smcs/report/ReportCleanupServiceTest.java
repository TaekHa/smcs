package com.smcs.report;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.Instant;
import java.util.List;
import org.junit.jupiter.api.Test;

/** Unit tests for retention-cutoff cleanup (Story 3.5 AC5). DB + filesystem are mocked. */
class ReportCleanupServiceTest {

	private final ReportRepository reportRepository = mock(ReportRepository.class);
	private final ReportStorageService storageService = mock(ReportStorageService.class);
	private final ReportCleanupService service = new ReportCleanupService(reportRepository, storageService);

	@Test
	void emptyExpiredListIsNoOp() {
		Instant cutoff = Instant.parse("2026-02-22T00:00:00Z");
		when(reportRepository.findByCreatedAtBefore(cutoff)).thenReturn(List.of());

		int deleted = service.cleanupExpired(cutoff);

		assertThat(deleted).isZero();
		verify(storageService, never()).delete(any());
		verify(reportRepository, never()).deleteAll(any());
	}

	@Test
	void deletesFilesAndMetadataForEveryExpiredRow() {
		Instant cutoff = Instant.parse("2026-02-22T00:00:00Z");
		Report r1 = mock(Report.class);
		Report r2 = mock(Report.class);
		when(r1.getFilePath()).thenReturn("reports/DAILY/2025-12-01.pdf");
		when(r2.getFilePath()).thenReturn("reports/DAILY/2025-12-02.pdf");
		when(reportRepository.findByCreatedAtBefore(cutoff)).thenReturn(List.of(r1, r2));

		int deleted = service.cleanupExpired(cutoff);

		assertThat(deleted).isEqualTo(2);
		verify(storageService).delete("reports/DAILY/2025-12-01.pdf");
		verify(storageService).delete("reports/DAILY/2025-12-02.pdf");
		verify(reportRepository, times(1)).deleteAll(List.of(r1, r2));
	}

}
