package com.smcs.notification;

import java.time.Instant;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface NotificationRepository extends JpaRepository<Notification, Long> {

	long countByRecipientIdAndReadAtIsNull(Long recipientId);

	Page<Notification> findByRecipientIdOrderByCreatedAtDesc(Long recipientId, Pageable pageable);

	@Modifying
	@Query("update Notification n set n.readAt = :now where n.recipientId = :recipientId and n.readAt is null")
	int markAllRead(@Param("recipientId") Long recipientId, @Param("now") Instant now);

	/** Story 4.1 — 90-day retention. Bulk delete used by {@link NotificationCleanupService}. */
	@Modifying
	@Query("delete from Notification n where n.createdAt < :cutoff")
	int deleteByCreatedAtBefore(@Param("cutoff") Instant cutoff);
}
