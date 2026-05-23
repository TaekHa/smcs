package com.smcs.report;

import com.smcs.common.ErrorResponse;
import com.smcs.report.dto.ReportKind;
import com.smcs.report.dto.ReportSummary;
import java.time.LocalDate;
import org.springframework.core.io.Resource;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.web.PageableDefault;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * Serves on-demand PDF reports for ADMINs (§6 reports/*=ADMIN). Streams bytes inline so
 * Story 3.5's preview tab can render them; downloads use a frontend blob. No persistence —
 * the archive (V6) is Story 3.4's responsibility. Invalid {@code date}/{@code week} → 400.
 */
@RestController
@RequestMapping("/api")
public class ReportController {

	private final ReportService reportService;
	private final ReportRepository reportRepository;
	private final ReportStorageService storageService;

	public ReportController(ReportService reportService, ReportRepository reportRepository,
			ReportStorageService storageService) {
		this.reportService = reportService;
		this.reportRepository = reportRepository;
		this.storageService = storageService;
	}

	@GetMapping("/reports/daily")
	@PreAuthorize("hasRole('ADMIN')")
	public ResponseEntity<byte[]> daily(
			@RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate date) {
		byte[] pdf = reportService.generateDaily(date);
		return pdfResponse(pdf, "daily-" + date + ".pdf");
	}

	@GetMapping("/reports/weekly")
	@PreAuthorize("hasRole('ADMIN')")
	public ResponseEntity<byte[]> weekly(@RequestParam String week) {
		ReportPeriod period = ReportPeriod.parseWeek(week);
		byte[] pdf = reportService.generateWeekly(period.weekBasedYear(), period.weekOfYear());
		return pdfResponse(pdf, "weekly-" + period.periodKey() + ".pdf");
	}

	/** Archive list (Story 3.5 AC1) — newest first, ADMIN only. */
	@GetMapping("/reports")
	@PreAuthorize("hasRole('ADMIN')")
	public Page<ReportSummary> list(@RequestParam ReportKind kind,
			@PageableDefault(size = 20) Pageable pageable) {
		return reportRepository.findByKindOrderByCreatedAtDesc(kind, pageable)
				.map(r -> new ReportSummary(r.getId(), r.getKind(), r.getPeriodKey(),
						r.getSizeBytes(), r.getCreatedAt()));
	}

	/**
	 * Serves a stored PDF (Story 3.5 AC3/AC4). {@code mode=preview} → inline (new browser tab),
	 * {@code mode=download} → attachment (save dialog). The file is identified by db id so the
	 * on-disk {@code file_path} never leaks to the client.
	 */
	@GetMapping("/reports/{id}/file")
	@PreAuthorize("hasRole('ADMIN')")
	public ResponseEntity<Resource> file(@PathVariable Long id,
			@RequestParam(defaultValue = "preview") String mode) {
		if (!"preview".equals(mode) && !"download".equals(mode)) {
			throw new IllegalArgumentException("mode must be 'preview' or 'download'");
		}
		Report report = reportRepository.findById(id).orElseThrow(() -> new ReportNotFoundException(id));
		Resource resource = storageService.load(report.getFilePath());
		String filename = report.getKind().name() + "-" + report.getPeriodKey() + ".pdf";
		String disposition = ("download".equals(mode) ? "attachment" : "inline")
				+ "; filename=\"" + filename + "\"";
		return ResponseEntity.ok()
				.contentType(MediaType.APPLICATION_PDF)
				.header(HttpHeaders.CONTENT_DISPOSITION, disposition)
				.body(resource);
	}

	/** Controller-local handler keeps the global advice clean while still returning {code,message}. */
	@ExceptionHandler(IllegalArgumentException.class)
	public ResponseEntity<ErrorResponse> handleInvalidPeriod(IllegalArgumentException ex) {
		return ResponseEntity.status(HttpStatus.BAD_REQUEST)
				.body(ErrorResponse.of("VALIDATION_FAILED", ex.getMessage()));
	}

	private static ResponseEntity<byte[]> pdfResponse(byte[] pdf, String filename) {
		return ResponseEntity.ok()
				.contentType(MediaType.APPLICATION_PDF)
				.header(HttpHeaders.CONTENT_DISPOSITION, "inline; filename=\"" + filename + "\"")
				.body(pdf);
	}
}
