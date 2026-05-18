package com.smcs.issue;

import com.smcs.issue.dto.IssueListFilter;
import jakarta.persistence.criteria.Predicate;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import org.springframework.data.jpa.domain.Specification;

/** Builds the dynamic WHERE for issue list/search. Each clause applies only when its input is present. */
final class IssueSpecifications {

	private IssueSpecifications() {
	}

	/**
	 * @param phoneHash precomputed HMAC of the search term, or null when the term is not a
	 *                  plausible phone number (PO refinement R1: >= 9 normalized digits).
	 */
	static Specification<Issue> build(IssueListFilter f, String phoneHash, Instant fromInstant, Instant toInstant) {
		return (root, query, cb) -> {
			List<Predicate> and = new ArrayList<>();

			if (f.statuses() != null && !f.statuses().isEmpty()) {
				and.add(root.get("status").in(f.statuses()));
			}
			if (f.categoryL1Ids() != null && !f.categoryL1Ids().isEmpty()) {
				and.add(root.get("categoryL1Id").in(f.categoryL1Ids()));
			}
			if (f.categoryL2Ids() != null && !f.categoryL2Ids().isEmpty()) {
				and.add(root.get("categoryL2Id").in(f.categoryL2Ids()));
			}
			if (f.categoryL3Ids() != null && !f.categoryL3Ids().isEmpty()) {
				and.add(root.get("categoryL3Id").in(f.categoryL3Ids()));
			}
			if (f.assigneeId() != null) {
				and.add(cb.equal(root.get("assignedTo"), f.assigneeId()));
			}
			if (fromInstant != null) {
				and.add(cb.greaterThanOrEqualTo(root.get("createdAt"), fromInstant));
			}
			if (toInstant != null) {
				and.add(cb.lessThan(root.get("createdAt"), toInstant));
			}
			if (f.q() != null && !f.q().isBlank()) {
				String like = "%" + f.q().toLowerCase() + "%";
				Predicate text = cb.or(
						cb.like(cb.lower(root.get("title")), like),
						cb.like(cb.lower(root.get("description")), like));
				// caller name is NOT searchable (encrypted column, §5.3 MVP);
				// phone is exact-match via HMAC only when the term looks like a phone (R1).
				and.add(phoneHash != null
						? cb.or(text, cb.equal(root.get("callerPhoneHash"), phoneHash))
						: text);
			}

			return cb.and(and.toArray(new Predicate[0]));
		};
	}

	/** Default order: priority severity (URGENT→LOW) then createdAt asc. */
	static Specification<Issue> defaultOrder() {
		return (root, query, cb) -> {
			// Skip on the count query Spring Data issues for Page totals.
			Class<?> rt = query.getResultType();
			if (rt != Long.class && rt != long.class) {
				query.orderBy(
						cb.asc(cb.selectCase(root.get("priority"))
								.when(Priority.URGENT, 0)
								.when(Priority.HIGH, 1)
								.when(Priority.NORMAL, 2)
								.when(Priority.LOW, 3)
								.otherwise(4)),
						cb.asc(root.get("createdAt")));
			}
			return cb.conjunction();
		};
	}
}
