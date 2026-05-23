package com.smcs.report;

import com.smcs.notification.NotificationKind;
import com.smcs.notification.NotificationService;
import com.smcs.report.dto.ReportKind;
import java.time.LocalDate;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Generates a report PDF (Story 3.3 {@link ReportService}), persists the bytes
 * ({@link ReportStorageService}), upserts metadata ({@link ReportRepository}), and notifies all
 * active ADMINs. Time-independent — the date/week is passed in so unit tests bypass the clock
 * (Story 3.4 Dev Notes §5.8); the {@link ReportScheduler} is the only time-aware caller.
 *
 * <p>Failure handling (AC7): caught + logged + REPORT_FAILED notification, then swallowed so the
 * scheduler keeps running. No dedup/throttle — every failure emits one alert (PO MVP decision).
 */
@Service
public class ReportArchiveService {

	private static final Logger log = LoggerFactory.getLogger(ReportArchiveService.class);

	private final ReportService reportService;
	private final ReportStorageService storageService;
	private final ReportRepository reportRepository;
	private final NotificationService notificationService;

	public ReportArchiveService(ReportService reportService, ReportStorageService storageService,
			ReportRepository reportRepository, NotificationService notificationService) {
		this.reportService = reportService;
		this.storageService = storageService;
		this.reportRepository = reportRepository;
		this.notificationService = notificationService;
	}

	/** Generates and archives the daily report for {@code date} (KST). */
	@Transactional
	public void generateAndStoreDaily(LocalDate date) {
		ReportPeriod period = ReportPeriod.forDate(date);
		runArchive(period, () -> reportService.generateDaily(date),
				"어제 일간 보고서(" + period.periodKey() + ")가 준비되었습니다",
				"일간 보고서 생성에 실패했습니다 (기간: " + period.periodKey() + ")");
	}

	/** Generates and archives the weekly report for the given ISO {@code (weekBasedYear, weekOfYear)}. */
	@Transactional
	public void generateAndStoreWeekly(int weekBasedYear, int weekOfYear) {
		ReportPeriod period = ReportPeriod.forWeek(weekBasedYear, weekOfYear);
		runArchive(period, () -> reportService.generateWeekly(weekBasedYear, weekOfYear),
				"지난 주 주간 보고서(" + period.periodKey() + ")가 준비되었습니다",
				"주간 보고서 생성에 실패했습니다 (기간: " + period.periodKey() + ")");
	}

	// @Transactional moved to the public entry points above (TD-2 Task 1) — Spring AOP can't
	// see self-invocations, so leaving it here would silently fail dirty checking on re-runs.
	void runArchive(ReportPeriod period, PdfSupplier supplier, String readyMessage, String failedMessage) {
		try {
			byte[] pdf = supplier.get();
			String path = storageService.storeOrReplace(period.kind(), period.periodKey(), pdf);
			upsertMetadata(period.kind(), period.periodKey(), path, pdf.length);
			notificationService.notifyAdmins(NotificationKind.REPORT_READY, readyMessage);
		} catch (RuntimeException e) {
			// AC7 — log + alert ADMINs, swallow so the scheduler keeps running.
			log.error("Report archive failed for {} {}: {}", period.kind(), period.periodKey(), e.getMessage(), e);
			notificationService.notifyAdmins(NotificationKind.REPORT_FAILED, failedMessage);
		}
	}

	private void upsertMetadata(ReportKind kind, String periodKey, String filePath, long sizeBytes) {
		reportRepository.findByKindAndPeriodKey(kind, periodKey)
				.ifPresentOrElse(
						existing -> existing.replaceFile(filePath, sizeBytes),
						() -> reportRepository.save(new Report(kind, periodKey, filePath, sizeBytes)));
	}

	@FunctionalInterface
	interface PdfSupplier {
		byte[] get();
	}
}
