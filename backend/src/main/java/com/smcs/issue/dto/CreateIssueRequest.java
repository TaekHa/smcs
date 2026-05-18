package com.smcs.issue.dto;

import com.smcs.issue.Priority;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

public record CreateIssueRequest(
		@NotBlank @Size(max = 200) String title,
		@NotBlank String callerName,
		@NotBlank String callerPhone,
		@NotNull Long categoryL1Id,
		@NotNull Long categoryL2Id,
		@NotNull Long categoryL3Id,
		@NotNull Priority priority,
		@NotBlank String description) {
}
