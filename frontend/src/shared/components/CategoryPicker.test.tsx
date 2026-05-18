import { render, screen, waitFor } from '@testing-library/react';
import { QueryClient, QueryClientProvider } from '@tanstack/react-query';
import { describe, it, expect, vi, beforeEach } from 'vitest';

const listCategoriesMock = vi.fn();
vi.mock('../../api/categories', () => ({
  listCategories: (level: number) => listCategoriesMock(level),
}));

import { CategoryPicker } from './CategoryPicker';

function renderPicker() {
  const qc = new QueryClient({ defaultOptions: { queries: { retry: false } } });
  return render(
    <QueryClientProvider client={qc}>
      <CategoryPicker value={{}} onChange={() => {}} />
    </QueryClientProvider>
  );
}

describe('CategoryPicker', () => {
  beforeEach(() => {
    listCategoriesMock.mockReset();
    listCategoriesMock.mockImplementation((level: number) =>
      Promise.resolve([{ id: level * 10, name: `cat-L${level}`, level }])
    );
  });

  it('renders three labelled selects, all enabled with no forced L1/L2/L3 dependency (AC7)', async () => {
    const { container } = renderPicker();
    await waitFor(() => expect(listCategoriesMock).toHaveBeenCalledWith(1));

    expect(container.querySelector('[aria-label="대분류"]')).toBeInTheDocument();
    expect(container.querySelector('[aria-label="중분류"]')).toBeInTheDocument();
    expect(container.querySelector('[aria-label="소분류"]')).toBeInTheDocument();

    // All three comboboxes exist and are enabled simultaneously with no
    // selection — proves L2/L3 are NOT gated on L1 (AC7: all combinations allowed).
    const comboboxes = screen.getAllByRole('combobox');
    expect(comboboxes).toHaveLength(3);
    comboboxes.forEach((cb) => expect(cb).not.toBeDisabled());
  });

  it('loads options independently for each level via useCategories', async () => {
    renderPicker();
    await waitFor(() => {
      expect(listCategoriesMock).toHaveBeenCalledWith(1);
      expect(listCategoriesMock).toHaveBeenCalledWith(2);
      expect(listCategoriesMock).toHaveBeenCalledWith(3);
    });
  });
});
