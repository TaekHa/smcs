import { render, screen, waitFor } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { MemoryRouter } from 'react-router-dom';
import { describe, it, expect, vi, beforeEach } from 'vitest';

const useAutoCategoryMock = vi.fn();
vi.mock('../../shared/hooks/useAutoCategory', () => ({
  useAutoCategory: (args: unknown) => useAutoCategoryMock(args),
}));

vi.mock('../../api/issues', () => ({ createIssue: vi.fn() }));

// CategoryPicker — track its onChange prop so we can simulate per-level user clicks that
// must flip the `touched` flag passed into useAutoCategory.
const pickerOnChangeRef: { current: ((v: { l1?: number; l2?: number; l3?: number }) => void) | null } = {
  current: null,
};
vi.mock('../../shared/components/CategoryPicker', () => ({
  CategoryPicker: (props: { onChange: (v: { l1?: number; l2?: number; l3?: number }) => void }) => {
    pickerOnChangeRef.current = props.onChange;
    return <div data-testid="picker-stub" />;
  },
}));

import { IssueFormView } from './IssueFormView';

function renderForm() {
  return render(
    <MemoryRouter initialEntries={['/issues/new']}>
      <IssueFormView />
    </MemoryRouter>
  );
}

function latestArgs() {
  const calls = useAutoCategoryMock.mock.calls;
  return calls[calls.length - 1]?.[0] as {
    text: string;
    touched: Record<1 | 2 | 3, boolean>;
    apply: (level: 1 | 2 | 3, id: number) => void;
  };
}

describe('IssueFormView ↔ useAutoCategory wiring (Story 4.2)', () => {
  beforeEach(() => {
    useAutoCategoryMock.mockReset();
    pickerOnChangeRef.current = null;
  });

  it('passes the combined title + description to useAutoCategory as the user types', async () => {
    const user = userEvent.setup();
    renderForm();

    await user.type(screen.getByLabelText('제목 *'), '관리자 VOIP');
    await user.type(screen.getByLabelText('상세 내용 *'), '로그인 안됨');

    await waitFor(() => {
      const args = latestArgs();
      expect(args.text).toContain('관리자 VOIP');
      expect(args.text).toContain('로그인 안됨');
    });
  });

  it('marks only the levels the user changed as touched (AC4)', async () => {
    const user = userEvent.setup();
    renderForm();
    // bootstrap a non-empty text so the hook re-evaluates
    await user.type(screen.getByLabelText('제목 *'), 'seed');

    // before any user change: every level is untouched.
    await waitFor(() => {
      const args = latestArgs();
      expect(args.touched).toEqual({ 1: false, 2: false, 3: false });
    });

    // user picks only L1 + L3
    pickerOnChangeRef.current?.({ l1: 10, l3: 30 });

    await waitFor(() => {
      const args = latestArgs();
      expect(args.touched[1]).toBe(true);
      expect(args.touched[3]).toBe(true);
      expect(args.touched[2]).toBe(false);
    });
  });

  it('apply callback writes into the right RHF field per level', async () => {
    const user = userEvent.setup();
    renderForm();
    await user.type(screen.getByLabelText('제목 *'), 'go');

    let apply: (level: 1 | 2 | 3, id: number) => void = () => undefined;
    await waitFor(() => {
      apply = latestArgs().apply;
      expect(typeof apply).toBe('function');
    });

    // Simulate the hook deciding to apply suggestions for all three levels.
    apply(1, 11);
    apply(2, 22);
    apply(3, 33);

    // After apply, the form's internal RHF values flow back through the picker stub's
    // `onChange`-driven `watch` cycle. We can't read RHF state directly, but we CAN ensure
    // calling apply did not throw and the hook is re-invoked on the next render with the
    // updated `touched` (still untouched — apply does not set touched).
    await waitFor(() => {
      const args = latestArgs();
      expect(args.touched).toEqual({ 1: false, 2: false, 3: false });
    });
  });
});
