import { render, screen, waitFor } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { MemoryRouter } from 'react-router-dom';
import { describe, it, expect, vi, beforeEach } from 'vitest';
import type { DashboardStats, StatsPeriod } from '../../types/stats';

const useDashboardStatsMock = vi.fn();

vi.mock('../../shared/hooks/useDashboardStats', () => ({
  useDashboardStats: (period: StatsPeriod) => useDashboardStatsMock(period),
}));

// Ant Design Charts renders to <canvas>; jsdom has no canvas. Replace with simple stand-ins
// so we can assert presence + props without exercising the real renderer.
vi.mock('@ant-design/charts', () => ({
  Column: ({ data }: { data: unknown[] }) => (
    <div data-testid="column-chart" data-rows={data.length} />
  ),
  Line: ({ data }: { data: unknown[] }) => <div data-testid="line-chart" data-rows={data.length} />,
}));

import { DashboardView } from './DashboardView';

const SAMPLE: DashboardStats = {
  kpi: { newCount: 4, resolvedCount: 2, openCount: 3, avgResolveMinutes: 90 },
  byCategory: [{ name: '관리자웹', count: 3 }],
  byAssignee: [{ name: '김현장', resolved: 2 }],
  byPriority: [{ priority: 'URGENT', count: 1 }],
  trend: [
    { date: '2026-05-22', newCount: 4, resolvedCount: 2 },
    { date: '2026-05-23', newCount: 1, resolvedCount: 1 },
  ],
};

const EMPTY: DashboardStats = {
  kpi: { newCount: 0, resolvedCount: 0, openCount: 0, avgResolveMinutes: 0 },
  byCategory: [],
  byAssignee: [],
  byPriority: [],
  trend: [],
};

function renderView() {
  return render(
    <MemoryRouter>
      <DashboardView />
    </MemoryRouter>
  );
}

describe('DashboardView', () => {
  beforeEach(() => {
    useDashboardStatsMock.mockReset();
  });

  it('renders the four KPI cards with the active period label (AC1, AC5)', () => {
    useDashboardStatsMock.mockReturnValue({ data: SAMPLE, isLoading: false, isError: false });
    renderView();

    // default period = today → labels reflect "오늘"
    expect(screen.getByText('오늘 신규')).toBeInTheDocument();
    expect(screen.getByText('오늘 처리')).toBeInTheDocument();
    expect(screen.getByText('미처리 총건')).toBeInTheDocument();
    expect(screen.getByText('현재 시점 기준')).toBeInTheDocument();
    expect(screen.getByText('오늘 평균 처리시간')).toBeInTheDocument();
    expect(screen.getByText('1시간 30분')).toBeInTheDocument();
  });

  it('changing the period triggers a refetch with the new key (AC5)', async () => {
    useDashboardStatsMock.mockReturnValue({ data: SAMPLE, isLoading: false, isError: false });
    const user = userEvent.setup();
    renderView();

    await user.click(screen.getByText('이번주'));

    await waitFor(() => expect(useDashboardStatsMock).toHaveBeenCalledWith('week'));
    expect(screen.getByText('이번주 신규')).toBeInTheDocument();
  });

  it('renders all three charts when data is present (AC2, AC3, AC4)', () => {
    useDashboardStatsMock.mockReturnValue({ data: SAMPLE, isLoading: false, isError: false });
    renderView();

    // Two Column charts (category + assignee) + one Line chart (trend) — series doubles trend rows.
    const columns = screen.getAllByTestId('column-chart');
    expect(columns).toHaveLength(2);
    expect(screen.getByTestId('line-chart')).toBeInTheDocument();
    expect(screen.getByTestId('line-chart').getAttribute('data-rows')).toBe(String(SAMPLE.trend.length * 2));
  });

  it('shows an empty-state message per chart when data is 0 (AC6)', () => {
    useDashboardStatsMock.mockReturnValue({ data: EMPTY, isLoading: false, isError: false });
    renderView();

    expect(screen.getByText('카테고리 데이터가 없습니다')).toBeInTheDocument();
    expect(screen.getByText('담당자 데이터가 없습니다')).toBeInTheDocument();
    expect(screen.getByText('추세 데이터가 없습니다')).toBeInTheDocument();
    // Charts should not be rendered at all when the series is empty
    expect(screen.queryByTestId('column-chart')).toBeNull();
    expect(screen.queryByTestId('line-chart')).toBeNull();
  });

  it('shows a spinner while loading', () => {
    useDashboardStatsMock.mockReturnValue({ data: undefined, isLoading: true, isError: false });
    const { container } = renderView();
    expect(container.querySelector('.ant-spin')).not.toBeNull();
  });

  it('shows an error placeholder when the query fails', () => {
    useDashboardStatsMock.mockReturnValue({ data: undefined, isLoading: false, isError: true });
    renderView();
    expect(screen.getByText('대시보드 데이터를 불러오지 못했습니다')).toBeInTheDocument();
  });
});
