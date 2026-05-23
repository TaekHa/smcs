import { render, screen, waitFor, within } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { MemoryRouter } from 'react-router-dom';
import { describe, it, expect, vi, beforeEach } from 'vitest';
import type { Page } from '../../types/issue';
import type { ReportFileMode, ReportKind, ReportSummary } from '../../types/report';

const useReportsMock = vi.fn();
const fetchReportPdfMock = vi.fn();

vi.mock('../../shared/hooks/useReports', () => ({
  useReports: (kind: ReportKind, page = 0) => useReportsMock(kind, page),
}));
vi.mock('../../api/reports', () => ({
  fetchReportPdf: (id: number, mode: ReportFileMode) => fetchReportPdfMock(id, mode),
}));

import { ReportsView } from './ReportsView';

const DAILY_PAGE: Page<ReportSummary> = {
  content: [
    { id: 1, kind: 'DAILY', periodKey: '2026-05-22', sizeBytes: 3072, createdAt: '2026-05-23T08:00:00Z' },
  ],
  totalElements: 1,
  totalPages: 1,
  number: 0,
  size: 20,
};

const EMPTY_PAGE: Page<ReportSummary> = {
  content: [],
  totalElements: 0,
  totalPages: 0,
  number: 0,
  size: 20,
};

function renderView() {
  return render(
    <MemoryRouter>
      <ReportsView />
    </MemoryRouter>
  );
}

describe('ReportsView', () => {
  let openSpy: ReturnType<typeof vi.spyOn>;
  let createObjectUrlSpy: ReturnType<typeof vi.spyOn>;

  beforeEach(() => {
    useReportsMock.mockReset();
    fetchReportPdfMock.mockReset();
    // jsdom doesn't implement URL.createObjectURL / revokeObjectURL — provide stubs.
    createObjectUrlSpy = vi.spyOn(URL, 'createObjectURL').mockReturnValue('blob:fake');
    vi.spyOn(URL, 'revokeObjectURL').mockImplementation(() => undefined);
    openSpy = vi.spyOn(window, 'open').mockReturnValue({} as Window);
  });

  it('renders the daily archive by default with formatted size', () => {
    useReportsMock.mockReturnValue({ data: DAILY_PAGE, isLoading: false });
    renderView();

    expect(screen.getByText('보고서 보관함')).toBeInTheDocument();
    expect(screen.getByText('2026-05-22')).toBeInTheDocument();
    expect(screen.getByText('3.0 KB')).toBeInTheDocument();
    expect(screen.getByRole('button', { name: /2026-05-22 일간 보고서 미리보기/ })).toBeInTheDocument();
    expect(screen.getByRole('button', { name: /2026-05-22 일간 보고서 다운로드/ })).toBeInTheDocument();
  });

  it('switches to the weekly tab and queries with kind=WEEKLY', async () => {
    useReportsMock.mockReturnValue({ data: EMPTY_PAGE, isLoading: false });
    const user = userEvent.setup();
    renderView();

    await user.click(screen.getByRole('tab', { name: '주간' }));

    await waitFor(() =>
      expect(useReportsMock).toHaveBeenCalledWith('WEEKLY', expect.any(Number))
    );
  });

  it('shows the empty-state message when there are no reports', () => {
    useReportsMock.mockReturnValue({ data: EMPTY_PAGE, isLoading: false });
    renderView();

    expect(screen.getByText('아직 생성된 보고서가 없습니다')).toBeInTheDocument();
  });

  it('preview click fetches blob and opens a new tab (Story 3.5 AC3)', async () => {
    useReportsMock.mockReturnValue({ data: DAILY_PAGE, isLoading: false });
    fetchReportPdfMock.mockResolvedValue(new Blob(['%PDF-test'], { type: 'application/pdf' }));
    const user = userEvent.setup();
    renderView();

    await user.click(screen.getByRole('button', { name: /2026-05-22 일간 보고서 미리보기/ }));

    await waitFor(() => expect(fetchReportPdfMock).toHaveBeenCalledWith(1, 'preview'));
    expect(createObjectUrlSpy).toHaveBeenCalled();
    expect(openSpy).toHaveBeenCalledWith('blob:fake', '_blank', 'noopener,noreferrer');
  });

  it('download click fetches blob and triggers a download anchor (Story 3.5 AC4)', async () => {
    useReportsMock.mockReturnValue({ data: DAILY_PAGE, isLoading: false });
    fetchReportPdfMock.mockResolvedValue(new Blob(['%PDF-test'], { type: 'application/pdf' }));
    const user = userEvent.setup();
    renderView();

    // The component appends an <a download="..."> to the body, clicks it, and removes it.
    // Spy on createElement to capture the anchor before removal.
    const anchor = document.createElement('a');
    const clickSpy = vi.spyOn(anchor, 'click').mockImplementation(() => undefined);
    const createElementSpy = vi.spyOn(document, 'createElement').mockImplementation((tag) => {
      return tag === 'a' ? anchor : document.createElement(tag);
    });

    await user.click(screen.getByRole('button', { name: /2026-05-22 일간 보고서 다운로드/ }));

    await waitFor(() => expect(fetchReportPdfMock).toHaveBeenCalledWith(1, 'download'));
    expect(anchor.download).toBe('DAILY-2026-05-22.pdf');
    expect(clickSpy).toHaveBeenCalled();
    createElementSpy.mockRestore();
  });

  it('does not leak filePath via the table (security)', () => {
    useReportsMock.mockReturnValue({ data: DAILY_PAGE, isLoading: false });
    renderView();
    const tableScope = within(screen.getByRole('table'));
    expect(tableScope.queryByText(/reports\/DAILY/)).toBeNull();
  });
});
