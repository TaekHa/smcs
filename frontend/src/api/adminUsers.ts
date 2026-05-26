import { apiClient } from './client';
import type {
  UserAdminItem,
  UserCreateRequest,
  UserCreateResponse,
  UserUpdateRequest,
} from '../types/admin-user';

export async function listAdminUsers(): Promise<UserAdminItem[]> {
  const res = await apiClient.get<UserAdminItem[]>('/admin/users');
  return res.data;
}

/**
 * Admin create — 201 + temporary password (Story 4.4 AC2). The plaintext is in
 * {@link UserCreateResponse#temporaryPassword} and must be surfaced to the operator
 * exactly once (TemporaryPasswordModal); after that point there is no API to recover it.
 */
export async function createAdminUser(req: UserCreateRequest): Promise<UserCreateResponse> {
  const res = await apiClient.post<UserCreateResponse>('/admin/users', req);
  return res.data;
}

/** Partial update — null/undefined fields preserve the existing value (Deviation #9). */
export async function updateAdminUser(id: number, req: UserUpdateRequest): Promise<UserAdminItem> {
  const res = await apiClient.post<UserAdminItem>(`/admin/users/${id}`, req);
  return res.data;
}
