package com.smcs.category;

import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;

public interface CategoryRepository extends JpaRepository<Category, Long> {

	/** Public form/filter: active categories only (Story 2.1). */
	List<Category> findByLevelAndActiveTrueOrderBySortOrderAsc(short level);

	/** Admin list: includes inactive rows; id tiebreaker keeps order stable on equal sort_order. */
	List<Category> findByLevelOrderBySortOrderAscIdAsc(short level);
}
