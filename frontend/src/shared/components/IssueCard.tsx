import type { KeyboardEvent } from 'react';
import { Card, Space, Typography } from 'antd';
import dayjs from 'dayjs';
import { PriorityBadge } from './PriorityBadge';
import type { IssueSummary, Priority } from '../../types/issue';

const { Text } = Typography;

const BAR_COLOR: Record<Priority, string> = {
  URGENT: '#ff4d4f',
  HIGH: '#fa8c16',
  NORMAL: '#1677ff',
  LOW: '#8c8c8c',
};

interface IssueCardProps {
  issue: IssueSummary;
  onClick?: () => void;
  showAssignee?: boolean;
  compact?: boolean;
}

/**
 * Mobile/desktop issue card (§9.6.5): left priority color bar (URGENT emphasized),
 * title + category + 접수일. Keyboard-operable (role=button, Enter/Space), ≥44px touch.
 */
export function IssueCard({ issue, onClick, showAssignee = true, compact = false }: IssueCardProps) {
  const urgent = issue.priority === 'URGENT';

  function handleKeyDown(e: KeyboardEvent) {
    if (onClick && (e.key === 'Enter' || e.key === ' ')) {
      e.preventDefault();
      onClick();
    }
  }

  return (
    <Card
      role="button"
      tabIndex={0}
      aria-label={`이슈 ${issue.title}`}
      onClick={onClick}
      onKeyDown={handleKeyDown}
      styles={{ body: { padding: 12 } }}
      style={{
        borderLeft: `${urgent ? 6 : 4}px solid ${BAR_COLOR[issue.priority]}`,
        minHeight: 44,
        marginBottom: 8,
        cursor: onClick ? 'pointer' : undefined,
        ...(urgent ? { boxShadow: '0 0 0 1px #ffccc7' } : {}),
      }}
    >
      <Space direction="vertical" size={4} style={{ width: '100%' }}>
        <div style={{ display: 'flex', justifyContent: 'space-between', alignItems: 'center', gap: 8 }}>
          <Text strong style={{ fontSize: compact ? 14 : 16 }}>
            {issue.title}
          </Text>
          <PriorityBadge priority={issue.priority} size={compact ? 'sm' : 'md'} />
        </div>
        <Text type="secondary" style={{ fontSize: 12 }}>
          {issue.categoryL1Name} &gt; {issue.categoryL2Name} &gt; {issue.categoryL3Name}
        </Text>
        <div style={{ display: 'flex', justifyContent: 'space-between', gap: 8 }}>
          <Text type="secondary" style={{ fontSize: 12 }}>
            {dayjs(issue.createdAt).format('YYYY-MM-DD HH:mm')}
          </Text>
          {showAssignee && issue.assigneeName && (
            <Text type="secondary" style={{ fontSize: 12 }}>
              {issue.assigneeName}
            </Text>
          )}
        </div>
      </Space>
    </Card>
  );
}
