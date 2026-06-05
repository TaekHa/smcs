import { create } from 'zustand';
import type { UserSummary } from '../types/auth';
import { queryClient } from '../shared/lib/queryClient';

const STORAGE_KEY = 'smcs.auth';

interface AuthState {
  token: string | null;
  user: UserSummary | null;
  hydrated: boolean;
  setSession: (token: string, user: UserSummary) => void;
  logout: () => void;
  hydrate: () => void;
}

export const useAuthStore = create<AuthState>((set) => ({
  token: null,
  user: null,
  hydrated: false,
  setSession: (token, user) => {
    localStorage.setItem(STORAGE_KEY, JSON.stringify({ token, user }));
    set({ token, user });
  },
  logout: () => {
    localStorage.removeItem(STORAGE_KEY);
    // Drop all cached per-user data so the next session starts clean — both the
    // logout button (AppLayout) and the 401 interceptor (api/client.ts) funnel
    // through here (Story 4.7 Phase 2 UT-001 cross-user cache leak).
    queryClient.clear();
    set({ token: null, user: null });
  },
  hydrate: () => {
    try {
      const raw = localStorage.getItem(STORAGE_KEY);
      if (raw) {
        const parsed = JSON.parse(raw) as { token?: string; user?: UserSummary };
        if (typeof parsed.token === 'string' && parsed.user && typeof parsed.user.id === 'number') {
          set({ token: parsed.token, user: parsed.user, hydrated: true });
          return;
        }
      }
    } catch {
      // corrupted localStorage — fall through to empty state
    }
    set({ hydrated: true });
  },
}));

export const useAuth = (): UserSummary | null => useAuthStore((s) => s.user);
export const useHydrated = (): boolean => useAuthStore((s) => s.hydrated);
