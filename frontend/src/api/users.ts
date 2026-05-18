import { apiClient } from './client';
import type { UserSummary } from '../types/auth';

export async function listUsers(): Promise<UserSummary[]> {
  const res = await apiClient.get<UserSummary[]>('/users');
  return res.data;
}
