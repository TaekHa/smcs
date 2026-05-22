import { render, screen } from '@testing-library/react';
import { describe, it, expect } from 'vitest';
import { StatusBadge } from './StatusBadge';
import type { IssueStatus } from '../../types/issue';

describe('StatusBadge', () => {
  it.each([
    ['NEW', '신규'],
    ['ASSIGNED', '배정'],
    ['IN_PROGRESS', '진행중'],
    ['DONE', '완료'],
    ['VERIFIED', '검수'],
  ])('renders %s with Korean label, role=status and aria-label', (s, label) => {
    render(<StatusBadge status={s as IssueStatus} />);
    const badge = screen.getByRole('status', { name: `상태: ${label}` });
    expect(badge).toHaveTextContent(label);
  });

  it('without showProgress renders a single status tag (2.2 regression)', () => {
    render(<StatusBadge status="IN_PROGRESS" />);
    expect(screen.getByRole('status', { name: '상태: 진행중' })).toBeInTheDocument();
    // not the progress group
    expect(screen.queryByRole('group')).not.toBeInTheDocument();
  });

  describe('showProgress', () => {
    it('renders all 5 steps with labels and a group aria-label', () => {
      render(<StatusBadge status="IN_PROGRESS" showProgress />);
      const group = screen.getByRole('group', { name: '상태 진행: 진행중' });
      expect(group).toBeInTheDocument();
      ['신규', '배정', '진행중', '완료', '검수'].forEach((label) => {
        expect(screen.getByText(label)).toBeInTheDocument();
      });
    });

    it('marks the current step with aria-current="step"', () => {
      render(<StatusBadge status="ASSIGNED" showProgress />);
      const current = screen.getByText('배정');
      expect(current).toHaveAttribute('aria-current', 'step');
      // a later step is not current
      expect(screen.getByText('검수')).not.toHaveAttribute('aria-current');
    });
  });
});
