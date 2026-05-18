package com.smcs.category;

import com.smcs.category.dto.CategoryOption;
import java.util.List;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * Read-only category lookup for the issue form / filters (authenticated users).
 * Admin category management is a separate ADMIN-only endpoint (Story 4.x).
 * L1/L2/L3 carry no parent dependency — filtered by level only (AC7).
 */
@RestController
@RequestMapping("/api")
public class CategoryController {

	private final CategoryRepository categoryRepository;

	public CategoryController(CategoryRepository categoryRepository) {
		this.categoryRepository = categoryRepository;
	}

	@GetMapping("/categories")
	@PreAuthorize("isAuthenticated()")
	public List<CategoryOption> categories(@RequestParam short level) {
		return categoryRepository.findByLevelAndActiveTrueOrderBySortOrderAsc(level)
				.stream()
				.map(CategoryOption::from)
				.toList();
	}
}
