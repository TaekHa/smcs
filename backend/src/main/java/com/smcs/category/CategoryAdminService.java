package com.smcs.category;

import com.smcs.category.dto.CategoryAdminResponse;
import com.smcs.category.dto.CategoryUpsertRequest;
import java.util.ArrayList;
import java.util.List;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Admin-side category management (Story 4.5). Soft-delete only — {@code active=false} keeps
 * the row reachable from existing {@code issues.category_*_id} FK references (AC4).
 */
@Service
public class CategoryAdminService {

	private final CategoryRepository categoryRepository;

	public CategoryAdminService(CategoryRepository categoryRepository) {
		this.categoryRepository = categoryRepository;
	}

	@Transactional(readOnly = true)
	public List<CategoryAdminResponse> list(short level) {
		return categoryRepository.findByLevelOrderBySortOrderAscIdAsc(level).stream()
				.map(CategoryAdminResponse::from)
				.toList();
	}

	/**
	 * Upsert dispatch (Deviation #2): {@code id == null} creates a row at {@code max(sort_order)+1}
	 * within the level (Deviation #8); {@code id != null} loads-or-404 then applies all editable
	 * fields via the single {@link Category#update} mutator.
	 */
	@Transactional
	public CategoryAdminResponse upsert(CategoryUpsertRequest req) {
		List<String> keywords = req.keywords() == null ? new ArrayList<>() : req.keywords();
		boolean active = req.active() == null ? true : req.active();

		Category saved;
		if (req.id() == null) {
			int sortOrder = req.sortOrder() != null ? req.sortOrder() : nextSortOrder(req.level());
			Category created = new Category(null, req.level(), req.name(), keywords, sortOrder);
			if (!active) {
				// Caller explicitly creating an inactive row — rare but legal (e.g. seeding a future name).
				created.update(req.name(), keywords, sortOrder, false);
			}
			saved = categoryRepository.save(created);
		} else {
			Category existing = categoryRepository.findById(req.id())
					.orElseThrow(() -> new CategoryNotFoundException(req.id()));
			int sortOrder = req.sortOrder() != null
					? req.sortOrder()
					: (existing.getSortOrder() == null ? 0 : existing.getSortOrder());
			existing.update(req.name(), keywords, sortOrder, active);
			saved = existing;
		}
		return CategoryAdminResponse.from(saved);
	}

	private int nextSortOrder(short level) {
		return categoryRepository.findByLevelOrderBySortOrderAscIdAsc(level).stream()
				.map(Category::getSortOrder)
				.filter(s -> s != null)
				.max(Integer::compareTo)
				.map(max -> max + 1)
				.orElse(1);
	}
}
