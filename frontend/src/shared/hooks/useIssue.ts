import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query';
import { addComment, getIssue, listIssueEvents } from '../../api/issues';
import type { AddCommentRequest } from '../../types/issue';

/** Issue detail. Short stale — freshness-first for an internal tool (§7.3 no-store). */
export function useIssue(id: number) {
  return useQuery({
    queryKey: ['issue', id],
    queryFn: () => getIssue(id),
    staleTime: 15_000,
  });
}

/** Activity log, newest-first (AC4). */
export function useIssueEvents(id: number) {
  return useQuery({
    queryKey: ['issue', id, 'events'],
    queryFn: () => listIssueEvents(id),
    staleTime: 15_000,
  });
}

/**
 * Add a comment. On success, invalidate the issue key — partial matching also covers
 * ['issue', id, 'events'] — so the comment list and activity log auto-refresh (AC3).
 */
export function useAddComment(id: number) {
  const queryClient = useQueryClient();
  return useMutation({
    mutationFn: (req: AddCommentRequest) => addComment(id, req),
    onSuccess: () => {
      queryClient.invalidateQueries({ queryKey: ['issue', id] });
    },
  });
}
