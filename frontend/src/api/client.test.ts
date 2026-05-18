import { beforeEach, describe, expect, it, vi } from 'vitest';
import type { AxiosAdapter, AxiosError, AxiosResponse, InternalAxiosRequestConfig } from 'axios';
import type { UserSummary } from '../types/auth';

const redirectMock = vi.fn();
vi.mock('./navigation', () => ({
  redirectToLogin: () => redirectMock(),
}));

import { apiClient } from './client';
import { useAuthStore } from '../auth/useAuthStore';

const USER: UserSummary = { id: 1, username: 'agent1', displayName: '김상담1', role: 'AGENT' };

function adapterReturning(status: number, body: unknown): AxiosAdapter {
  return (config) =>
    new Promise((resolve, reject) => {
      const response: AxiosResponse = {
        data: body,
        status,
        statusText: status === 200 ? 'OK' : 'Error',
        headers: {},
        config: config as InternalAxiosRequestConfig,
      };
      if (status >= 200 && status < 300) {
        resolve(response);
      } else {
        const err = new Error(`HTTP ${status}`) as AxiosError;
        err.response = response;
        err.config = config as InternalAxiosRequestConfig;
        reject(err);
      }
    });
}

function adapterCapturing(captured: { config?: InternalAxiosRequestConfig }): AxiosAdapter {
  return (config) => {
    captured.config = config;
    const response: AxiosResponse = {
      data: {},
      status: 200,
      statusText: 'OK',
      headers: {},
      config,
    };
    return Promise.resolve(response);
  };
}

describe('apiClient interceptors', () => {
  beforeEach(() => {
    localStorage.clear();
    useAuthStore.setState({ token: null, user: null, hydrated: true });
    redirectMock.mockReset();
  });

  it('attaches Authorization header when token is set', async () => {
    useAuthStore.getState().setSession('TOK123', USER);
    const captured: { config?: InternalAxiosRequestConfig } = {};
    await apiClient.get('/me', { adapter: adapterCapturing(captured) });
    expect(captured.config?.headers.Authorization).toBe('Bearer TOK123');
  });

  it('omits Authorization header when no token', async () => {
    const captured: { config?: InternalAxiosRequestConfig } = {};
    await apiClient.get('/me', { adapter: adapterCapturing(captured) });
    expect(captured.config?.headers.Authorization).toBeUndefined();
  });

  it('401 on non-login URL triggers logout and redirect', async () => {
    useAuthStore.getState().setSession('TOK', USER);
    await expect(
      apiClient.get('/me', { adapter: adapterReturning(401, { code: 'UNAUTHORIZED' }) })
    ).rejects.toBeTruthy();
    expect(useAuthStore.getState().token).toBeNull();
    expect(useAuthStore.getState().user).toBeNull();
    expect(redirectMock).toHaveBeenCalledTimes(1);
  });

  it('401 on /auth/login does NOT trigger logout', async () => {
    useAuthStore.getState().setSession('TOK', USER);
    await expect(
      apiClient.post(
        '/auth/login',
        { username: 'agent1', password: 'wrong' },
        { adapter: adapterReturning(401, { code: 'INVALID_CREDENTIALS' }) }
      )
    ).rejects.toBeTruthy();
    expect(useAuthStore.getState().token).toBe('TOK');
    expect(redirectMock).not.toHaveBeenCalled();
  });

  it('non-401 errors leave session intact', async () => {
    useAuthStore.getState().setSession('TOK', USER);
    await expect(
      apiClient.get('/me', { adapter: adapterReturning(500, { code: 'INTERNAL_ERROR' }) })
    ).rejects.toBeTruthy();
    expect(useAuthStore.getState().token).toBe('TOK');
    expect(redirectMock).not.toHaveBeenCalled();
  });
});
