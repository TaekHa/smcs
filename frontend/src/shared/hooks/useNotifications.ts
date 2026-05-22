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

/** Notification list (newest first). */
export function useNotifications(page = 0) {
  return useQuery({
    queryKey: ['notifications', 'list', page],
    queryFn: () => listNotifications(page),
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
