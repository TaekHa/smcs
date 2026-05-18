import { render, screen, waitFor } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { MemoryRouter, Route, Routes } from 'react-router-dom';
import { describe, it, expect, vi, beforeEach } from 'vitest';
import type { Page, IssueSummary } from '../../types/issue';

const useIssuesMock = vi.fn();
vi.mock('../../shared/hooks/useIssues', () => ({
  useIssues: (p: unknown) => useIssuesMock(p),
}));
vi.mock('../../shared/hooks/useCategories', () => ({
  useCategories: (lvl: number) => ({
    data: [{ id: lvl * 10, name: `cat-L${lvl}`, level: lvl }],
    isLoading: false,
  }),
}));
vi.mock('../../shared/hooks/useUsers', () => ({
  useUsers: () => ({
    data: [{ id: 7, username: 'field1', displayName: '현장1', role: 'FIELD' }],
    isLoading: false,
  }),
}));

import { IssueListView } from './IssueListView';

const PAGE: Page<IssueSummary> = {
  content: [
    {
      id: 1,
      title: '엘리베이터 긴급',
      categoryL1Name: '아파트먼트v1',
      categoryL2Name: '관리자웹',
      categoryL3Name: '기기미동작',
      priority: 'URGENT',
      status: 'NEW',
      assigneeName: null,
      createdAt: '2026-05-18T00:00:00Z',
    },
  ],
  totalElements: 1,
  totalPages: 1,
  number: 0,
  size: 50,
};

function renderView() {
  return render(
    <MemoryRouter initialEntries={['/issues']}>
      <Routes>
        <Route path="/issues" element={<IssueListView />} />
        <Route path="/issues/:id" element={<div>DETAIL PAGE</div>} />
      </Routes>
    </MemoryRouter>
  );
}

describe('IssueListView', () => {
  beforeEach(() => {
    useIssuesMock.mockReset();
    useIssuesMock.mockReturnValue({ data: PAGE, isFetching: false });
  });

  it('renders a row with category path, priority and status badges', () => {
    renderView();
    expect(screen.getByText('엘리베이터 긴급')).toBeInTheDocument();
    expect(screen.getByText('아파트먼트v1 > 관리자웹 > 기기미동작')).toBeInTheDocument();
    expect(screen.getByRole('status', { name: '우선순위: 긴급' })).toBeInTheDocument();
    expect(screen.getByRole('status', { name: '상태: 신규' })).toBeInTheDocument();
  });

  it('navigates to issue detail on row click', async () => {
    const user = userEvent.setup();
    renderView();
    await user.click(screen.getByText('엘리베이터 긴급'));
    await waitFor(() => expect(screen.getByText('DETAIL PAGE')).toBeInTheDocument());
  });

  it('debounced search updates the query params (q + page reset)', async () => {
    const user = userEvent.setup();
    renderView();
    const search = screen.getByPlaceholderText('제목/내용/전화번호 검색');
    await user.type(search, '엘리베이터');
    await waitFor(
      () =>
        expect(useIssuesMock).toHaveBeenCalledWith(
          expect.objectContaining({ q: '엘리베이터', page: 0 })
        ),
      { timeout: 1500 }
    );
  });

  it('initial query has no sort (server severity default; not persisted — PO R2/AC5)', () => {
    renderView();
    expect(useIssuesMock).toHaveBeenCalledWith(
      expect.objectContaining({ page: 0, size: 50 })
    );
    const firstCall = useIssuesMock.mock.calls[0][0];
    expect(firstCall.sort).toBeUndefined();
  });
});
