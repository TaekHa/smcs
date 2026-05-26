package com.smcs.category.dto;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.util.List;

/**
 * Admin upsert payload — {@code id=null} creates, {@code id!=null} updates the row at that id
 * (Story 4.5 Deviation #2, PRD §6 single endpoint).
 */
public record CategoryUpsertRequest(
		Long id,
		@NotNull @Min(1) @Max(3) Short level,
		@NotBlank @Size(max = 100) String name,
		List<String> keywords,
		Integer sortOrder,
		Boolean active) {
}
