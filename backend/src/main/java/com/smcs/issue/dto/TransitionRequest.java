package com.smcs.issue.dto;

import com.smcs.issue.IssueStatus;
import jakarta.validation.constraints.NotNull;

/**
 * Status transition payload (Story 2.4). {@code reason} is optional here;
 * the reopen-reason flow is Story 2.7.
 */
public record TransitionRequest(@NotNull IssueStatus to, String reason) {
}
