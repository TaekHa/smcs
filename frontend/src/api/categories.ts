import { apiClient } from './client';
import type { CategoryOption } from '../types/issue';

export async function listCategories(level: 1 | 2 | 3): Promise<CategoryOption[]> {
  const res = await apiClient.get<CategoryOption[]>('/categories', { params: { level } });
  return res.data;
}
