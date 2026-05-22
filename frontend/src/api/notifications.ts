import { apiClient } from './client';
import type { Notification, UnreadCount } from '../types/notification';
import type { Page } from '../types/issue';

export async function getUnreadCount(): Promise<UnreadCount> {
  const res = await apiClient.get<UnreadCount>('/notifications/unread-count');
  return res.data;
}

export async function listNotifications(page = 0, size = 20): Promise<Page<Notification>> {
  const res = await apiClient.get<Page<Notification>>('/notifications', { params: { page, size } });
  return res.data;
}

export async function markRead(id: number): Promise<void> {
  await apiClient.post(`/notifications/${id}/read`);
}

export async function markAllRead(): Promise<void> {
  await apiClient.post('/notifications/read-all');
}
