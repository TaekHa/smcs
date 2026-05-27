import type { Role } from './auth';

export interface UserAdminItem {
  id: number;
  username: string;
  displayName: string;
  role: Role;
  phone: string | null;
  active: boolean;
  createdAt: string;
}

export interface UserCreateRequest {
  username: string;
  displayName: string;
  role: Role;
  phone?: string;
}

export interface UserUpdateRequest {
  displayName?: string;
  role?: Role;
  phone?: string;
  active?: boolean;
}

export interface UserCreateResponse {
  user: UserAdminItem;
  /** One-time plaintext — never returned again. Must be displayed once, then forgotten. */
  temporaryPassword: string;
}
