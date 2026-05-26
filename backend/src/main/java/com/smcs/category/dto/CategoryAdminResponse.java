package com.smcs.category.dto;

import com.smcs.category.Category;
import java.util.List;

/**
 * Admin view of a category — exposes operational fields ({@code sortOrder}/{@code active}/
 * {@code keywords}) that the public {@link CategoryOption} intentionally hides.
 */
public record CategoryAdminResponse(
		Long id,
		short level,
		String name,
		int sortOrder,
		boolean active,
		List<String> keywords) {

	public static CategoryAdminResponse from(Category c) {
		return new CategoryAdminResponse(
				c.getId(),
				c.getLevel(),
				c.getName(),
				c.getSortOrder() == null ? 0 : c.getSortOrder(),
				c.isActive(),
				c.getKeywords());
	}
}
