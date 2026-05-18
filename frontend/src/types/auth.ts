export type Role = 'AGENT' | 'FIELD' | 'ADMIN';

export interface UserSummary {
  id: number;
  username: string;
  displayName: string;
  role: Role;
}

export interface LoginRequest {
  username: string;
  password: string;
}

export interface LoginResponse {
  token: string;
  expiresInSeconds: number;
  user: UserSummary;
}

export interface ApiError {
  code: string;
  message: string;
  traceId: string | null;
}
