package com.smcs.report;

import com.smcs.report.dto.ReportKind;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardOpenOption;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.io.FileSystemResource;
import org.springframework.core.io.Resource;
import org.springframework.stereotype.Service;

/**
 * Persists generated report PDFs under {@code smcs.files.dir/reports/{kind}/{periodKey}.pdf}.
 * Re-runs of the same period overwrite the existing file (Story 3.4 AC4 idempotent) — no UUID
 * suffix; {@code periodKey} is the natural unique id. Mirrors the path traversal guard from the
 * attachment {@code FileStorageService} (Story 2.6 precedent).
 */
@Service
public class ReportStorageService {

	private final Path baseDir;

	public ReportStorageService(@Value("${smcs.files.dir}") String dir) {
		this.baseDir = Paths.get(dir).toAbsolutePath().normalize();
	}

	/** Resolves a known relative path to a readable resource (traversal-guarded — Story 3.5). */
	public Resource load(String relativePath) {
		Path target = baseDir.resolve(relativePath).normalize();
		if (!target.startsWith(baseDir)) {
			throw new IllegalArgumentException("path traversal blocked");
		}
		return new FileSystemResource(target);
	}

	/** Best-effort cleanup of an expired report file (Story 3.5 AC5). Missing file is fine. */
	public void delete(String relativePath) {
		Path target = baseDir.resolve(relativePath).normalize();
		if (!target.startsWith(baseDir)) {
			throw new IllegalArgumentException("path traversal blocked");
		}
		try {
			Files.deleteIfExists(target);
		} catch (IOException ignored) {
			// best-effort — metadata cleanup still proceeds (Dev Notes Task 4)
		}
	}

	/** Writes {@code pdf} and returns the relative path stored in {@code reports.file_path}. */
	public String storeOrReplace(ReportKind kind, String periodKey, byte[] pdf) {
		String relative = "reports/" + kind.name() + "/" + periodKey + ".pdf";
		Path target = baseDir.resolve(relative).normalize();
		if (!target.startsWith(baseDir)) {
			throw new IllegalStateException("resolved path escapes base dir");
		}
		try {
			Files.createDirectories(target.getParent());
			Files.write(target, pdf,
					StandardOpenOption.CREATE,
					StandardOpenOption.TRUNCATE_EXISTING,
					StandardOpenOption.WRITE);
		} catch (IOException e) {
			throw new UncheckedIOException("failed to store report: " + relative, e);
		}
		return relative;
	}
}
