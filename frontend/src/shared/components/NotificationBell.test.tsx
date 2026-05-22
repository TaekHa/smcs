import { render, screen, waitFor } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { MemoryRouter, Route, Routes } from 'react-router-dom';
import { describe, it, expect, vi, beforeEach } from 'vitest';

const useUnreadCountMock = vi.fn();
vi.mock('../hooks/useNotifications', () => ({
  useUnreadCount: () => useUnreadCountMock(),
}));

import { NotificationBell } from './NotificationBell';

function renderBell() {
  return render(
    <MemoryRouter initialEntries={['/']}>
      <Routes>
        <Route path="/" element={<NotificationBell />} />
        <Route path="/notifications" element={<div>NOTIFICATIONS PAGE</div>} />
      </Routes>
    </MemoryRouter>
  );
}

describe('NotificationBell', () => {
  beforeEach(() => useUnreadCountMock.mockReset());

  it('shows the unread count and an aria-label', () => {
    useUnreadCountMock.mockReturnValue({ data: { count: 3 } });
    renderBell();
    expect(screen.getByRole('button', { name: '미읽음 알림 3건' })).toBeInTheDocument();
    expect(screen.getByText('3')).toBeInTheDocument();
  });

  it('navigates to the notifications page on click', async () => {
    const user = userEvent.setup();
    useUnreadCountMock.mockReturnValue({ data: { count: 0 } });
    renderBell();
    await user.click(screen.getByRole('button', { name: '미읽음 알림 0건' }));
    await waitFor(() => expect(screen.getByText('NOTIFICATIONS PAGE')).toBeInTheDocument());
  });
});
