import { beforeEach, describe, expect, it } from 'vitest';
import { useAuthStore } from './useAuthStore';
import type { UserSummary } from '../types/auth';

const STORAGE_KEY = 'smcs.auth';
const USER: UserSummary = { id: 1, username: 'agent1', displayName: '김상담1', role: 'AGENT' };
const TOKEN = 'fake.jwt.token';

describe('useAuthStore', () => {
  beforeEach(() => {
    localStorage.clear();
    useAuthStore.setState({ token: null, user: null, hydrated: false });
  });

  it('setSession stores state and persists to localStorage', () => {
    useAuthStore.getState().setSession(TOKEN, USER);
    const state = useAuthStore.getState();
    expect(state.token).toBe(TOKEN);
    expect(state.user).toEqual(USER);
    const raw = localStorage.getItem(STORAGE_KEY);
    expect(raw).not.toBeNull();
    expect(JSON.parse(raw!)).toEqual({ token: TOKEN, user: USER });
  });

  it('logout clears state and localStorage', () => {
    useAuthStore.getState().setSession(TOKEN, USER);
    useAuthStore.getState().logout();
    expect(useAuthStore.getState().token).toBeNull();
    expect(useAuthStore.getState().user).toBeNull();
    expect(localStorage.getItem(STORAGE_KEY)).toBeNull();
  });

  it('hydrate restores from valid localStorage', () => {
    localStorage.setItem(STORAGE_KEY, JSON.stringify({ token: TOKEN, user: USER }));
    useAuthStore.getState().hydrate();
    const state = useAuthStore.getState();
    expect(state.token).toBe(TOKEN);
    expect(state.user).toEqual(USER);
    expect(state.hydrated).toBe(true);
  });

  it('hydrate ignores corrupted localStorage and still marks hydrated', () => {
    localStorage.setItem(STORAGE_KEY, '{not valid json');
    useAuthStore.getState().hydrate();
    const state = useAuthStore.getState();
    expect(state.token).toBeNull();
    expect(state.user).toBeNull();
    expect(state.hydrated).toBe(true);
  });

  it('hydrate ignores partial payload missing user', () => {
    localStorage.setItem(STORAGE_KEY, JSON.stringify({ token: TOKEN }));
    useAuthStore.getState().hydrate();
    expect(useAuthStore.getState().token).toBeNull();
    expect(useAuthStore.getState().hydrated).toBe(true);
  });
});
