import { render, screen, waitFor } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { QueryClient, QueryClientProvider } from '@tanstack/react-query';
import { describe, it, expect, vi, beforeEach } from 'vitest';
import type { CategoryAdminItem } from '../../types/category';

const listAdminCategoriesMock = vi.fn();
const upsertCategoryMock = vi.fn();
vi.mock('../../api/adminCategories', () => ({
  listAdminCategories: (...args: unknown[]) => listAdminCategoriesMock(...args),
  upsertCategory: (...args: unknown[]) => upsertCategoryMock(...args),
}));

import { AdminCategoriesView } from './AdminCategoriesView';

const L1: CategoryAdminItem[] = [
  { id: 1, level: 1, name: '아파트먼트v1', sortOrder: 1, active: true, keywords: [] },
  { id: 2, level: 1, name: '아파트먼트v2', sortOrder: 2, active: true, keywords: ['v2'] },
  { id: 3, level: 1, name: 'voip/pbx', sortOrder: 3, active: false, keywords: [] },
];

function renderView() {
  const qc = new QueryClient({ defaultOptions: { queries: { retry: false } } });
  return render(
    <QueryClientProvider client={qc}>
      <AdminCategoriesView />
    </QueryClientProvider>
  );
}

describe('AdminCategoriesView', () => {
  beforeEach(() => {
    listAdminCategoriesMock.mockReset();
    upsertCategoryMock.mockReset();
    listAdminCategoriesMock.mockImplementation((level: number) =>
      Promise.resolve(level === 1 ? L1 : [])
    );
  });

  it('renders L1/L2/L3 tabs with seed rows in the default tab', async () => {
    renderView();
    expect(screen.getByRole('tab', { name: /대분류/ })).toBeInTheDocument();
    expect(screen.getByRole('tab', { name: /중분류/ })).toBeInTheDocument();
    expect(screen.getByRole('tab', { name: /소분류/ })).toBeInTheDocument();
    expect(await screen.findByText('아파트먼트v1')).toBeInTheDocument();
    expect(screen.getByText('아파트먼트v2')).toBeInTheDocument();
    expect(screen.getByText('voip/pbx')).toBeInTheDocument();
  });

  it('renders inactive row with Switch off (AC4 SoT — admin still sees inactive)', async () => {
    renderView();
    await screen.findByText('voip/pbx');
    const switches = screen.getAllByRole('switch');
    // row order matches L1 fixture: index 2 = voip/pbx (inactive)
    expect(switches[2]).not.toBeChecked();
    expect(switches[0]).toBeChecked();
  });

  it('opens Add modal and calls upsert with id=null on submit', async () => {
    upsertCategoryMock.mockResolvedValue({
      id: 99,
      level: 1,
      name: '신규',
      sortOrder: 4,
      active: true,
      keywords: ['kw1'],
    });
    const user = userEvent.setup();
    renderView();
    await screen.findByText('아파트먼트v1');

    await user.click(screen.getByRole('button', { name: 'L1 카테고리 추가' }));
    const dialog = await screen.findByRole('dialog', { name: '카테고리 추가' });
    await user.type(within(dialog, screen).getByLabelText('이름'), '신규');
    await user.click(screen.getByRole('button', { name: '저장' }));

    await waitFor(() => expect(upsertCategoryMock).toHaveBeenCalled());
    const arg = upsertCategoryMock.mock.calls[0][0];
    expect(arg.id).toBeNull();
    expect(arg.level).toBe(1);
    expect(arg.name).toBe('신규');
  });

  it('opens Edit modal pre-filled and calls upsert with id present', async () => {
    upsertCategoryMock.mockResolvedValue({ ...L1[1], name: 'v2-개정' });
    const user = userEvent.setup();
    renderView();
    await screen.findByText('아파트먼트v2');

    // edit button for second row (아파트먼트v2)
    await user.click(screen.getByRole('button', { name: '카테고리 아파트먼트v2 수정' }));
    const dialog = await screen.findByRole('dialog', { name: '카테고리 수정' });
    const nameInput = within(dialog, screen).getByLabelText('이름') as HTMLInputElement;
    expect(nameInput.value).toBe('아파트먼트v2');
    await user.clear(nameInput);
    await user.type(nameInput, 'v2-개정');
    await user.click(screen.getByRole('button', { name: '저장' }));

    await waitFor(() => expect(upsertCategoryMock).toHaveBeenCalled());
    const arg = upsertCategoryMock.mock.calls[0][0];
    expect(arg.id).toBe(2);
    expect(arg.name).toBe('v2-개정');
  });

  it('Switch toggle requires Popconfirm then calls upsert with flipped active', async () => {
    upsertCategoryMock.mockResolvedValue({ ...L1[0], active: false });
    const user = userEvent.setup();
    renderView();
    await screen.findByText('아파트먼트v1');

    // click the first switch (아파트먼트v1, currently active) — Popconfirm opens
    const switches = screen.getAllByRole('switch');
    await user.click(switches[0]);
    // confirm "계속"
    await user.click(await screen.findByRole('button', { name: '계속' }));

    await waitFor(() => expect(upsertCategoryMock).toHaveBeenCalled());
    const arg = upsertCategoryMock.mock.calls[0][0];
    expect(arg.id).toBe(1);
    expect(arg.active).toBe(false);
  });

  it('arrow-down button swaps sortOrder with the next row (2 mutations)', async () => {
    upsertCategoryMock.mockResolvedValue(L1[0]);
    const user = userEvent.setup();
    renderView();
    await screen.findByText('아파트먼트v1');

    await user.click(screen.getByRole('button', { name: '아파트먼트v1 아래로 이동' }));

    await waitFor(() => expect(upsertCategoryMock).toHaveBeenCalledTimes(2));
    // first call: row 1 (id=1, sortOrder was 1) gets neighbor's sortOrder (2)
    expect(upsertCategoryMock.mock.calls[0][0]).toMatchObject({ id: 1, sortOrder: 2 });
    // second call: neighbor (id=2, sortOrder was 2) gets row's old sortOrder (1)
    expect(upsertCategoryMock.mock.calls[1][0]).toMatchObject({ id: 2, sortOrder: 1 });
  });
});

// utility — search within an element using @testing-library
import { within as twlWithin } from '@testing-library/react';
function within(element: HTMLElement, _screen: typeof screen) {
  return twlWithin(element);
}
