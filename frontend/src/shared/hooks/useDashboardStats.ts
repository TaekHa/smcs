import { useQuery } from '@tanstack/react-query';
import { getDashboardStats } from '../../api/stats';
import type { StatsPeriod } from '../../types/stats';

/** Dashboard aggregates for the chosen period (Story 3.2). Refetches when the period changes. */
export function useDashboardStats(period: StatsPeriod) {
  return useQuery({
    queryKey: ['stats', 'dashboard', period],
    queryFn: () => getDashboardStats(period),
  });
}
