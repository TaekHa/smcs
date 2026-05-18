import { useQuery } from '@tanstack/react-query';
import { listUsers } from '../../api/users';
import type { UserSummary } from '../../types/auth';

/** Active users for assignee filter/select. Cached 5min (architecture §9.7). */
export function useUsers() {
  return useQuery<UserSummary[]>({
    queryKey: ['users'],
    queryFn: listUsers,
    staleTime: 5 * 60_000,
  });
}
