package com.smcs.report;

import com.smcs.report.dto.ReportKind;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface ReportRepository extends JpaRepository<Report, Long> {

	/** Upsert key — Story 3.4 AC4 idempotent re-runs. */
	Optional<Report> findByKindAndPeriodKey(ReportKind kind, String periodKey);
}
