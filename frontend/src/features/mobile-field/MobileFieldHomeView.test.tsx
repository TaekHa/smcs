import { render, screen, waitFor } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { MemoryRouter, Route, Routes } from 'react-router-dom';
import { describe, it, expect, vi, beforeEach } from 'vitest';
import type { IssueSummary } from '../../types/issue';

const useMyAssignedMock = vi.fn();
vi.mock('../../shared/hooks/useMyAssigned', () => ({
  useMyAssigned: () => useMyAssignedMock(),
}));

import { MobileFieldHomeView } from './MobileFieldHomeView';

const ISSUES: IssueSummary[] = [
  {
    id: 1,
    title: '엘리베이터 긴급',
    categoryL1Name: '아파트',
    categoryL2Name: '승강기',
    categoryL3Name: '고장',
    priority: 'URGENT',
    status: 'ASSIGNED',
    assigneeName: '이현장1',
    createdAt: '2026-05-20T01:00:00Z',
  },
  {
    id: 2,
    title: '정기 점검',
    categoryL1Name: '아파트',
    categoryL2Name: '설비',
    categoryL3Name: '점검',
    priority: 'LOW',
    status: 'ASSIGNED',
    assigneeName: '이현장1',
    createdAt: '2026-05-20T02:00:00Z',
  },
];

function renderView() {
  return render(
    <MemoryRouter initialEntries={['/m']}>
      <Routes>
        <Route path="/m" element={<MobileFieldHomeView />} />
        <Route path="/m/issues/:id" element={<div>MOBILE DETAIL</div>} />
      </Routes>
    </MemoryRouter>
  );
}

describe('MobileFieldHomeView', () => {
  beforeEach(() => {
    useMyAssignedMock.mockReset();
  });

  it('renders the assigned issues as a card stack (server order preserved)', () => {
    useMyAssignedMock.mockReturnValue({ data: ISSUES, isLoading: false });
    renderView();
    const urgent = screen.getByText('엘리베이터 긴급');
    const low = screen.getByText('정기 점검');
    expect(urgent).toBeInTheDocument();
    expect(low).toBeInTheDocument();
    // server order: URGENT before LOW
    expect(urgent.compareDocumentPosition(low) & Node.DOCUMENT_POSITION_FOLLOWING).toBeTruthy();
  });

  it('navigates to mobile detail on card tap (AC4)', async () => {
    const user = userEvent.setup();
    useMyAssignedMock.mockReturnValue({ data: ISSUES, isLoading: false });
    renderView();
    await user.click(screen.getByText('엘리베이터 긴급'));
    await waitFor(() => expect(screen.getByText('MOBILE DETAIL')).toBeInTheDocument());
  });

  it('shows an empty state when no issues are assigned', () => {
    useMyAssignedMock.mockReturnValue({ data: [], isLoading: false });
    renderView();
    expect(screen.getByText('배정된 이슈가 없습니다')).toBeInTheDocument();
  });
});
