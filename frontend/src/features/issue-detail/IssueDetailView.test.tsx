import { render, screen, waitFor, within } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { QueryClient, QueryClientProvider } from '@tanstack/react-query';
import { MemoryRouter, Route, Routes } from 'react-router-dom';
import { describe, it, expect, vi, beforeEach } from 'vitest';
import type { IssueActivity, IssueDetail } from '../../types/issue';

vi.mock('../../api/issues', () => ({
  getIssue: vi.fn(),
  listIssueEvents: vi.fn(),
  addComment: vi.fn(),
  assignIssue: vi.fn(),
  transitionIssue: vi.fn(),
}));

// useAuth gates the management controls; default = no user (controls hidden).
const useAuthMock = vi.fn();
vi.mock('../../auth/useAuthStore', () => ({ useAuth: () => useAuthMock() }));

// UserSelect has its own test; stub it so the assign flow is unit-isolated.
vi.mock('../../shared/components/UserSelect', () => ({
  UserSelect: ({ onChange }: { onChange: (id: number) => void }) => (
    <button onClick={() => onChange(2)}>mock-select-field</button>
  ),
}));

import { getIssue, listIssueEvents, addComment, assignIssue, transitionIssue } from '../../api/issues';
import { IssueDetailView } from './IssueDetailView';

const baseIssue: IssueDetail = {
  id: 1,
  title: '엘리베이터 고장',
  description: '3호기 멈춤',
  categoryL1: { id: 10, name: '아파트' },
  categoryL2: { id: 20, name: '승강기' },
  categoryL3: { id: 30, name: '고장' },
  priority: 'URGENT',
  status: 'IN_PROGRESS',
  createdByName: '김상담1',
  assigneeName: '이현장1',
  resolvedAt: null,
  createdAt: '2026-05-20T01:00:00Z',
  updatedAt: '2026-05-21T02:00:00Z',
  callerName: '홍길동',
  callerPhone: '010-1234-5678',
  comments: [
    { id: 100, authorName: '김상담1', body: '확인했습니다', kind: 'NOTE', createdAt: '2026-05-20T03:00:00Z' },
  ],
  attachments: [
    {
      id: 200,
      originalName: 'photo.jpg',
      url: '/files/abc.jpg',
      mimeType: 'image/jpeg',
      sizeBytes: 1234,
      createdAt: '2026-05-20T03:00:00Z',
    },
  ],
};

const events: IssueActivity[] = [
  { id: 2, eventType: 'COMMENTED', actorName: '김상담1', fromValue: null, toValue: null, createdAt: '2026-05-20T03:00:00Z' },
  { id: 1, eventType: 'CREATED', actorName: '김상담1', fromValue: null, toValue: null, createdAt: '2026-05-20T01:00:00Z' },
];

function renderView() {
  const queryClient = new QueryClient({ defaultOptions: { queries: { retry: false } } });
  return render(
    <QueryClientProvider client={queryClient}>
      <MemoryRouter initialEntries={['/issues/1']}>
        <Routes>
          <Route path="/issues/:id" element={<IssueDetailView />} />
        </Routes>
      </MemoryRouter>
    </QueryClientProvider>
  );
}

