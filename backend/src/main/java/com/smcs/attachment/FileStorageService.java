package com.smcs.attachment;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.UUID;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.io.FileSystemResource;
import org.springframework.core.io.Resource;
import org.springframework.stereotype.Service;

/** Stores attachment bytes under {@code smcs.files.dir} as {@code yyyy/MM/{uuid}.{ext}} (UUID name, monthly dirs). */
@Service
public class FileStorageService {

	private static final ZoneId KST = ZoneId.of("Asia/Seoul");

	private final Path baseDir;

	public FileStorageService(@Value("${smcs.files.dir}") String dir) {
		this.baseDir = Paths.get(dir).toAbsolutePath().normalize();
	}

	/** Writes bytes and returns the relative path (stored in {@code attachments.filename}). */
	public String store(byte[] bytes, String ext) {
		LocalDate today = LocalDate.now(KST);
		String relative = "%04d/%02d/%s.%s".formatted(today.getYear(), today.getMonthValue(), UUID.randomUUID(), ext);
		Path target = baseDir.resolve(relative).normalize();
		if (!target.startsWith(baseDir)) {
			throw new IllegalStateException("resolved path escapes base dir");
		}
		try {
			Files.createDirectories(target.getParent());
			Files.write(target, bytes);
		} catch (IOException e) {
			throw new UncheckedIOException("failed to store attachment", e);
		}
		return relative.replace('\\', '/');
	}

	/** Resolves a known relative path to a readable resource (traversal-guarded). */
	public Resource load(String relativePath) {
		Path target = baseDir.resolve(relativePath).normalize();
		if (!target.startsWith(baseDir)) {
			throw new IllegalArgumentException("path traversal blocked");
		}
		return new FileSystemResource(target);
	}

	/** Best-effort cleanup of an orphaned file (e.g. after a failed DB insert). */
	public void delete(String relativePath) {
		try {
			Files.deleteIfExists(baseDir.resolve(relativePath).normalize());
		} catch (IOException ignored) {
			// orphan file is acceptable; nothing else to do
		}
	}
}
