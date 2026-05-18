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
});
