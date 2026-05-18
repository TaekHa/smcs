import { Tag } from 'antd';
import type { Priority } from '../../types/issue';

const COLORS: Record<Priority, string> = {
  URGENT: '#ff4d4f',
  HIGH: '#fa8c16',
  NORMAL: '#1677ff',
  LOW: '#8c8c8c',
};

const ICONS: Record<Priority, string> = {
  URGENT: '🔥',
  HIGH: '⚠️',
  NORMAL: '•',
  LOW: '○',
};

const LABELS: Record<Priority, string> = {
  URGENT: '긴급',
  HIGH: '높음',
  NORMAL: '보통',
  LOW: '낮음',
};

interface PriorityBadgeProps {
  priority: Priority;
  size?: 'sm' | 'md';
  showIcon?: boolean;
  label?: string;
}

/**
 * Priority indicator using color + text + icon (never color alone — a11y, PRD FR16).
 */
export function PriorityBadge({
  priority,
  size = 'md',
  showIcon = true,
  label,
}: PriorityBadgeProps) {
  const text = label ?? LABELS[priority];
  return (
    <Tag
      color={COLORS[priority]}
      role="status"
      aria-label={`우선순위: ${text}`}
      style={{ fontSize: size === 'sm' ? 11 : 13, margin: 0 }}
    >
      {showIcon && <span aria-hidden>{ICONS[priority]} </span>}
      {text}
    </Tag>
  );
}
