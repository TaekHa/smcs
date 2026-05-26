package com.smcs.category;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.smcs.category.dto.CategoryAdminResponse;
import com.smcs.category.dto.CategoryUpsertRequest;
import java.lang.reflect.Field;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class CategoryAdminServiceTest {

	@Mock CategoryRepository categoryRepository;

	@InjectMocks CategoryAdminService service;

	private static Category cat(Long id, short level, String name, int sortOrder, boolean active,
			List<String> keywords) throws Exception {
		Category c = new Category(null, level, name, keywords, sortOrder);
		setField(c, "id", id);
		if (!active) {
			c.update(name, keywords, sortOrder, false);
		}
		return c;
	}

	@Test
	void listReturnsRowsIncludingInactive() throws Exception {
		when(categoryRepository.findByLevelOrderBySortOrderAscIdAsc((short) 1))
				.thenReturn(List.of(
						cat(1L, (short) 1, "활성", 1, true, List.of("a")),
						cat(2L, (short) 1, "비활성", 2, false, List.of())));

		List<CategoryAdminResponse> result = service.list((short) 1);

		assertThat(result).hasSize(2);
		assertThat(result.get(0).active()).isTrue();
		assertThat(result.get(1).active()).isFalse();
		assertThat(result.get(0).keywords()).containsExactly("a");
	}

	@Test
	void upsertCreatesNewRowWithMaxSortOrderPlusOne() {
		when(categoryRepository.findByLevelOrderBySortOrderAscIdAsc((short) 1))
				.thenReturn(List.of(
						mock(1L, 1), mock(2L, 5), mock(3L, 3)));
		ArgumentCaptor<Category> captor = ArgumentCaptor.forClass(Category.class);
		when(categoryRepository.save(captor.capture()))
				.thenAnswer(inv -> {
					Category arg = inv.getArgument(0);
					setQuiet(arg, "id", 99L);
					return arg;
				});

		CategoryAdminResponse out = service.upsert(new CategoryUpsertRequest(
				null, (short) 1, "신규", List.of("kw"), null, null));

		assertThat(out.id()).isEqualTo(99L);
		assertThat(out.sortOrder()).isEqualTo(6); // max 5 + 1
		assertThat(out.active()).isTrue();
		assertThat(captor.getValue().getName()).isEqualTo("신규");
		assertThat(captor.getValue().getKeywords()).containsExactly("kw");
		verify(categoryRepository, never()).findById(any());
	}

	@Test
	void upsertCreateRespectsExplicitSortOrder() {
		when(categoryRepository.save(any(Category.class))).thenAnswer(inv -> inv.getArgument(0));

		CategoryAdminResponse out = service.upsert(new CategoryUpsertRequest(
				null, (short) 2, "n", null, 42, true));

		assertThat(out.sortOrder()).isEqualTo(42);
		verify(categoryRepository, never()).findByLevelOrderBySortOrderAscIdAsc(any(Short.class));
	}

	@Test
	void upsertUpdatesExistingRowAndKeepsId() throws Exception {
		Category existing = cat(7L, (short) 1, "기존", 3, true, List.of("old"));
		when(categoryRepository.findById(7L)).thenReturn(Optional.of(existing));

		CategoryAdminResponse out = service.upsert(new CategoryUpsertRequest(
				7L, (short) 1, "수정", List.of("new", "키워드"), 4, false));

		assertThat(out.id()).isEqualTo(7L);
		assertThat(out.name()).isEqualTo("수정");
		assertThat(out.sortOrder()).isEqualTo(4);
		assertThat(out.active()).isFalse();
		assertThat(out.keywords()).containsExactly("new", "키워드");
		verify(categoryRepository, never()).save(any()); // managed entity, dirty checking persists
	}

	@Test
	void upsertUpdateUnknownIdThrowsCategoryNotFound() {
		when(categoryRepository.findById(999L)).thenReturn(Optional.empty());

		assertThatThrownBy(() -> service.upsert(new CategoryUpsertRequest(
				999L, (short) 1, "n", List.of(), 1, true)))
				.isInstanceOf(CategoryNotFoundException.class);
		verify(categoryRepository, times(1)).findById(999L);
		verify(categoryRepository, never()).save(any());
	}

	@Test
	void upsertUpdateNullSortOrderKeepsExistingSortOrder() throws Exception {
		Category existing = cat(7L, (short) 1, "기존", 9, true, List.of());
		when(categoryRepository.findById(7L)).thenReturn(Optional.of(existing));

		CategoryAdminResponse out = service.upsert(new CategoryUpsertRequest(
				7L, (short) 1, "rename", List.of(), null, true));

		assertThat(out.sortOrder()).isEqualTo(9);
	}

	@Test
	void upsertCreateNullKeywordsBecomesEmptyList() {
		when(categoryRepository.save(any(Category.class))).thenAnswer(inv -> inv.getArgument(0));

		CategoryAdminResponse out = service.upsert(new CategoryUpsertRequest(
				null, (short) 3, "n", null, 1, true));

		assertThat(out.keywords()).isEmpty();
	}

	// --- helpers ---

	private static Category mock(Long id, int sortOrder) {
		try {
			return cat(id, (short) 1, "x", sortOrder, true, List.of());
		} catch (Exception e) {
			throw new RuntimeException(e);
		}
	}

	private static void setField(Object target, String field, Object value) throws Exception {
		Field f = target.getClass().getDeclaredField(field);
		f.setAccessible(true);
		f.set(target, value);
	}

	private static void setQuiet(Object target, String field, Object value) {
		try {
			setField(target, field, value);
		} catch (Exception e) {
			throw new RuntimeException(e);
		}
	}
}
