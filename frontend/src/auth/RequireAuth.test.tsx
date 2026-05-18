import { render, screen } from '@testing-library/react';
import { MemoryRouter, Route, Routes } from 'react-router-dom';
import { beforeEach, describe, expect, it } from 'vitest';
import { RequireAuth } from './RequireAuth';
import { useAuthStore } from './useAuthStore';
import type { UserSummary } from '../types/auth';

const USER: UserSummary = { id: 1, username: 'agent1', displayName: '김상담1', role: 'AGENT' };

function renderWithRouter(initial: string) {
  return render(
    <MemoryRouter initialEntries={[initial]}>
      <Routes>
        <Route path="/login" element={<div>Login Page</div>} />
        <Route
          path="/"
          element={
            <RequireAuth>
              <div>Protected Content</div>
            </RequireAuth>
          }
        />
      </Routes>
    </MemoryRouter>
  );
}

describe('RequireAuth', () => {
  beforeEach(() => {
    useAuthStore.setState({ token: null, user: null, hydrated: true });
  });

  it('redirects to /login when unauthenticated', () => {
    renderWithRouter('/');
    expect(screen.getByText('Login Page')).toBeInTheDocument();
    expect(screen.queryByText('Protected Content')).not.toBeInTheDocument();
  });

  it('renders children when authenticated', () => {
    useAuthStore.setState({ token: 'TOK', user: USER, hydrated: true });
    renderWithRouter('/');
    expect(screen.getByText('Protected Content')).toBeInTheDocument();
    expect(screen.queryByText('Login Page')).not.toBeInTheDocument();
  });
});
