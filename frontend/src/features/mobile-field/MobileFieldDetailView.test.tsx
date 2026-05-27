import { render, screen, waitFor } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { MemoryRouter, Route, Routes } from 'react-router-dom';
import { describe, it, expect, vi, beforeEach } from 'vitest';
import type { IssueDetail } from '../../types/issue';

const useIssueMock = vi.fn();
const addCommentMutate = vi.fn((_vars: unknown, opts?: { onSuccess?: () => void }) => opts?.onSuccess?.());
const transitionMutate = vi.fn((_vars: unknown, opts?: { onSuccess?: () => void }) => opts?.onSuccess?.());
const uploadMutate = vi.fn();

vi.mock('../../shared/hooks/useIssue', () => ({
  useIssue: () => useIssueMock(),
  useAddComment: () => ({ mutate: addCommentMutate, isPending: false }),
  useTransitionIssue: () => ({ mutate: transitionMutate, isPending: false }),
  useUploadAttachment: () => ({ mutate: uploadMutate, isPending: false }),
}));
vi.mock('../../shared/components/AuthImage', () => ({
  AuthImage: ({ alt }: { alt: string }) => <img alt={alt} />,
}));

import { MobileFieldDetailView } from './MobileFieldDetailView';

const ISSUE: IssueDetail = {
  id: 7,
  title: '엘리베이터 고장',
  description: '3호기 멈춤',
  categoryL1: { id: 1, name: '아파트' },
  categoryL2: { id: 2, name: '승강기' },
  categoryL3: { id: 3, name: '고장' },
  priority: 'URGENT',
  status: 'IN_PROGRESS',
  createdByName: '김상담1',
  assigneeName: '이현장1',
  resolvedAt: null,
  createdAt: '2026-05-20T01:00:00Z',
  updatedAt: '2026-05-20T01:00:00Z',
  callerName: null,
  callerPhone: null,
  comments: [],
  attachments: [
    {
      id: 100,
      originalName: 'photo.jpg',
      url: '/files/2026/05/abc.jpg',
      mimeType: 'image/jpeg',
      sizeBytes: 1234,
      createdAt: '2026-05-20T02:00:00Z',
    },
  ],
};

function renderView() {
  return render(
    <MemoryRouter initialEntries={['/m/issues/7']}>
      <Routes>
        <Route path="/m/issues/:id" element={<MobileFieldDetailView />} />
      </Routes>
    </MemoryRouter>
  );
}

describe('MobileFieldDetailView', () => {
  beforeEach(() => {
    useIssueMock.mockReset();
    addCommentMutate.mockClear();
    transitionMutate.mockClear();
    uploadMutate.mockClear();
    useIssueMock.mockReturnValue({ data: ISSUE, isLoading: false, isError: false });
  });

  it('renders summary and attachment gallery (AC1)', () => {
    renderView();
    expect(screen.getByText('#7 엘리베이터 고장')).toBeInTheDocument();
    expect(screen.getByText('아파트 > 승강기 > 고장')).toBeInTheDocument();
    expect(screen.getByAltText('photo.jpg')).toBeInTheDocument();
  });

  it('완료 처리 button is disabled until action text is entered (AC5)', async () => {
    const user = userEvent.setup();
    renderView();
    const btn = screen.getByRole('button', { name: '완료 처리' });
    expect(btn).toBeDisabled();
    await user.type(screen.getByPlaceholderText('조치 내용을 입력하세요'), '교체 완료');
    expect(btn).toBeEnabled();
  });

  it('completes from IN_PROGRESS: FIELD_ACTION comment then single DONE transition (AC4, AC6)', async () => {
    const user = userEvent.setup();
    renderView();
    await user.type(screen.getByPlaceholderText('조치 내용을 입력하세요'), '부품 교체 완료');
    await user.click(screen.getByRole('button', { name: '완료 처리' }));
    expect(addCommentMutate).toHaveBeenCalledWith(
      { body: '부품 교체 완료', kind: 'FIELD_ACTION' },
      expect.anything()
    );
    await waitFor(() => expect(transitionMutate).toHaveBeenCalledWith({ to: 'DONE' }, expect.anything()));
    expect(transitionMutate).toHaveBeenCalledTimes(1);
  });

  it('SW-001 P1 regression: ASSIGNED issue chains IN_PROGRESS then DONE', async () => {
    useIssueMock.mockReturnValue({
      data: { ...ISSUE, status: 'ASSIGNED' },
      isLoading: false,
      isError: false,
    });
    const user = userEvent.setup();
    renderView();
    await user.type(screen.getByPlaceholderText('조치 내용을 입력하세요'), '현장 조치 완료');
    await user.click(screen.getByRole('button', { name: '완료 처리' }));
    expect(addCommentMutate).toHaveBeenCalledWith(
      { body: '현장 조치 완료', kind: 'FIELD_ACTION' },
      expect.anything()
    );
    await waitFor(() => expect(transitionMutate).toHaveBeenCalledTimes(2));
    expect(transitionMutate).toHaveBeenNthCalledWith(1, { to: 'IN_PROGRESS' }, expect.anything());
    expect(transitionMutate).toHaveBeenNthCalledWith(2, { to: 'DONE' }, expect.anything());
  });

  it('uploads a selected image (AC2/AC3)', async () => {
    const user = userEvent.setup();
    const { container } = renderView();
    const input = container.querySelector('input[type="file"]') as HTMLInputElement;
    const file = new File(['x'], 'p.jpg', { type: 'image/jpeg' });
    await user.upload(input, file);
    expect(uploadMutate).toHaveBeenCalled();
  });

  it('shows completion text instead of the form for a DONE issue', () => {
    useIssueMock.mockReturnValue({
      data: { ...ISSUE, status: 'DONE', resolvedAt: '2026-05-21T03:00:00Z' },
      isLoading: false,
      isError: false,
    });
    renderView();
    expect(screen.queryByRole('button', { name: '완료 처리' })).not.toBeInTheDocument();
    expect(screen.getByText(/완료 처리된 이슈입니다/)).toBeInTheDocument();
  });

  it('shows Forbidden on a 403 response', () => {
    useIssueMock.mockReturnValue({
      data: undefined,
      isLoading: false,
      isError: true,
      error: { isAxiosError: true, response: { status: 403 } },
    });
    renderView();
    expect(screen.getByText('권한이 없습니다')).toBeInTheDocument();
  });
});
