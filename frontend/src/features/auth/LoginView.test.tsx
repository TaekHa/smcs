import { render, screen, waitFor } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { MemoryRouter, Route, Routes } from 'react-router-dom';
import { beforeEach, describe, expect, it, vi } from 'vitest';
import type { LoginResponse } from '../../types/auth';
import { useAuthStore } from '../../auth/useAuthStore';

const loginMock = vi.fn();
vi.mock('../../api/auth', () => ({
  login: (req: unknown) => loginMock(req),
}));

import { LoginView } from './LoginView';

function renderApp() {
  return render(
    <MemoryRouter initialEntries={['/login']}>
      <Routes>
        <Route path="/login" element={<LoginView />} />
        <Route path="/" element={<div>Home Page</div>} />
      </Routes>
    </MemoryRouter>
  );
}

const successResponse: LoginResponse = {
  token: 'TOK',
  expiresInSeconds: 28800,
  user: { id: 1, username: 'agent1', displayName: '김상담1', role: 'AGENT' },
};

describe('LoginView', () => {
  beforeEach(() => {
    loginMock.mockReset();
    localStorage.clear();
    useAuthStore.setState({ token: null, user: null, hydrated: true });
  });

  it('renders username/password fields and submit button', () => {
    renderApp();
    expect(screen.getByLabelText('사용자명')).toBeInTheDocument();
    expect(screen.getByLabelText('비밀번호')).toBeInTheDocument();
    expect(screen.getByRole('button', { name: '로그인' })).toBeInTheDocument();
  });

  it('shows validation errors on empty submit', async () => {
    const user = userEvent.setup();
    renderApp();
    await user.click(screen.getByRole('button', { name: '로그인' }));
    expect(await screen.findByText('사용자명을 입력하세요.')).toBeInTheDocument();
    expect(screen.getByText('비밀번호를 입력하세요.')).toBeInTheDocument();
  });

  it('on success stores session and navigates to /', async () => {
    loginMock.mockResolvedValueOnce(successResponse);
    const user = userEvent.setup();
    renderApp();
    await user.type(screen.getByLabelText('사용자명'), 'agent1');
    await user.type(screen.getByLabelText('비밀번호'), 'dev1234');
    await user.click(screen.getByRole('button', { name: '로그인' }));

    await waitFor(() => {
      expect(screen.getByText('Home Page')).toBeInTheDocument();
    });
    const state = useAuthStore.getState();
    expect(state.token).toBe('TOK');
    expect(state.user?.username).toBe('agent1');
  });

  it('maps 401 INVALID_CREDENTIALS to Korean message', async () => {
    loginMock.mockRejectedValueOnce({
      response: { status: 401, data: { code: 'INVALID_CREDENTIALS', message: 'x', traceId: 't1' } },
    });
    const user = userEvent.setup();
    renderApp();
    await user.type(screen.getByLabelText('사용자명'), 'agent1');
    await user.type(screen.getByLabelText('비밀번호'), 'wrong');
    await user.click(screen.getByRole('button', { name: '로그인' }));

    expect(await screen.findByText('사용자명 또는 비밀번호를 확인하세요.')).toBeInTheDocument();
  });

  it('maps 423 ACCOUNT_LOCKED to lockout message', async () => {
    loginMock.mockRejectedValueOnce({
      response: { status: 423, data: { code: 'ACCOUNT_LOCKED', message: 'x', traceId: null } },
    });
    const user = userEvent.setup();
    renderApp();
    await user.type(screen.getByLabelText('사용자명'), 'agent1');
    await user.type(screen.getByLabelText('비밀번호'), 'x');
    await user.click(screen.getByRole('button', { name: '로그인' }));

    expect(
      await screen.findByText(/계정이 잠겼습니다.*10분 후 다시 시도/)
    ).toBeInTheDocument();
  });

  it('maps 429 RATE_LIMIT_EXCEEDED to rate limit message', async () => {
    loginMock.mockRejectedValueOnce({
      response: { status: 429, data: { code: 'RATE_LIMIT_EXCEEDED', message: 'x', traceId: null } },
    });
    const user = userEvent.setup();
    renderApp();
    await user.type(screen.getByLabelText('사용자명'), 'agent1');
    await user.type(screen.getByLabelText('비밀번호'), 'x');
    await user.click(screen.getByRole('button', { name: '로그인' }));

    expect(await screen.findByText('요청이 너무 많습니다. 잠시 후 다시 시도하세요.')).toBeInTheDocument();
  });

  it('shows network error message when response missing', async () => {
    loginMock.mockRejectedValueOnce(new Error('Network Error'));
    const user = userEvent.setup();
    renderApp();
    await user.type(screen.getByLabelText('사용자명'), 'agent1');
    await user.type(screen.getByLabelText('비밀번호'), 'x');
    await user.click(screen.getByRole('button', { name: '로그인' }));

    expect(await screen.findByText('서버에 연결할 수 없습니다.')).toBeInTheDocument();
  });
});
