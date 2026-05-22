package com.smcs.attachment;

import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface AttachmentRepository extends JpaRepository<Attachment, Long> {

	List<Attachment> findByIssueIdOrderByCreatedAtAsc(Long issueId);

	long countByIssueId(Long issueId);

	Optional<Attachment> findByFilename(String filename);
}
