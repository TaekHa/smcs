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

/** Canonical status flow (§9.6.2). */
const STATUS_ORDER: IssueStatus[] = ['NEW', 'ASSIGNED', 'IN_PROGRESS', 'DONE', 'VERIFIED'];

interface StatusBadgeProps {
  status: IssueStatus;
  /** Story 2.3 — render the 5-step horizontal progress bar instead of a single tag. */
  showProgress?: boolean;
}

/** Issue status indicator: color + text label (never color alone — a11y §9.8.4). */
export function StatusBadge({ status, showProgress }: StatusBadgeProps) {
  const text = LABELS[status];

  if (!showProgress) {
    return (
      <Tag color={COLORS[status]} role="status" aria-label={`상태: ${text}`} style={{ margin: 0 }}>
        {text}
      </Tag>
    );
  }

  // 5-step progress bar: steps up to and including the current status are active.
  const currentIndex = STATUS_ORDER.indexOf(status);
  return (
    <div
      role="group"
      aria-label={`상태 진행: ${text}`}
      style={{ display: 'flex', gap: 4, flexWrap: 'wrap', alignItems: 'center' }}
    >
      {STATUS_ORDER.map((s, i) => {
        const active = i <= currentIndex;
        const isCurrent = s === status;
        return (
          <span
            key={s}
            aria-current={isCurrent ? 'step' : undefined}
            style={{
              padding: '2px 10px',
              borderRadius: 4,
              fontSize: 12,
              color: active ? '#fff' : '#8c8c8c',
              background: active ? COLORS[s] : '#f0f0f0',
              fontWeight: isCurrent ? 600 : 400,
              outline: isCurrent ? '2px solid #000' : undefined,
            }}
          >
            {LABELS[s]}
          </span>
        );
      })}
    </div>
  );
}
