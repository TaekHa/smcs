import { apiClient } from './client';
import type { UserSummary } from '../types/auth';

export async function getMe(): Promise<UserSummary> {
  const res = await apiClient.get<UserSummary>('/me');
  return res.data;
}
