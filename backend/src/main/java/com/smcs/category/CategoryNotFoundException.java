package com.smcs.category;

/** Thrown by the admin upsert path when {@code id} does not resolve to an existing row. */
public class CategoryNotFoundException extends RuntimeException {

	public CategoryNotFoundException(Long id) {
		super("Category not found: " + id);
	}
}
