package com.smcs.report;

import com.smcs.common.ErrorResponse;
import java.time.LocalDate;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.GetMapping;
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

	public ReportController(ReportService reportService) {
		this.reportService = reportService;
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
