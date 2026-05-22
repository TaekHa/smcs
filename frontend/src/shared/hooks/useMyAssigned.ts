import { useQuery } from '@tanstack/react-query';
import { getMyAssigned } from '../../api/issues';
import type { IssueSummary } from '../../types/issue';

/** Field worker's assigned issues (mobile home). 30s stale (architecture §9.7). */
export function useMyAssigned() {
  return useQuery<IssueSummary[]>({
    queryKey: ['me', 'assigned'],
    queryFn: getMyAssigned,
    staleTime: 30_000,
  });
}
