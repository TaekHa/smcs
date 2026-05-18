import { render, screen } from '@testing-library/react';
import { describe, it, expect } from 'vitest';
import { PriorityBadge } from './PriorityBadge';
import type { Priority } from '../../types/issue';

describe('PriorityBadge', () => {
  it.each([
    ['URGENT', '긴급'],
    ['HIGH', '높음'],
    ['NORMAL', '보통'],
    ['LOW', '낮음'],
  ])('renders %s with Korean label, role=status and aria-label', (p, label) => {
    render(<PriorityBadge priority={p as Priority} />);
    const badge = screen.getByRole('status', { name: `우선순위: ${label}` });
    expect(badge).toHaveTextContent(label);
  });

  it('still renders label when icon hidden (color is never the only cue)', () => {
    render(<PriorityBadge priority="URGENT" showIcon={false} />);
    expect(screen.getByRole('status', { name: '우선순위: 긴급' })).toHaveTextContent('긴급');
  });

  it('accepts a custom label', () => {
    render(<PriorityBadge priority="LOW" label="낮은 우선순위" />);
    expect(screen.getByRole('status', { name: '우선순위: 낮은 우선순위' })).toBeInTheDocument();
  });
});
