import { Tag } from 'antd';
import type { IssueStatus } from '../../types/issue';

const COLORS: Record<IssueStatus, string> = {
  NEW: '#13c2c2',
  ASSIGNED: '#1677ff',
  IN_PROGRESS: '#fa8c16',
  DONE: '#52c41a',
  VERIFIED: '#722ed1',
};

const LABELS: Record<IssueStatus, string> = {
  NEW: '신규',
  ASSIGNED: '배정',
  IN_PROGRESS: '진행중',
  DONE: '완료',
  VERIFIED: '검수',
};

interface StatusBadgeProps {
  status: IssueStatus;
  /** Story 2.3 only — 5-step progress bar. Unused here (kept for API stability). */
  showProgress?: boolean;
}

/** Issue status indicator: color + text label (never color alone — a11y §9.8.4). */
export function StatusBadge({ status }: StatusBadgeProps) {
  const text = LABELS[status];
  return (
    <Tag color={COLORS[status]} role="status" aria-label={`상태: ${text}`} style={{ margin: 0 }}>
      {text}
    </Tag>
  );
}
