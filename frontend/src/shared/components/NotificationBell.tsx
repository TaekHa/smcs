import { useState } from 'react';
import { Badge, Button, Divider, Empty, List, Popover, Typography } from 'antd';
import { BellOutlined } from '@ant-design/icons';
import { useNavigate } from 'react-router-dom';
import dayjs from 'dayjs';
import relativeTime from 'dayjs/plugin/relativeTime';
import 'dayjs/locale/ko';
import { useNotifications, useUnreadCount } from '../hooks/useNotifications';
import { useOpenNotification } from '../../features/notifications/useOpenNotification';

dayjs.extend(relativeTime);

const { Text } = Typography;

const DROPDOWN_ITEMS = 10;

/**
 * Header bell with unread badge (Story 2.8) + recent-10 dropdown (Story 4.1 AC2).
 * Polls /unread-count every 30s; clicking the bell opens a popover with the latest
 * notifications (sliced from the existing list cache — single SoT with /notifications).
 * No pulse/sound on new arrivals (PRD AC6 — "조용히 갱신").
 */
export function NotificationBell() {
  const navigate = useNavigate();
  const [open, setOpen] = useState(false);
  const { data: countData } = useUnreadCount();
  const { data: listData } = useNotifications(0, { enabled: open });
  const openNotification = useOpenNotification();

  const count = countData?.count ?? 0;
  const recent = (listData?.content ?? []).slice(0, DROPDOWN_ITEMS);

  function viewAll() {
    setOpen(false);
    navigate('/notifications');
  }

  function handleItemClick(n: (typeof recent)[number]) {
    openNotification(n);
    setOpen(false);
  }

  const content = (
    <div style={{ width: 320 }} role="region" aria-label="알림 미리보기">
      <div style={{ padding: '4px 8px' }}>
        <Text strong>알림</Text>
        {count > 0 && (
          <Text type="secondary" style={{ marginLeft: 8 }}>
            ({count} 미읽음)
          </Text>
        )}
      </div>
      <Divider style={{ margin: '4px 0' }} />
      {recent.length === 0 ? (
        <Empty
          image={Empty.PRESENTED_IMAGE_SIMPLE}
          description="새 알림이 없습니다"
          style={{ padding: '12px 0' }}
        />
      ) : (
        <List
          size="small"
          dataSource={recent}
          renderItem={(n) => (
            <List.Item
              onClick={() => handleItemClick(n)}
              style={{
                cursor: 'pointer',
                padding: '8px 12px',
                background: n.readAt ? undefined : '#e6f4ff',
              }}
            >
              <List.Item.Meta
                title={
                  <Text strong={!n.readAt} style={{ fontSize: 13 }}>
                    {n.message}
                  </Text>
                }
                description={
                  <Text type="secondary" style={{ fontSize: 11 }}>
                    {dayjs(n.createdAt).locale('ko').fromNow()}
                  </Text>
                }
              />
            </List.Item>
          )}
        />
      )}
      <Divider style={{ margin: '4px 0' }} />
      <div style={{ textAlign: 'center', padding: '4px 8px' }}>
        <Button type="link" onClick={viewAll} size="small">
          모두 보기
        </Button>
      </div>
    </div>
  );

  return (
    <Popover
      content={content}
      trigger="click"
      placement="bottomRight"
      open={open}
      onOpenChange={setOpen}
    >
      <Badge count={count} size="small">
        <Button type="text" icon={<BellOutlined />} aria-label={`미읽음 알림 ${count}건`} />
      </Badge>
    </Popover>
  );
}
