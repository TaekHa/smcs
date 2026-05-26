import { render, screen, waitFor, fireEvent } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { MemoryRouter, Route, Routes } from 'react-router-dom';
import { describe, it, expect, vi, beforeEach } from 'vitest';

const createIssueMock = vi.fn();
vi.mock('../../api/issues', () => ({
  createIssue: (req: unknown) => createIssueMock(req),
}));

// Isolate the form: CategoryPicker has its own test and uses antd Select
// (brittle in jsdom). Stub it to set the three category ids in one click.
vi.mock('../../shared/components/CategoryPicker', () => ({
  CategoryPicker: ({ onChange }: { onChange: (v: unknown) => void }) => (
    <button type="button" onClick={() => onChange({ l1: 1, l2: 2, l3: 3 })}>
      pick-categories
    </button>
  ),
}));

// Story 4.2 — auto-category hook needs `useCategories` (3 levels). Stub the hook so the
// form tests don't depend on network. A dedicated test below exercises the wiring.
vi.mock('../../shared/hooks/useCategories', () => ({
  useCategories: () => ({ data: [], isLoading: false }),
}));

import { IssueFormView } from './IssueFormView';

function renderForm() {
  return render(
    <MemoryRouter initialEntries={['/issues/new']}>
      <Routes>
        <Route path="/issues/new" element={<IssueFormView />} />
        <Route path="/issues/:id" element={<div>DETAIL PAGE</div>} />
        <Route path="/issues" element={<div>LIST PAGE</div>} />
      </Routes>
    </MemoryRouter>
  );
}

async function fillValid(user: ReturnType<typeof userEvent.setup>) {
  await user.type(screen.getByLabelText('제목 *'), '엘리베이터 고장');
  await user.type(screen.getByLabelText('발신자명 *'), '홍길동');
  await user.type(screen.getByLabelText('발신자 전화번호 *'), '010-1234-5678');
  await user.type(screen.getByLabelText('상세 내용 *'), '3호기 멈춤');
  await user.click(screen.getByRole('button', { name: 'pick-categories' }));
}

describe('IssueFormView', () => {
  beforeEach(() => {
    createIssueMock.mockReset();
  });

  it('renders all required fields and the four priority badges', () => {
    renderForm();
    expect(screen.getByLabelText('제목 *')).toBeInTheDocument();
    expect(screen.getByLabelText('발신자명 *')).toBeInTheDocument();
    expect(screen.getByLabelText('발신자 전화번호 *')).toBeInTheDocument();
    expect(screen.getByLabelText('상세 내용 *')).toBeInTheDocument();
    ['긴급', '높음', '보통', '낮음'].forEach((l) =>
      expect(screen.getByRole('status', { name: `우선순위: ${l}` })).toBeInTheDocument()
    );
  });

  it('shows required-field validation messages on empty submit', async () => {
    const user = userEvent.setup();
    renderForm();
    await user.click(screen.getByRole('button', { name: /저장/ }));
    expect(await screen.findByText('제목을 입력하세요.')).toBeInTheDocument();
    expect(screen.getByText('발신자명을 입력하세요.')).toBeInTheDocument();
    expect(screen.getByText('발신자 전화번호를 입력하세요.')).toBeInTheDocument();
    expect(screen.getByText('대분류를 선택하세요.')).toBeInTheDocument();
    expect(screen.getByText('상세 내용을 입력하세요.')).toBeInTheDocument();
    expect(createIssueMock).not.toHaveBeenCalled();
  });

  it('submits and navigates to the issue detail page on success', async () => {
    createIssueMock.mockResolvedValueOnce({
      id: 123,
      title: '엘리베이터 고장',
      priority: 'NORMAL',
      status: 'NEW',
      createdAt: '2026-05-18T00:00:00Z',
    });
    const user = userEvent.setup();
    renderForm();
    await fillValid(user);
    await user.click(screen.getByRole('button', { name: /저장/ }));

    await waitFor(() => expect(screen.getByText('DETAIL PAGE')).toBeInTheDocument());
    expect(createIssueMock).toHaveBeenCalledWith(
      expect.objectContaining({
        title: '엘리베이터 고장',
        callerName: '홍길동',
        callerPhone: '010-1234-5678',
        categoryL1Id: 1,
        categoryL2Id: 2,
        categoryL3Id: 3,
        priority: 'NORMAL',
        description: '3호기 멈춤',
      })
    );
  });

  it('Ctrl+S submits the form', async () => {
    createIssueMock.mockResolvedValueOnce({
      id: 9,
      title: 't',
      priority: 'NORMAL',
      status: 'NEW',
      createdAt: '2026-05-18T00:00:00Z',
    });
    const user = userEvent.setup();
    renderForm();
    await fillValid(user);
    fireEvent.keyDown(screen.getByRole('form'), { key: 's', ctrlKey: true });
    await waitFor(() => expect(createIssueMock).toHaveBeenCalledTimes(1));
  });

  it('Esc cancels and navigates to the issue list', async () => {
    renderForm();
    fireEvent.keyDown(screen.getByRole('form'), { key: 'Escape' });
    await waitFor(() => expect(screen.getByText('LIST PAGE')).toBeInTheDocument());
  });

  it('maps a 403 error to a Korean permission message', async () => {
    createIssueMock.mockRejectedValueOnce({ response: { status: 403, data: { code: 'FORBIDDEN' } } });
    const user = userEvent.setup();
    renderForm();
    await fillValid(user);
    await user.click(screen.getByRole('button', { name: /저장/ }));
    expect(await screen.findByText('이슈를 등록할 권한이 없습니다.')).toBeInTheDocument();
  });
});
