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

const useAuthMock = vi.fn();
vi.mock('../../auth/useAuthStore', () => ({
  useAuth: () => useAuthMock(),
  useAuthStore: { getState: () => ({ logout: vi.fn() }) },
}));

const exportIssuesCsvMock = vi.fn();
vi.mock('../../api/issues', () => ({
  exportIssuesCsv: (...args: unknown[]) => exportIssuesCsvMock(...args),
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

const AGENT_USER = { id: 1, username: 'agent1', displayName: 'Agent', role: 'AGENT' as const };
const ADMIN_USER = { id: 2, username: 'admin1', displayName: 'Admin', role: 'ADMIN' as const };

function renderView() {
  return render(
    <MemoryRouter initialEntries={['/issues']}>
      <Routes>
        <Route path="/issues" element={<IssueListView />} />
        <Route path="/issues/new" element={<div>NEW ISSUE FORM</div>} />
        <Route path="/issues/:id" element={<div>DETAIL PAGE</div>} />
      </Routes>
    </MemoryRouter>
  );
}

describe('IssueListView', () => {
  beforeEach(() => {
    useIssuesMock.mockReset();
    useIssuesMock.mockReturnValue({ data: PAGE, isFetching: false });
    useAuthMock.mockReset();
    useAuthMock.mockReturnValue(AGENT_USER);
    exportIssuesCsvMock.mockReset();
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

  it('SW-008 P1 regression: 신규 등록 버튼이 /issues/new 로 이동 (AGENT/ADMIN primary entry)', async () => {
    const user = userEvent.setup();
    renderView();
    await user.click(screen.getByRole('button', { name: '신규 이슈 등록' }));
    await waitFor(() => expect(screen.getByText('NEW ISSUE FORM')).toBeInTheDocument());
  });

  it('SW-002 P2 regression: `N` 단축키가 /issues/new 로 이동 (AC6 키보드 골든 패스)', async () => {
    const user = userEvent.setup();
    renderView();
    // body has focus by default; the listener is on window so this should fire even without focus.
    await user.keyboard('n');
    await waitFor(() => expect(screen.getByText('NEW ISSUE FORM')).toBeInTheDocument());
  });

  it('SW-002 gate: `N` is ignored while typing in the search input (no false navigation)', async () => {
    const user = userEvent.setup();
    renderView();
    const search = screen.getByPlaceholderText('제목/내용/전화번호 검색');
    await user.click(search);
    await user.keyboard('n');
    // We are still on the list — the new-issue route has not been entered.
    expect(screen.queryByText('NEW ISSUE FORM')).not.toBeInTheDocument();
  });

  it('SW-002 gate: Ctrl+N is ignored (browser shortcut passthrough)', async () => {
    const user = userEvent.setup();
    renderView();
    await user.keyboard('{Control>}n{/Control}');
    expect(screen.queryByText('NEW ISSUE FORM')).not.toBeInTheDocument();
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

  describe('CSV export (Story 4.3)', () => {
    it('hides CSV buttons for non-ADMIN users (Deviation #2)', () => {
      useAuthMock.mockReturnValue(AGENT_USER);
      renderView();
      expect(screen.queryByRole('button', { name: '이슈 CSV 내보내기' })).not.toBeInTheDocument();
      expect(
        screen.queryByRole('button', { name: '이슈 CSV 내보내기 - 개인정보 포함' })
      ).not.toBeInTheDocument();
    });

    it('shows both CSV buttons for ADMIN', () => {
      useAuthMock.mockReturnValue(ADMIN_USER);
      renderView();
      expect(screen.getByRole('button', { name: '이슈 CSV 내보내기' })).toBeInTheDocument();
      expect(
        screen.getByRole('button', { name: '이슈 CSV 내보내기 - 개인정보 포함' })
      ).toBeInTheDocument();
    });

    it('plain export triggers download with includePii=false', async () => {
      useAuthMock.mockReturnValue(ADMIN_USER);
      exportIssuesCsvMock.mockResolvedValue(new Blob(['csv'], { type: 'text/csv' }));

      const createObjectURL = vi.fn().mockReturnValue('blob:test');
      const revokeObjectURL = vi.fn();
      vi.stubGlobal('URL', { createObjectURL, revokeObjectURL } as unknown as typeof URL);
      const clickSpy = vi
        .spyOn(HTMLAnchorElement.prototype, 'click')
        .mockImplementation(() => undefined);

      const user = userEvent.setup();
      renderView();
      await user.click(screen.getByRole('button', { name: '이슈 CSV 내보내기' }));

      await waitFor(() => expect(exportIssuesCsvMock).toHaveBeenCalled());
      const [, includePii] = exportIssuesCsvMock.mock.calls[0];
      expect(includePii).toBe(false);
      expect(createObjectURL).toHaveBeenCalled();
      expect(clickSpy).toHaveBeenCalled();

      clickSpy.mockRestore();
      vi.unstubAllGlobals();
    });

    it('PII export requires Popconfirm then calls with includePii=true', async () => {
      useAuthMock.mockReturnValue(ADMIN_USER);
      exportIssuesCsvMock.mockResolvedValue(new Blob(['csv'], { type: 'text/csv' }));

      const createObjectURL = vi.fn().mockReturnValue('blob:pii');
      const revokeObjectURL = vi.fn();
      vi.stubGlobal('URL', { createObjectURL, revokeObjectURL } as unknown as typeof URL);
      const clickSpy = vi
        .spyOn(HTMLAnchorElement.prototype, 'click')
        .mockImplementation(() => undefined);

      const user = userEvent.setup();
      renderView();
      await user.click(
        screen.getByRole('button', { name: '이슈 CSV 내보내기 - 개인정보 포함' })
      );
      // Popconfirm "계속" — confirm before the API runs
      await user.click(await screen.findByRole('button', { name: '계속' }));

      await waitFor(() => expect(exportIssuesCsvMock).toHaveBeenCalled());
      const [, includePii] = exportIssuesCsvMock.mock.calls[0];
      expect(includePii).toBe(true);

      clickSpy.mockRestore();
      vi.unstubAllGlobals();
    });

    it('recovers (button re-enabled) when backend returns EXPORT_TOO_MANY_ROWS', async () => {
      useAuthMock.mockReturnValue(ADMIN_USER);
      const errorBody = JSON.stringify({
        code: 'EXPORT_TOO_MANY_ROWS',
        message: '결과가 5,000건을 초과합니다(현재 5001건). 필터를 좁혀주세요.',
      });
      exportIssuesCsvMock.mockRejectedValue({
        response: { data: new Blob([errorBody], { type: 'application/json' }) },
      });

      const user = userEvent.setup();
      renderView();
      const button = screen.getByRole('button', { name: '이슈 CSV 내보내기' });
      await user.click(button);

      // After the rejection is handled, the exporting state must reset so the user can retry
      // with a narrower filter — this is the visible end of the EXPORT_TOO_MANY_ROWS recovery path.
      await waitFor(() => expect(button).not.toBeDisabled());
      expect(exportIssuesCsvMock).toHaveBeenCalledTimes(1);
    });
  });
});
