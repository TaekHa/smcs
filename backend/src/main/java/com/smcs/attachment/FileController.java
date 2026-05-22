package com.smcs.attachment;

import com.smcs.issue.IssueAccessGuard;
import com.smcs.security.AuthSupport;
import jakarta.servlet.http.HttpServletRequest;
import java.time.Duration;
import org.springframework.core.io.Resource;
import org.springframework.http.CacheControl;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Serves attachment images directly (Story 2.6, Deviation #1). Authenticates + verifies the
 * caller can access the owning issue, then streams bytes. The §8.6 X-Accel-Redirect optimization
 * is a deployment swap-in (§10/Story 4.x); security is identical (backend authorizes either way).
 */
@RestController
public class FileController {

	private static final String PREFIX = "/files/";

	private final AttachmentRepository attachmentRepository;
	private final IssueAccessGuard accessGuard;
	private final FileStorageService fileStorage;

	public FileController(AttachmentRepository attachmentRepository, IssueAccessGuard accessGuard,
			FileStorageService fileStorage) {
		this.attachmentRepository = attachmentRepository;
		this.accessGuard = accessGuard;
		this.fileStorage = fileStorage;
	}

	@GetMapping("/files/**")
	@PreAuthorize("isAuthenticated()")
	public ResponseEntity<Resource> serve(HttpServletRequest request,
			@AuthenticationPrincipal Object principal, Authentication authentication) {
		String uri = request.getRequestURI();
		String filename = uri.substring(uri.indexOf(PREFIX) + PREFIX.length());
		// Only known attachments are served (no arbitrary disk path → traversal-safe).
		Attachment attachment = attachmentRepository.findByFilename(filename)
				.orElseThrow(() -> new AttachmentNotFoundException(filename));
		accessGuard.requireAccessible(attachment.getIssueId(), (Long) principal,
				AuthSupport.isPrivileged(authentication)); // 403 / 404
		Resource resource = fileStorage.load(attachment.getFilename());
		return ResponseEntity.ok()
				.contentType(MediaType.parseMediaType(attachment.getMimeType()))
				.cacheControl(CacheControl.maxAge(Duration.ofHours(1)).cachePrivate())
				.body(resource);
	}
}
