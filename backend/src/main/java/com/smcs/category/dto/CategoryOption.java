package com.smcs.category.dto;

import com.smcs.category.Category;

public record CategoryOption(Long id, String name, short level) {

	public static CategoryOption from(Category category) {
		return new CategoryOption(category.getId(), category.getName(), category.getLevel());
	}
}
