import { useQuery } from '@tanstack/react-query';
import { listUsers } from '../../api/users';
import type { Role, UserSummary } from '../../types/auth';

/**
 * Active users for assignee filter/select. Cached 5min (architecture §9.7).
 * The API already returns active users only; an optional `roles` filter narrows
 * client-side (e.g. FIELD-only for assignment — Story 2.4) without a new request.
 */
export function useUsers(filter?: { roles?: Role[] }) {
  return useQuery<UserSummary[]>({
    queryKey: ['users'],
    queryFn: listUsers,
    staleTime: 5 * 60_000,
    select: filter?.roles
      ? (users) => users.filter((u) => filter.roles!.includes(u.role))
      : undefined,
  });
}
