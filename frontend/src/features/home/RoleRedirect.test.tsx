import { render, screen } from '@testing-library/react';
import { MemoryRouter, Route, Routes } from 'react-router-dom';
import { beforeEach, describe, expect, it } from 'vitest';
import { RoleRedirect } from './RoleRedirect';
import { useAuthStore } from '../../auth/useAuthStore';
import type { Role, UserSummary } from '../../types/auth';

function makeUser(role: Role): UserSummary {
  return { id: 1, username: 'u', displayName: 'D', role };
}

function renderApp() {
  return render(
    <MemoryRouter initialEntries={['/']}>
      <Routes>
        <Route path="/" element={<RoleRedirect />} />
        <Route path="/issues" element={<div>Issues Page</div>} />
        <Route path="/m" element={<div>Mobile Page</div>} />
        <Route path="/login" element={<div>Login Page</div>} />
      </Routes>
    </MemoryRouter>
  );
}

describe('RoleRedirect', () => {
  beforeEach(() => {
    useAuthStore.setState({ token: null, user: null, hydrated: true });
  });

  it('AGENT redirects to /issues', () => {
    useAuthStore.setState({ token: 'TOK', user: makeUser('AGENT'), hydrated: true });
    renderApp();
    expect(screen.getByText('Issues Page')).toBeInTheDocument();
  });

  it('ADMIN redirects to /issues', () => {
    useAuthStore.setState({ token: 'TOK', user: makeUser('ADMIN'), hydrated: true });
    renderApp();
    expect(screen.getByText('Issues Page')).toBeInTheDocument();
  });

  it('FIELD redirects to /m', () => {
    useAuthStore.setState({ token: 'TOK', user: makeUser('FIELD'), hydrated: true });
    renderApp();
    expect(screen.getByText('Mobile Page')).toBeInTheDocument();
  });

  it('unauthenticated redirects to /login (defensive guard)', () => {
    renderApp();
    expect(screen.getByText('Login Page')).toBeInTheDocument();
  });
});
