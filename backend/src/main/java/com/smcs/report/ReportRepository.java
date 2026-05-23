package com.smcs.report;

import com.smcs.report.dto.ReportKind;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

public interface ReportRepository extends JpaRepository<Report, Long> {

	/** Upsert key — Story 3.4 AC4 idempotent re-runs. */
	Optional<Report> findByKindAndPeriodKey(ReportKind kind, String periodKey);

	/** Archive list — Story 3.5 AC1, hits {@code idx_reports_kind_created_at} (V6). */
	Page<Report> findByKindOrderByCreatedAtDesc(ReportKind kind, Pageable pageable);

	/** Story 3.5 AC5 — daily cleanup of reports older than the retention cutoff. */
	List<Report> findByCreatedAtBefore(Instant cutoff);
}
