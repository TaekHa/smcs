package com.smcs.issue.export;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.smcs.category.Category;
import com.smcs.category.CategoryRepository;
import com.smcs.crypto.HmacHasher;
import com.smcs.issue.Issue;
import com.smcs.issue.IssueRepository;
import com.smcs.issue.IssueStatus;
import com.smcs.issue.Priority;
import com.smcs.issue.dto.IssueListFilter;
import com.smcs.user.User;
import com.smcs.user.UserRepository;
import java.io.StringWriter;
import java.lang.reflect.Field;
import java.time.Instant;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.jpa.domain.Specification;

@ExtendWith(MockitoExtension.class)
class IssueExportServiceTest {

	@Mock IssueRepository issueRepository;
	@Mock CategoryRepository categoryRepository;
	@Mock UserRepository userRepository;
	@Mock HmacHasher hmacHasher;

	private IssueExportService service;

	@BeforeEach
	void setUp() {
		service = new IssueExportService(issueRepository, categoryRepository, userRepository,
				hmacHasher, new IssueCsvExporter());
	}

	@Test
	void rejectsExportWhenCountExceedsMax() {
		when(issueRepository.count(any(Specification.class))).thenReturn(5_001L);

		IssueListFilter filter = new IssueListFilter(null, null, null, null, null, null, null, null);
		StringWriter out = new StringWriter();

		assertThatThrownBy(() -> service.exportCsv(filter, false, out))
				.isInstanceOf(ExportTooManyRowsException.class);

		// Count gate must short-circuit before the main fetch — that's the whole point.
		verify(issueRepository, times(1)).count(any(Specification.class));
		verify(issueRepository, never()).findAll(any(Specification.class));
	}

	@Test
	void writesHeaderAndRowsWhenUnderMax() throws Exception {
		when(issueRepository.count(any(Specification.class))).thenReturn(2L);
		Issue a = newIssue(1L, "alpha");
		Issue b = newIssue(2L, "bravo");
		when(issueRepository.findAll(any(Specification.class))).thenReturn(List.of(a, b));
		Category c1 = newCategory(10L, "L1");
		Category c2 = newCategory(20L, "L2");
		Category c3 = newCategory(30L, "L3");
		when(categoryRepository.findAll()).thenReturn(List.of(c1, c2, c3));
		when(userRepository.findAllById(List.of())).thenReturn(List.of());

		IssueListFilter filter = new IssueListFilter(null, null, null, null, null, null, null, null);
		StringWriter out = new StringWriter();
		service.exportCsv(filter, false, out);

		String csv = out.toString();
		// Header + 2 rows = 3 CRLF-terminated lines
		assertThat(csv.split("\r\n")).hasSize(3);
		assertThat(csv).startsWith("ID,제목,카테고리");
		assertThat(csv).contains(",alpha,");
		assertThat(csv).contains(",bravo,");
	}

	@Test
	void doesNotHashShortQueryAsPhone() {
		when(issueRepository.count(any(Specification.class))).thenReturn(0L);
		when(issueRepository.findAll(any(Specification.class))).thenReturn(List.of());

		IssueListFilter filter = new IssueListFilter(null, null, null, null, null, null, null, "no");
		service.exportCsv(filter, false, new StringWriter());

		verify(hmacHasher, never()).hashPhone(any());
	}

	// --- helpers ---

	private Issue newIssue(Long id, String title) throws Exception {
		Issue i = new Issue(title, "desc", null, null, null, 10L, 20L, 30L, Priority.NORMAL, 1L);
		set(i, "id", id);
		set(i, "status", IssueStatus.NEW);
		set(i, "createdAt", Instant.parse("2026-05-26T00:00:00Z"));
		return i;
	}

	private Category newCategory(Long id, String name) throws Exception {
		java.lang.reflect.Constructor<Category> ctor = Category.class.getDeclaredConstructor();
		ctor.setAccessible(true);
		Category c = ctor.newInstance();
		set(c, "id", id);
		set(c, "name", name);
		return c;
	}

	private static void set(Object target, String field, Object value) throws Exception {
		Field f = target.getClass().getDeclaredField(field);
		f.setAccessible(true);
		f.set(target, value);
	}
}
