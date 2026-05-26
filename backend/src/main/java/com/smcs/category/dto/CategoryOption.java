package com.smcs.category.dto;

import com.smcs.category.Category;
import java.util.List;

/**
 * Public category lookup payload (form/filter). {@code keywords} feeds Story 4.2 auto
 * category suggestion — included on every response (~0 cost) so clients that don't need
 * it can ignore the field; the admin DTO ({@link CategoryAdminResponse}) keeps surfacing
 * the operational fields (sortOrder/active) on top.
 */
public record CategoryOption(Long id, String name, short level, List<String> keywords) {

	public static CategoryOption from(Category category) {
		return new CategoryOption(
				category.getId(),
				category.getName(),
				category.getLevel(),
				category.getKeywords());
	}
}