describe('IssueDetailView', () => {
  beforeEach(() => {
    vi.mocked(getIssue).mockReset();
    vi.mocked(listIssueEvents).mockReset();
    vi.mocked(addComment).mockReset();
    vi.mocked(assignIssue).mockReset();
    vi.mocked(transitionIssue).mockReset();
    useAuthMock.mockReset();
    useAuthMock.mockReturnValue(null); // default: no management controls
    vi.mocked(getIssue).mockResolvedValue(baseIssue);
    vi.mocked(listIssueEvents).mockResolvedValue(events);
    vi.mocked(addComment).mockResolvedValue({
      id: 101,
      authorName: '김상담1',
      body: '추가 코멘트',
      kind: 'NOTE',
      createdAt: '2026-05-21T05:00:00Z',
    });
    vi.mocked(assignIssue).mockResolvedValue(baseIssue);
    vi.mocked(transitionIssue).mockResolvedValue(baseIssue);
  });

  it('renders metadata, body, comments and activity log', async () => {
    renderView();
    expect(await screen.findByText('#1 엘리베이터 고장')).toBeInTheDocument();
    expect(screen.getByText('아파트 > 승강기 > 고장')).toBeInTheDocument();
    expect(screen.getByText('3호기 멈춤')).toBeInTheDocument();
    expect(screen.getByText('확인했습니다')).toBeInTheDocument();
    // activity labels (scoped to the activity-log region)
    const log = await screen.findByRole('region', { name: '활동 로그' });
    expect(within(log).getByText('코멘트')).toBeInTheDocument();
    expect(within(log).getByText('생성')).toBeInTheDocument();
  });

  it('shows caller PII when present (AGENT/ADMIN)', async () => {
    renderView();
    expect(await screen.findByText('홍길동')).toBeInTheDocument();
    expect(screen.getByText('010-1234-5678')).toBeInTheDocument();
  });

  it('hides caller PII when null (FIELD — Deviation #2)', async () => {
    vi.mocked(getIssue).mockResolvedValue({ ...baseIssue, callerName: null, callerPhone: null });
    renderView();
    await screen.findByText('#1 엘리베이터 고장');
    expect(screen.queryByText('발신자명')).not.toBeInTheDocument();
    expect(screen.queryByText('발신자 전화')).not.toBeInTheDocument();
  });

  it('renders the activity log newest-first (AC4)', async () => {
    renderView();
    const log = await screen.findByRole('region', { name: '활동 로그' });
    const commented = within(log).getByText('코멘트');
    const created = within(log).getByText('생성');
    // 'COMMENTED' (newest) must appear before 'CREATED' in document order
    expect(commented.compareDocumentPosition(created) & Node.DOCUMENT_POSITION_FOLLOWING).toBeTruthy();
  });

  it('submits a comment and refreshes the list on success (AC3)', async () => {
    const user = userEvent.setup();
    const updated: IssueDetail = {
      ...baseIssue,
      comments: [
        ...baseIssue.comments,
        { id: 101, authorName: '김상담1', body: '추가 코멘트', kind: 'NOTE', createdAt: '2026-05-21T05:00:00Z' },
      ],
    };
    vi.mocked(getIssue).mockResolvedValueOnce(baseIssue).mockResolvedValue(updated);

    renderView();
    await screen.findByText('#1 엘리베이터 고장');

    await user.type(screen.getByPlaceholderText('코멘트를 입력하세요'), '추가 코멘트');
    await user.click(screen.getByRole('button', { name: '코멘트 등록' }));

    await waitFor(() => expect(addComment).toHaveBeenCalledWith(1, { body: '추가 코멘트' }));
    expect(await screen.findByText('추가 코멘트')).toBeInTheDocument();
  });

  it('opens a preview when an attachment thumbnail is clicked (AC5)', async () => {
    const user = userEvent.setup();
    renderView();
    const img = await screen.findByAltText('photo.jpg');
    await user.click(img);
    await waitFor(() =>
      expect(document.querySelector('.ant-image-preview-root, .ant-image-preview-wrap')).toBeTruthy()
    );
  });

  it('shows Forbidden on a 403 response (AC6)', async () => {
    vi.mocked(getIssue).mockRejectedValue(
      Object.assign(new Error('forbidden'), { isAxiosError: true, response: { status: 403 } })
    );
    vi.mocked(listIssueEvents).mockResolvedValue([]);
    renderView();
    expect(await screen.findByText('권한이 없습니다')).toBeInTheDocument();
  });

  it('assigns a field worker for AGENT (AC1, AC2)', async () => {
    const user = userEvent.setup();
    useAuthMock.mockReturnValue({ id: 1, username: 'agent1', displayName: '김상담1', role: 'AGENT' });
    renderView();
    await screen.findByText('#1 엘리베이터 고장');
    await user.click(screen.getByText('mock-select-field')); // sets assigneeId=2
    await user.click(screen.getByRole('button', { name: '배정' }));
    await waitFor(() => expect(assignIssue).toHaveBeenCalledWith(1, { assigneeId: 2 }));
  });

  it('shows only the valid next-state transition button (AC3) and transitions', async () => {
    const user = userEvent.setup();
    useAuthMock.mockReturnValue({ id: 1, username: 'agent1', displayName: '김상담1', role: 'AGENT' });
    vi.mocked(getIssue).mockResolvedValue({ ...baseIssue, status: 'ASSIGNED' });
    renderView();
    await screen.findByText('#1 엘리베이터 고장');
    // ASSIGNED → only [IN_PROGRESS]; DONE button must not appear
    const next = screen.getByRole('button', { name: '진행중(으)로 변경' });
    expect(screen.queryByRole('button', { name: '완료(으)로 변경' })).not.toBeInTheDocument();
    await user.click(next);
    await waitFor(() => expect(transitionIssue).toHaveBeenCalledWith(1, { to: 'IN_PROGRESS' }));
  });

  it('hides management controls for FIELD users (Deviation #8)', async () => {
    useAuthMock.mockReturnValue({ id: 2, username: 'field1', displayName: '이현장1', role: 'FIELD' });
    renderView();
    await screen.findByText('#1 엘리베이터 고장');
    expect(screen.queryByRole('button', { name: '배정' })).not.toBeInTheDocument();
    expect(screen.queryByText('mock-select-field')).not.toBeInTheDocument();
  });

  it('verifies a DONE issue (2.7 AC1)', async () => {
    const user = userEvent.setup();
    useAuthMock.mockReturnValue({ id: 1, username: 'agent1', displayName: '김상담1', role: 'AGENT' });
    vi.mocked(getIssue).mockResolvedValue({ ...baseIssue, status: 'DONE' });
    renderView();
    await screen.findByText('#1 엘리베이터 고장');
    await user.click(screen.getByRole('button', { name: '검수 완료' }));
    await waitFor(() => expect(transitionIssue).toHaveBeenCalledWith(1, { to: 'VERIFIED' }));
  });

  it('reopens a DONE issue with a required reason (2.7 AC2/AC3)', async () => {
    const user = userEvent.setup();
    useAuthMock.mockReturnValue({ id: 1, username: 'agent1', displayName: '김상담1', role: 'AGENT' });
    vi.mocked(getIssue).mockResolvedValue({ ...baseIssue, status: 'DONE' });
    renderView();
    await screen.findByText('#1 엘리베이터 고장');
    await user.click(screen.getByRole('button', { name: '재오픈' })); // open the modal
    await user.type(screen.getByPlaceholderText('재오픈 사유를 입력하세요'), '추가 작업 필요');
    // both the trigger and the modal OK are labelled 재오픈; the modal OK is the last
    const reopenButtons = screen.getAllByRole('button', { name: '재오픈' });
    await user.click(reopenButtons[reopenButtons.length - 1]);
    await waitFor(() =>
      expect(transitionIssue).toHaveBeenCalledWith(1, { to: 'IN_PROGRESS', reason: '추가 작업 필요' })
    );
  });
});
