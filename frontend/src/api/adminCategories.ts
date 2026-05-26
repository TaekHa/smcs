import { apiClient } from './client';
import type { CategoryAdminItem, CategoryLevel, CategoryUpsertRequest } from '../types/category';

/** Admin lookup — includes inactive rows. {@link listCategories} keeps active-only semantics. */
export async function listAdminCategories(level: CategoryLevel): Promise<CategoryAdminItem[]> {
  const res = await apiClient.get<CategoryAdminItem[]>('/admin/categories', { params: { level } });
  return res.data;
}

/**
 * Single upsert (PRD §6, Story 4.5 Deviation #2): id null creates (201), id present updates (200).
 * Caller is responsible for query-cache invalidation on both ['admin-categories', level] AND
 * ['categories', level] so the public issue-form dropdown reflects active toggles immediately.
 */
export async function upsertCategory(req: CategoryUpsertRequest): Promise<CategoryAdminItem> {
  const res = await apiClient.post<CategoryAdminItem>('/admin/categories', req);
  return res.data;
}
