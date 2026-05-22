import { render, screen } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { QueryClient, QueryClientProvider } from '@tanstack/react-query';
import { describe, it, expect, vi, beforeEach } from 'vitest';
import type { UserSummary } from '../../types/auth';

vi.mock('../../api/users', () => ({ listUsers: vi.fn() }));
import { listUsers } from '../../api/users';
import { UserSelect } from './UserSelect';

const USERS: UserSummary[] = [
  { id: 1, username: 'agent1', displayName: '김상담1', role: 'AGENT' },
  { id: 2, username: 'field1', displayName: '이현장1', role: 'FIELD' },
  { id: 3, username: 'field2', displayName: '이현장2', role: 'FIELD' },
];

function renderSelect(onChange = vi.fn()) {
  const queryClient = new QueryClient();
  render(
    <QueryClientProvider client={queryClient}>
      <UserSelect onChange={onChange} filter={{ roles: ['FIELD'] }} placeholder="현장 담당자 선택" />
    </QueryClientProvider>
  );
  return onChange;
}

describe('UserSelect', () => {
  beforeEach(() => {
    vi.mocked(listUsers).mockReset();
    vi.mocked(listUsers).mockResolvedValue(USERS);
  });

  it('renders only FIELD users as options (client-side role filter)', async () => {
    const user = userEvent.setup();
    renderSelect();
    await user.click(screen.getByRole('combobox'));
    expect(await screen.findByText('이현장1')).toBeInTheDocument();
    expect(screen.getByText('이현장2')).toBeInTheDocument();
    expect(screen.queryByText('김상담1')).not.toBeInTheDocument();
  });

  it('calls onChange with the selected user id', async () => {
    const user = userEvent.setup();
    const onChange = renderSelect();
    await user.click(screen.getByRole('combobox'));
    await user.click(await screen.findByText('이현장1'));
    expect(onChange).toHaveBeenCalledWith(2, expect.anything());
  });
});
