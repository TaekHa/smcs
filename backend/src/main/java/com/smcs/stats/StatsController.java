package com.smcs.stats;

import com.smcs.stats.dto.DashboardStats;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * Dashboard statistics. The API requires authentication (§6); the dashboard's ADMIN-only
 * screen guard is a frontend route concern (Story 3.2). An invalid {@code period} is rejected
 * as 400 VALIDATION_FAILED by StatsPeriodConverter → MethodArgumentTypeMismatchException.
 */
@RestController
@RequestMapping("/api")
public class StatsController {

	private final StatsService statsService;

	public StatsController(StatsService statsService) {
		this.statsService = statsService;
	}

	@GetMapping("/stats/dashboard")
	@PreAuthorize("isAuthenticated()")
	public DashboardStats dashboard(@RequestParam(defaultValue = "today") StatsPeriod period) {
		return statsService.dashboard(period);
	}
}
