import { apiClient } from './client';
import type { DashboardStats, StatsPeriod } from '../types/stats';

export async function getDashboardStats(period: StatsPeriod): Promise<DashboardStats> {
  const res = await apiClient.get<DashboardStats>('/stats/dashboard', { params: { period } });
  return res.data;
}
