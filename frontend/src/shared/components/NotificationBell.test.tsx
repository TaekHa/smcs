import { render, screen, waitFor } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { MemoryRouter, Route, Routes } from 'react-router-dom';
import { describe, it, expect, vi, beforeEach } from 'vitest';
import type { Notification } from '../../types/notification';
import type { Page } from '../../types/issue';

const useUnreadCountMock = vi.fn();
const useNotificationsMock = vi.fn();
const markReadMock = vi.fn();

vi.mock('../hooks/useNotifications', () => ({
  useUnreadCount: () => useUnreadCountMock(),
  useNotifications: () => useNotificationsMock(),
  useMarkRead: () => ({ mutate: markReadMock }),
}));

import { NotificationBell } from './NotificationBell';

const NOTIF_ISSUE: Notification = {
  id: 10,
  kind: 'ISSUE_ASSIGNED',
  issueId: 42,
  actorName: '김상담1',
  message: '김상담1님이 #42 이슈를 배정했습니다',
  readAt: null,
  createdAt: new Date(Date.now() - 5 * 60_000).toISOString(),
};

const NOTIF_REPORT: Notification = {
  id: 11,
  kind: 'REPORT_READY',
  issueId: null,
  actorName: null,
  message: '어제 일간 보고서(2026-05-22)가 준비되었습니다',
  readAt: null,
  createdAt: new Date(Date.now() - 2 * 60 * 60_000).toISOString(),
};

function pageOf(items: Notification[]): Page<Notification> {
  return {
    content: items,
    totalElements: items.length,
    totalPages: 1,
    number: 0,
    size: 20,
  };
}

function renderBell() {
  return render(
    <MemoryRouter initialEntries={['/']}>
      <Routes>
        <Route path="/" element={<NotificationBell />} />
        <Route path="/notifications" element={<div>NOTIFICATIONS PAGE</div>} />
        <Route path="/issues/:id" element={<div>ISSUE 42 DETAIL</div>} />
        <Route path="/reports" element={<div>REPORTS ARCHIVE</div>} />
      </Routes>
    </MemoryRouter>
  );
}

describe('NotificationBell', () => {
  beforeEach(() => {
    useUnreadCountMock.mockReset();
    useNotificationsMock.mockReset();
    markReadMock.mockReset();
    useNotificationsMock.mockReturnValue({ data: pageOf([NOTIF_ISSUE]) });
  });

  it('shows the unread count and an aria-label', () => {
    useUnreadCountMock.mockReturnValue({ data: { count: 3 } });
    renderBell();
    expect(screen.getByRole('button', { name: '미읽음 알림 3건' })).toBeInTheDocument();
    expect(screen.getByText('3')).toBeInTheDocument();
  });

  it('opens a dropdown with recent notifications on click', async () => {
    const user = userEvent.setup();
    useUnreadCountMock.mockReturnValue({ data: { count: 1 } });
    renderBell();
    await user.click(screen.getByRole('button', { name: '미읽음 알림 1건' }));
    await waitFor(() =>
      expect(screen.getByText('김상담1님이 #42 이슈를 배정했습니다')).toBeInTheDocument()
    );
    expect(screen.getByRole('region', { name: '알림 미리보기' })).toBeInTheDocument();
    expect(screen.getByText('(1 미읽음)')).toBeInTheDocument();
  });

  it('navigates to /notifications when "모두 보기" is clicked', async () => {
    const user = userEvent.setup();
    useUnreadCountMock.mockReturnValue({ data: { count: 0 } });
    renderBell();
    await user.click(screen.getByRole('button', { name: '미읽음 알림 0건' }));
    await user.click(await screen.findByRole('button', { name: '모두 보기' }));
    await waitFor(() => expect(screen.getByText('NOTIFICATIONS PAGE')).toBeInTheDocument());
  });

  it('marks read and navigates to the issue when an item is clicked', async () => {
    const user = userEvent.setup();
    useUnreadCountMock.mockReturnValue({ data: { count: 1 } });
    renderBell();
    await user.click(screen.getByRole('button', { name: '미읽음 알림 1건' }));
    await user.click(await screen.findByText('김상담1님이 #42 이슈를 배정했습니다'));
    expect(markReadMock).toHaveBeenCalledWith(10);
    await waitFor(() => expect(screen.getByText('ISSUE 42 DETAIL')).toBeInTheDocument());
  });

  it('routes REPORT_READY notifications (issueId=null) to /reports', async () => {
    const user = userEvent.setup();
    useUnreadCountMock.mockReturnValue({ data: { count: 1 } });
    useNotificationsMock.mockReturnValue({ data: pageOf([NOTIF_REPORT]) });
    renderBell();
    await user.click(screen.getByRole('button', { name: '미읽음 알림 1건' }));
    await user.click(
      await screen.findByText('어제 일간 보고서(2026-05-22)가 준비되었습니다')
    );
    expect(markReadMock).toHaveBeenCalledWith(11);
    await waitFor(() => expect(screen.getByText('REPORTS ARCHIVE')).toBeInTheDocument());
  });

  it('shows an empty state when there are no notifications', async () => {
    const user = userEvent.setup();
    useUnreadCountMock.mockReturnValue({ data: { count: 0 } });
    useNotificationsMock.mockReturnValue({ data: pageOf([]) });
    renderBell();
    await user.click(screen.getByRole('button', { name: '미읽음 알림 0건' }));
    expect(await screen.findByText('새 알림이 없습니다')).toBeInTheDocument();
  });

  it('limits dropdown to 10 items even if more are available', async () => {
    const many: Notification[] = Array.from({ length: 15 }, (_, i) => ({
      ...NOTIF_ISSUE,
      id: 100 + i,
      message: `알림 #${100 + i}`,
    }));
    const user = userEvent.setup();
    useUnreadCountMock.mockReturnValue({ data: { count: 15 } });
    useNotificationsMock.mockReturnValue({ data: pageOf(many) });
    renderBell();
    await user.click(screen.getByRole('button', { name: '미읽음 알림 15건' }));
    await screen.findByText('알림 #100');
    expect(screen.getByText('알림 #100')).toBeInTheDocument();
    expect(screen.getByText('알림 #109')).toBeInTheDocument();
    expect(screen.queryByText('알림 #110')).not.toBeInTheDocument();
  });
});
