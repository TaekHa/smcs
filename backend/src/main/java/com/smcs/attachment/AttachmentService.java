package com.smcs.attachment;

import com.smcs.attachment.dto.AttachmentResponse;
import com.smcs.issue.IssueAccessGuard;
import java.io.IOException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

@Service
public class AttachmentService {

	private static final int MAX_PER_ISSUE = 10;

	private final IssueAccessGuard accessGuard;
	private final AttachmentRepository attachmentRepository;
	private final ImageProcessor imageProcessor;
	private final FileStorageService fileStorage;

	public AttachmentService(IssueAccessGuard accessGuard, AttachmentRepository attachmentRepository,
			ImageProcessor imageProcessor, FileStorageService fileStorage) {
		this.accessGuard = accessGuard;
		this.attachmentRepository = attachmentRepository;
		this.imageProcessor = imageProcessor;
		this.fileStorage = fileStorage;
	}

	/**
	 * Validates + EXIF-strips the image, stores it, and records the attachment (Story 2.6).
	 * Ownership (§6.3) and the per-issue limit (AC7) are enforced first.
	 */
	@Transactional
	public AttachmentResponse upload(Long issueId, Long userId, boolean privileged, MultipartFile file) {
		accessGuard.requireAccessible(issueId, userId, privileged); // 404 / 403
		if (attachmentRepository.countByIssueId(issueId) >= MAX_PER_ISSUE) {
			throw new AttachmentLimitException(issueId);
		}
		byte[] raw;
		try {
			raw = file.getBytes();
		} catch (IOException e) {
			throw new InvalidImageException("unreadable upload");
		}
		ImageProcessor.ProcessedImage processed = imageProcessor.process(raw); // magic byte + strip → 400
		String relativePath = fileStorage.store(processed.bytes(), processed.ext());
		try {
			Attachment saved = attachmentRepository.save(new Attachment(
					issueId, userId, relativePath, originalName(file),
					processed.mimeType(), (long) processed.bytes().length));
			return AttachmentResponse.from(saved);
		} catch (RuntimeException e) {
			fileStorage.delete(relativePath); // orphan cleanup on DB failure
			throw e;
		}
	}

	private String originalName(MultipartFile file) {
		String name = file.getOriginalFilename();
		if (name == null || name.isBlank()) {
			return "upload";
		}
		return name.length() > 255 ? name.substring(0, 255) : name;
	}
}
