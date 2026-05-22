package com.smcs.stats;

import com.smcs.category.Category;
import com.smcs.category.CategoryRepository;
import com.smcs.issue.Priority;
import com.smcs.stats.dto.DashboardStats;
import com.smcs.stats.dto.DashboardStats.AssigneeCount;
import com.smcs.stats.dto.DashboardStats.CategoryCount;
import com.smcs.stats.dto.DashboardStats.Kpi;
import com.smcs.stats.dto.DashboardStats.PriorityCount;
import com.smcs.stats.dto.DashboardStats.TrendPoint;
import com.smcs.user.User;
import com.smcs.user.UserRepository;
import java.time.Instant;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Single source of dashboard/report aggregates (PRD §5.9). KST day grouping needs
 * {@code AT TIME ZONE} which JPQL can't express, so aggregation is native SQL via JdbcTemplate
 * (LocalDataSeeder precedent). The {@link #aggregate(Instant, Instant)} range method is reused
 * by Story 3.3 (PDF reports) for arbitrary date/week ranges — no logic duplication.
 */
@Service
public class StatsService {

	private static final ZoneId KST = ZoneId.of("Asia/Seoul");
	private static final String OPEN_STATUS_CLAUSE = "status NOT IN ('DONE', 'VERIFIED')";

	private final JdbcTemplate jdbc;
	private final CategoryRepository categoryRepository;
	private final UserRepository userRepository;

	public StatsService(JdbcTemplate jdbc, CategoryRepository categoryRepository, UserRepository userRepository) {
		this.jdbc = jdbc;
		this.categoryRepository = categoryRepository;
		this.userRepository = userRepository;
	}

	/** Dashboard stats for the given period, resolved against the current KST date. */
	@Transactional(readOnly = true)
	public DashboardStats dashboard(StatsPeriod period) {
		StatsRange range = period.rangeFor(LocalDate.now(KST));
		return aggregate(range.from(), range.to());
	}

	/** Aggregates over the half-open instant range {@code [from, to)}. Reused by Story 3.3. */
	@Transactional(readOnly = true)
	public DashboardStats aggregate(Instant from, Instant to) {
		Object f = ts(from);
		Object t = ts(to);

		long newCount = count("created_at >= ? AND created_at < ?", f, t);
		long resolvedCount = count("resolved_at >= ? AND resolved_at < ?", f, t);
		long openCount = count(OPEN_STATUS_CLAUSE);
		long avgResolveMinutes = avgResolveMinutes(f, t);

		return new DashboardStats(
				new Kpi(newCount, resolvedCount, openCount, avgResolveMinutes),
				byCategory(f, t),
				byAssignee(f, t),
				byPriority(f, t),
				trend(from, to, f, t));
	}

	private long count(String whereClause, Object... args) {
		Long n = jdbc.queryForObject("SELECT count(*) FROM issues WHERE " + whereClause, Long.class, args);
		return n == null ? 0L : n;
	}

	private long avgResolveMinutes(Object from, Object to) {
		Double avg = jdbc.queryForObject(
				"SELECT avg(EXTRACT(EPOCH FROM (resolved_at - created_at)) / 60.0) FROM issues "
						+ "WHERE resolved_at >= ? AND resolved_at < ?",
				Double.class, from, to);
		return avg == null ? 0L : Math.round(avg);
	}

	private List<CategoryCount> byCategory(Object from, Object to) {
		Map<Long, Long> counts = countByKey(
				"SELECT category_l1_id AS k, count(*) AS c FROM issues "
						+ "WHERE created_at >= ? AND created_at < ? GROUP BY category_l1_id",
				from, to);
		Map<Long, String> names = categoryRepository.findAll().stream()
				.collect(Collectors.toMap(Category::getId, Category::getName));
		return counts.entrySet().stream()
				.map(e -> new CategoryCount(names.get(e.getKey()), e.getValue()))
				.sorted(Comparator.comparingLong(CategoryCount::count).reversed())
				.toList();
	}

	private List<AssigneeCount> byAssignee(Object from, Object to) {
		Map<Long, Long> counts = countByKey(
				"SELECT assigned_to AS k, count(*) AS c FROM issues "
						+ "WHERE resolved_at >= ? AND resolved_at < ? AND assigned_to IS NOT NULL GROUP BY assigned_to",
				from, to);
		Map<Long, String> names = userRepository.findAllById(counts.keySet()).stream()
				.collect(Collectors.toMap(User::getId, User::getDisplayName));
		return counts.entrySet().stream()
				.map(e -> new AssigneeCount(names.get(e.getKey()), e.getValue()))
				.sorted(Comparator.comparingLong(AssigneeCount::resolved).reversed())
				.toList();
	}

	private List<PriorityCount> byPriority(Object from, Object to) {
		List<PriorityCount> result = new ArrayList<>();
		jdbc.query(
				"SELECT priority AS p, count(*) AS c FROM issues "
						+ "WHERE created_at >= ? AND created_at < ? GROUP BY priority",
				rs -> {
					result.add(new PriorityCount(Priority.valueOf(rs.getString("p")), rs.getLong("c")));
				},
				from, to);
		result.sort(Comparator.comparingLong(PriorityCount::count).reversed());
		return result;
	}

	private List<TrendPoint> trend(Instant from, Instant to, Object fromTs, Object toTs) {
		Map<LocalDate, Long> newByDate = countByDate(
				"SELECT (created_at AT TIME ZONE 'Asia/Seoul')::date AS d, count(*) AS c FROM issues "
						+ "WHERE created_at >= ? AND created_at < ? GROUP BY d",
				fromTs, toTs);
		Map<LocalDate, Long> resolvedByDate = countByDate(
				"SELECT (resolved_at AT TIME ZONE 'Asia/Seoul')::date AS d, count(*) AS c FROM issues "
						+ "WHERE resolved_at >= ? AND resolved_at < ? GROUP BY d",
				fromTs, toTs);

		List<TrendPoint> points = new ArrayList<>();
		LocalDate end = to.atZone(KST).toLocalDate(); // exclusive
		for (LocalDate d = from.atZone(KST).toLocalDate(); d.isBefore(end); d = d.plusDays(1)) {
			points.add(new TrendPoint(d, newByDate.getOrDefault(d, 0L), resolvedByDate.getOrDefault(d, 0L)));
		}
		return points;
	}

	private Map<Long, Long> countByKey(String sql, Object... args) {
		Map<Long, Long> map = new LinkedHashMap<>();
		jdbc.query(sql, rs -> {
			map.put(rs.getLong("k"), rs.getLong("c"));
		}, args);
		return map;
	}

	private Map<LocalDate, Long> countByDate(String sql, Object... args) {
		Map<LocalDate, Long> map = new LinkedHashMap<>();
		jdbc.query(sql, rs -> {
			map.put(rs.getObject("d", LocalDate.class), rs.getLong("c"));
		}, args);
		return map;
	}

	/** TIMESTAMPTZ bind as an unambiguous absolute instant (avoids JVM-tz Timestamp pitfalls). */
	private static Object ts(Instant instant) {
		return OffsetDateTime.ofInstant(instant, ZoneOffset.UTC);
	}
}
