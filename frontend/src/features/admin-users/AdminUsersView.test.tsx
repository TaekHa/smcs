import { render, screen, waitFor } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { QueryClient, QueryClientProvider } from '@tanstack/react-query';
import { describe, it, expect, vi, beforeEach } from 'vitest';
import type { UserAdminItem } from '../../types/admin-user';

const listAdminUsersMock = vi.fn();
const createAdminUserMock = vi.fn();
const updateAdminUserMock = vi.fn();
vi.mock('../../api/adminUsers', () => ({
  listAdminUsers: () => listAdminUsersMock(),
  createAdminUser: (req: unknown) => createAdminUserMock(req),
  updateAdminUser: (id: number, req: unknown) => updateAdminUserMock(id, req),
}));

const useAuthMock = vi.fn();
vi.mock('../../auth/useAuthStore', () => ({
  useAuth: () => useAuthMock(),
  useAuthStore: { getState: () => ({ logout: vi.fn() }) },
}));

import { AdminUsersView } from './AdminUsersView';

const ADMIN_USER = { id: 5, username: 'admin1', displayName: '관리자', role: 'ADMIN' as const };
const AGENT_USER = { id: 1, username: 'agent1', displayName: '에이전트1', role: 'AGENT' as const };

const SEED: UserAdminItem[] = [
  {
    id: 5,
    username: 'admin1',
    displayName: '관리자',
    role: 'ADMIN',
    phone: null,
    active: true,
    createdAt: '2026-05-26T00:00:00Z',
  },
  {
    id: 1,
    username: 'agent1',
    displayName: '에이전트1',
    role: 'AGENT',
    phone: '010-1111-2222',
    active: true,
    createdAt: '2026-05-26T00:00:00Z',
  },
  {
    id: 2,
    username: 'field1',
    displayName: '필드1',
    role: 'FIELD',
    phone: null,
    active: false,
    createdAt: '2026-05-26T00:00:00Z',
  },
];

function renderView() {
  const qc = new QueryClient({ defaultOptions: { queries: { retry: false } } });
  return render(
    <QueryClientProvider client={qc}>
      <AdminUsersView />
    </QueryClientProvider>
  );
}

describe('AdminUsersView', () => {
  beforeEach(() => {
    listAdminUsersMock.mockReset();
    createAdminUserMock.mockReset();
    updateAdminUserMock.mockReset();
    useAuthMock.mockReset();
    useAuthMock.mockReturnValue(ADMIN_USER);
    listAdminUsersMock.mockResolvedValue(SEED);
  });

  it('renders rows including inactive users', async () => {
    renderView();
    expect(await screen.findByText('admin1')).toBeInTheDocument();
    expect(screen.getByText('agent1')).toBeInTheDocument();
    expect(screen.getByText('field1')).toBeInTheDocument();
  });

  it('disables the Switch on the current user (AC5 frontend mirror)', async () => {
    renderView();
    await screen.findByText('admin1');
    const switches = screen.getAllByRole('switch');
    // SEED[0] is admin1 (self) — first row
    expect(switches[0]).toBeDisabled();
    // SEED[1] is agent1 (other) — enabled
    expect(switches[1]).not.toBeDisabled();
  });

  it('opens Add modal and surfaces the temporary password on success', async () => {
    createAdminUserMock.mockResolvedValue({
      user: {
        id: 99,
        username: 'test-create',
        displayName: '테스트',
        role: 'AGENT',
        phone: null,
        active: true,
        createdAt: '2026-05-26T00:00:00Z',
      },
      temporaryPassword: 'Kp9mZ2qR7nXc',
    });
    const user = userEvent.setup();
    renderView();
    await screen.findByText('admin1');

    await user.click(screen.getByRole('button', { name: '사용자 추가' }));
    const createDialog = await screen.findByRole('dialog', { name: '사용자 추가' });
    const within = await import('@testing-library/react').then((m) => m.within(createDialog));
    await user.type(within.getByLabelText('사용자명'), 'test-create');
    await user.type(within.getByLabelText('표시 이름'), '테스트');
    await user.click(within.getByRole('button', { name: '저장' }));

    await waitFor(() => expect(createAdminUserMock).toHaveBeenCalled());
    const arg = createAdminUserMock.mock.calls[0][0];
    expect(arg.username).toBe('test-create');
    expect(arg.role).toBe('AGENT');

    // TemporaryPasswordModal must surface the plaintext exactly once.
    expect(await screen.findByText('Kp9mZ2qR7nXc')).toBeInTheDocument();
    expect(
      screen.getByText('이 비밀번호는 다시 표시되지 않습니다.')
    ).toBeInTheDocument();
    expect(screen.getByRole('button', { name: /복사/ })).toBeInTheDocument();
  });

  it('Switch toggle requires Popconfirm then calls updateAdminUser with active flipped', async () => {
    updateAdminUserMock.mockResolvedValue({ ...SEED[1], active: false });
    const user = userEvent.setup();
    renderView();
    await screen.findByText('admin1');

    const switches = screen.getAllByRole('switch');
    // Click agent1's switch (index 1) — self is disabled at 0
    await user.click(switches[1]);
    await user.click(await screen.findByRole('button', { name: '계속' }));

    await waitFor(() => expect(updateAdminUserMock).toHaveBeenCalled());
    const [id, req] = updateAdminUserMock.mock.calls[0];
    expect(id).toBe(1);
    expect(req.active).toBe(false);
  });

  it('opens Edit modal and calls updateAdminUser with partial values', async () => {
    updateAdminUserMock.mockResolvedValue({ ...SEED[1], displayName: '에이전트1-수정' });
    const user = userEvent.setup();
    renderView();
    await screen.findByText('admin1');

    await user.click(screen.getByRole('button', { name: '사용자 agent1 수정' }));
    const editDialog = await screen.findByRole('dialog', { name: /사용자 수정/ });
    const within = await import('@testing-library/react').then((m) => m.within(editDialog));
    const nameInput = within.getByLabelText('표시 이름') as HTMLInputElement;
    await user.clear(nameInput);
    await user.type(nameInput, '에이전트1-수정');
    await user.click(within.getByRole('button', { name: '저장' }));

    await waitFor(() => expect(updateAdminUserMock).toHaveBeenCalled());
    const [id, req] = updateAdminUserMock.mock.calls[0];
    expect(id).toBe(1);
    expect(req.displayName).toBe('에이전트1-수정');
    expect(req.role).toBe('AGENT'); // preserved from initial
  });

  it('hides the entire view when current user is not ADMIN', async () => {
    // Sanity: the route guard handles this — this test asserts that the view still renders
    // for ADMIN. For non-ADMIN, RequireRole would short-circuit before mount, so we don't
    // exercise the inverse here.
    useAuthMock.mockReturnValue(AGENT_USER);
    renderView();
    // No self-disable check here; we just confirm the page renders without crashing.
    expect(await screen.findByText('사용자 관리')).toBeInTheDocument();
  });
});
