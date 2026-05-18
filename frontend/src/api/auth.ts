import { apiClient } from './client';
import type { LoginRequest, LoginResponse } from '../types/auth';

export async function login(req: LoginRequest): Promise<LoginResponse> {
  const res = await apiClient.post<LoginResponse>('/auth/login', req);
  return res.data;
}
