import { Badge, Button } from 'antd';
import { BellOutlined } from '@ant-design/icons';
import { useNavigate } from 'react-router-dom';
import { useUnreadCount } from '../hooks/useNotifications';

/** Header bell with unread badge (Story 2.8). Polls every 30s; taps to the notifications page. */
export function NotificationBell() {
  const navigate = useNavigate();
  const { data } = useUnreadCount();
  const count = data?.count ?? 0;
  return (
    <Badge count={count} size="small">
      <Button
        type="text"
        icon={<BellOutlined />}
        aria-label={`미읽음 알림 ${count}건`}
        onClick={() => navigate('/notifications')}
      />
    </Badge>
  );
}
