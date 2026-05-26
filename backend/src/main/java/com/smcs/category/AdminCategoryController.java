package com.smcs.category;

import com.smcs.category.dto.CategoryAdminResponse;
import com.smcs.category.dto.CategoryUpsertRequest;
import jakarta.validation.Valid;
import java.util.List;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * ADMIN-only category management (Story 4.5). Coexists with the public
 * {@link CategoryController} {@code GET /api/categories} which keeps its active-only
 * semantics for the issue form/filters (AC4 — public dropdowns hide inactive rows
 * the moment ADMIN flips {@code active=false}).
 */
@RestController
@RequestMapping("/api/admin")
public class AdminCategoryController {

	private final CategoryAdminService categoryAdminService;

	public AdminCategoryController(CategoryAdminService categoryAdminService) {
		this.categoryAdminService = categoryAdminService;
	}

	@GetMapping("/categories")
	@PreAuthorize("hasRole('ADMIN')")
	public List<CategoryAdminResponse> list(@RequestParam short level) {
		return categoryAdminService.list(level);
	}

	/**
	 * Single upsert endpoint per PRD §6 (Deviation #2): id null → 201 Created,
	 * id present → 200 OK. Validation/auth errors flow through the global handler.
	 */
	@PostMapping("/categories")
	@PreAuthorize("hasRole('ADMIN')")
	public ResponseEntity<CategoryAdminResponse> upsert(@Valid @RequestBody CategoryUpsertRequest request) {
		boolean creating = request.id() == null;
		CategoryAdminResponse body = categoryAdminService.upsert(request);
		return ResponseEntity.status(creating ? HttpStatus.CREATED : HttpStatus.OK).body(body);
	}
}
