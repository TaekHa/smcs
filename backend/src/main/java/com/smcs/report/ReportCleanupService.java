package com.smcs.report;

import java.time.Instant;
import java.util.List;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Deletes report files + metadata older than the retention cutoff (Story 3.5 AC5).
 * Time-independent — the scheduler computes {@code Instant.now() - retentionDays} and passes it
 * in so the algorithm can be unit-tested without a clock (3.4 ReportArchiveService precedent).
 */
@Service
public class ReportCleanupService {

	private static final Logger log = LoggerFactory.getLogger(ReportCleanupService.class);

	private final ReportRepository reportRepository;
	private final ReportStorageService storageService;

	public ReportCleanupService(ReportRepository reportRepository, ReportStorageService storageService) {
		this.reportRepository = reportRepository;
		this.storageService = storageService;
	}

	/** Removes every report with {@code createdAt < cutoff}. Returns the number of rows deleted. */
	@Transactional
	public int cleanupExpired(Instant cutoff) {
		List<Report> expired = reportRepository.findByCreatedAtBefore(cutoff);
		if (expired.isEmpty()) {
			return 0;
		}
		// File delete is best-effort — metadata still goes so we don't keep accumulating orphan rows.
		for (Report r : expired) {
			storageService.delete(r.getFilePath());
		}
		reportRepository.deleteAll(expired);
		log.info("report cleanup removed {} report(s) older than {}", expired.size(), cutoff);
		return expired.size();
	}
}
