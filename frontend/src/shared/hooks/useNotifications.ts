import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query';
import {
  getUnreadCount,
  listNotifications,
  markAllRead,
  markRead,
} from '../../api/notifications';

/** Unread count for the bell badge. 30s polling, paused on hidden tabs (§8.5). */
export function useUnreadCount() {
  return useQuery({
    queryKey: ['notifications', 'unread-count'],
    queryFn: getUnreadCount,
    refetchInterval: 30_000,
    refetchIntervalInBackground: false,
  });
}

/**
 * Notification list (newest first). Story 4.1 lets the bell dropdown gate fetching
 * via `enabled` so we only hit the API when the popover opens (architecture §8.5).
 */
export function useNotifications(page = 0, options: { enabled?: boolean } = {}) {
  return useQuery({
    queryKey: ['notifications', 'list', page],
    queryFn: () => listNotifications(page),
    enabled: options.enabled ?? true,
  });
}

export function useMarkRead() {
  const queryClient = useQueryClient();
  return useMutation({
    mutationFn: (id: number) => markRead(id),
    onSuccess: () => queryClient.invalidateQueries({ queryKey: ['notifications'] }),
  });
}

export function useMarkAllRead() {
  const queryClient = useQueryClient();
  return useMutation({
    mutationFn: markAllRead,
    onSuccess: () => queryClient.invalidateQueries({ queryKey: ['notifications'] }),
  });
}
