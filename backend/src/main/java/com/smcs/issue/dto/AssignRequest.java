package com.smcs.issue.dto;

import jakarta.validation.constraints.NotNull;

/** Assignment payload (Story 2.4). {@code assigneeId} must reference an active FIELD user. */
public record AssignRequest(@NotNull Long assigneeId) {
}
