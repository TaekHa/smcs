import { render, screen } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { QueryClient, QueryClientProvider } from '@tanstack/react-query';
import { MemoryRouter, Route, Routes } from 'react-router-dom';
import { beforeEach, describe, expect, it, vi } from 'vitest';
import { AppLayout } from './AppLayout';
import { useAuthStore } from '../auth/useAuthStore';
import type { Role, UserSummary } from '../types/auth';

// AppLayout's NotificationBell polls unread-count and fetches the recent list for the
// Story 4.1 dropdown; stub all consumed hooks to avoid network.
vi.mock('../shared/hooks/useNotifications', () => ({
  useUnreadCount: () => ({ data: { count: 0 } }),
  useNotifications: () => ({
    data: { content: [], totalElements: 0, totalPages: 0, number: 0, size: 20 },
  }),
  useMarkRead: () => ({ mutate: vi.fn() }),
}));

function makeUser(role: Role, displayName = 'TestUser'): UserSummary {
  return { id: 1, username: 'u', displayName, role };
}

function renderWithRole(role: Role) {
  useAuthStore.setState({ token: 'TOK', user: makeUser(role, '김상담1'), hydrated: true });
  const queryClient = new QueryClient({ defaultOptions: { queries: { retry: false } } });
  return render(
    <QueryClientProvider client={queryClient}>
      <MemoryRouter initialEntries={['/issues']}>
        <Routes>
          <Route
            path="/issues"
            element={
              <AppLayout>
                <div>Page Content</div>
              </AppLayout>
            }
          />
          <Route path="/login" element={<div>Login Page</div>} />
        </Routes>
      </MemoryRouter>
    </QueryClientProvider>
  );
}

describe('AppLayout', () => {
  beforeEach(() => {
    useAuthStore.setState({ token: null, user: null, hydrated: true });
  });

  it('AGENT sees 이슈 link and not 내 작업', () => {
    renderWithRole('AGENT');
    expect(screen.getByRole('link', { name: '이슈' })).toBeInTheDocument();
    expect(screen.queryByRole('link', { name: '내 작업' })).not.toBeInTheDocument();
  });

  it('FIELD sees 내 작업 link and not 이슈', () => {
    renderWithRole('FIELD');
    expect(screen.getByRole('link', { name: '내 작업' })).toBeInTheDocument();
    expect(screen.queryByRole('link', { name: '이슈' })).not.toBeInTheDocument();
  });

  it('ADMIN sees both 이슈 and 내 작업 links', () => {
    renderWithRole('ADMIN');
    expect(screen.getByRole('link', { name: '이슈' })).toBeInTheDocument();
    expect(screen.getByRole('link', { name: '내 작업' })).toBeInTheDocument();
  });

  it('renders user displayName, role tag, and logout button', () => {
    renderWithRole('AGENT');
    expect(screen.getByText('김상담1')).toBeInTheDocument();
    expect(screen.getByText('AGENT')).toBeInTheDocument();
    expect(screen.getByRole('button', { name: '로그아웃' })).toBeInTheDocument();
  });

  it('logout button clears session and navigates to /login', async () => {
    const user = userEvent.setup();
    renderWithRole('AGENT');
    await user.click(screen.getByRole('button', { name: '로그아웃' }));
    expect(screen.getByText('Login Page')).toBeInTheDocument();
    expect(useAuthStore.getState().user).toBeNull();
  });

  it('renders children content', () => {
    renderWithRole('AGENT');
    expect(screen.getByText('Page Content')).toBeInTheDocument();
  });
});
