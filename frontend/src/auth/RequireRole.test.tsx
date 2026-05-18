import { render, screen } from '@testing-library/react';
import { MemoryRouter, Route, Routes } from 'react-router-dom';
import { beforeEach, describe, expect, it } from 'vitest';
import { RequireRole } from './RequireRole';
import { useAuthStore } from './useAuthStore';
import type { Role, UserSummary } from '../types/auth';

function makeUser(role: Role): UserSummary {
  return { id: 1, username: 'u', displayName: 'D', role };
}

function renderWith(initial: string, allowedRoles: Role[]) {
  return render(
    <MemoryRouter initialEntries={[initial]}>
      <Routes>
        <Route path="/login" element={<div>Login Page</div>} />
        <Route path="/403" element={<div>Forbidden Page</div>} />
        <Route
          path="/issues"
          element={
            <RequireRole roles={allowedRoles}>
              <div>Issues Content</div>
            </RequireRole>
          }
        />
      </Routes>
    </MemoryRouter>
  );
}

describe('RequireRole', () => {
  beforeEach(() => {
    useAuthStore.setState({ token: null, user: null, hydrated: true });
  });

  it('renders children when user role is allowed', () => {
    useAuthStore.setState({ token: 'TOK', user: makeUser('AGENT'), hydrated: true });
    renderWith('/issues', ['AGENT', 'ADMIN']);
    expect(screen.getByText('Issues Content')).toBeInTheDocument();
  });

  it('redirects to /403 when role not allowed', () => {
    useAuthStore.setState({ token: 'TOK', user: makeUser('FIELD'), hydrated: true });
    renderWith('/issues', ['AGENT', 'ADMIN']);
    expect(screen.getByText('Forbidden Page')).toBeInTheDocument();
    expect(screen.queryByText('Issues Content')).not.toBeInTheDocument();
  });

  it('redirects to /login when unauthenticated', () => {
    renderWith('/issues', ['AGENT', 'ADMIN']);
    expect(screen.getByText('Login Page')).toBeInTheDocument();
  });
});
