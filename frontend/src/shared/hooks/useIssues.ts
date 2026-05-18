import { keepPreviousData, useQuery } from '@tanstack/react-query';
import { listIssues } from '../../api/issues';
import type { IssueListParams } from '../../types/issue';

/** Paged issue list. 30s stale (architecture §9.7); keeps previous page during refetch. */
export function useIssues(params: IssueListParams) {
  return useQuery({
    queryKey: ['issues', params],
    queryFn: () => listIssues(params),
    staleTime: 30_000,
    placeholderData: keepPreviousData,
  });
}
