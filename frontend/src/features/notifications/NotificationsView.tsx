import { Button, Card, Empty, List, Typography } from 'antd';
import dayjs from 'dayjs';
import { useNavigate } from 'react-router-dom';
import { useMarkAllRead, useMarkRead, useNotifications } from '../../shared/hooks/useNotifications';
import type { Notification } from '../../types/notification';

const { Title, Text } = Typography;

/** Notification list page (Story 2.8). Click → mark read + go to the issue. */
export function NotificationsView() {
  const navigate = useNavigate();
  const { data, isLoading } = useNotifications();
  const markRead = useMarkRead();
  const markAllRead = useMarkAllRead();

  function open(n: Notification) {
    markRead.mutate(n.id);
    // Story 3.4/3.5 — report-scoped notifications (issueId == null) link to the archive.
    navigate(n.issueId == null ? '/reports' : `/issues/${n.issueId}`);
  }

  return (
    <Card style={{ maxWidth: 720, margin: '0 auto' }}>
      <div style={{ display: 'flex', justifyContent: 'space-between', alignItems: 'center', marginBottom: 8 }}>
        <Title level={4} style={{ margin: 0 }}>
          알림
        </Title>
        <Button onClick={() => markAllRead.mutate()} loading={markAllRead.isPending}>
          모두 읽음
        </Button>
      </div>
      <List
        loading={isLoading}
        dataSource={data?.content ?? []}
        locale={{ emptyText: <Empty description="알림이 없습니다" /> }}
        renderItem={(n) => (
          <List.Item
            onClick={() => open(n)}
            style={{ cursor: 'pointer', background: n.readAt ? undefined : '#e6f4ff' }}
          >
            <List.Item.Meta
              title={<Text strong={!n.readAt}>{n.message}</Text>}
              description={
                <Text type="secondary" style={{ fontSize: 12 }}>
                  {dayjs(n.createdAt).format('YYYY-MM-DD HH:mm')}
                </Text>
              }
            />
          </List.Item>
        )}
      />
    </Card>
  );
}
