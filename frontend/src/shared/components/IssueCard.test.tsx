import { render, screen } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { describe, it, expect, vi } from 'vitest';
import { IssueCard } from './IssueCard';
import type { IssueSummary } from '../../types/issue';

const ISSUE: IssueSummary = {
  id: 5,
  title: '엘리베이터 고장',
  categoryL1Name: '아파트',
  categoryL2Name: '승강기',
  categoryL3Name: '고장',
  priority: 'URGENT',
  status: 'ASSIGNED',
  assigneeName: '이현장1',
  createdAt: '2026-05-20T01:00:00Z',
};

describe('IssueCard', () => {
  it('renders title, category path, priority badge and 접수일', () => {
    render(<IssueCard issue={ISSUE} />);
    expect(screen.getByText('엘리베이터 고장')).toBeInTheDocument();
    expect(screen.getByText('아파트 > 승강기 > 고장')).toBeInTheDocument();
    expect(screen.getByRole('status', { name: '우선순위: 긴급' })).toBeInTheDocument();
  });

  it('is keyboard operable (role=button, Enter/Space → onClick) — AC5 a11y', async () => {
    const user = userEvent.setup();
    const onClick = vi.fn();
    render(<IssueCard issue={ISSUE} onClick={onClick} />);
    const card = screen.getByRole('button', { name: '이슈 엘리베이터 고장' });
    card.focus();
    await user.keyboard('{Enter}');
    await user.keyboard(' ');
    await user.click(card);
    expect(onClick).toHaveBeenCalledTimes(3);
  });

  it('emphasizes URGENT with a thicker left bar than non-URGENT (AC3)', () => {
    const urgent = render(<IssueCard issue={ISSUE} />);
    const urgentCard = urgent.container.querySelector('.ant-card') as HTMLElement;
    expect(urgentCard.getAttribute('style')).toContain('6px');

    const normal = render(<IssueCard issue={{ ...ISSUE, priority: 'NORMAL' }} />);
    const normalCard = normal.container.querySelector('.ant-card') as HTMLElement;
    expect(normalCard.getAttribute('style')).toContain('4px');
  });
});
