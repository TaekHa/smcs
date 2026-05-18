package com.smcs.issue.dto;

import com.smcs.issue.IssueStatus;
import java.time.LocalDate;
import java.util.List;

/** Optional list/search filters. Any null/empty field means "no filter on that dimension". */
public record IssueListFilter(
		List<IssueStatus> statuses,
		List<Long> categoryL1Ids,
		List<Long> categoryL2Ids,
		List<Long> categoryL3Ids,
		Long assigneeId,
		LocalDate from,
		LocalDate to,
		String q) {
}
