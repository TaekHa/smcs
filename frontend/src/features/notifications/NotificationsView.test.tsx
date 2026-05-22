import { render, screen, waitFor } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { MemoryRouter, Route, Routes } from 'react-router-dom';
import { describe, it, expect, vi, beforeEach } from 'vitest';
import type { Notification } from '../../types/notification';
import type { Page } from '../../types/issue';

const useNotificationsMock = vi.fn();
const markReadMock = vi.fn();
const markAllReadMock = vi.fn();
vi.mock('../../shared/hooks/useNotifications', () => ({
  useNotifications: () => useNotificationsMock(),
  useMarkRead: () => ({ mutate: markReadMock }),
  useMarkAllRead: () => ({ mutate: markAllReadMock, isPending: false }),
}));

import { NotificationsView } from './NotificationsView';

const PAGE: Page<Notification> = {
  content: [
    {
      id: 10,
      kind: 'ISSUE_ASSIGNED',
      issueId: 42,
      actorName: '김상담1',
      message: '김상담1님이 #42 이슈를 배정했습니다',
      readAt: null,
      createdAt: '2026-05-22T01:00:00Z',
    },
  ],
  totalElements: 1,
  totalPages: 1,
  number: 0,
  size: 20,
};

function renderView() {
  return render(
    <MemoryRouter initialEntries={['/notifications']}>
      <Routes>
        <Route path="/notifications" element={<NotificationsView />} />
        <Route path="/issues/:id" element={<div>ISSUE 42 DETAIL</div>} />
      </Routes>
    </MemoryRouter>
  );
}

describe('NotificationsView', () => {
  beforeEach(() => {
    useNotificationsMock.mockReset();
    markReadMock.mockReset();
    markAllReadMock.mockReset();
    useNotificationsMock.mockReturnValue({ data: PAGE, isLoading: false });
  });

  it('renders notifications', () => {
    renderView();
    expect(screen.getByText('김상담1님이 #42 이슈를 배정했습니다')).toBeInTheDocument();
  });

  it('marks read and navigates to the issue on click (AC4)', async () => {
    const user = userEvent.setup();
    renderView();
    await user.click(screen.getByText('김상담1님이 #42 이슈를 배정했습니다'));
    expect(markReadMock).toHaveBeenCalledWith(10);
    await waitFor(() => expect(screen.getByText('ISSUE 42 DETAIL')).toBeInTheDocument());
  });

  it('marks all read (AC5)', async () => {
    const user = userEvent.setup();
    renderView();
    await user.click(screen.getByRole('button', { name: '모두 읽음' }));
    expect(markAllReadMock).toHaveBeenCalled();
  });

  it('shows empty state when there are no notifications', () => {
    useNotificationsMock.mockReturnValue({
      data: { content: [], totalElements: 0, totalPages: 0, number: 0, size: 20 },
      isLoading: false,
    });
    renderView();
    expect(screen.getByText('알림이 없습니다')).toBeInTheDocument();
  });
});
