import type { ReactNode } from 'react';
import { Button, Layout, Space, Tag, Typography } from 'antd';
import { Link, useNavigate } from 'react-router-dom';
import { useAuth, useAuthStore } from '../auth/useAuthStore';
import { NotificationBell } from '../shared/components/NotificationBell';
import type { Role } from '../types/auth';

const { Header, Content } = Layout;
const { Text } = Typography;

const ROLE_TAG_COLOR: Record<Role, string> = {
  AGENT: 'blue',
  FIELD: 'green',
  ADMIN: 'purple',
};

interface AppLayoutProps {
  children: ReactNode;
}

export function AppLayout({ children }: AppLayoutProps) {
  const user = useAuth();
  const navigate = useNavigate();

  function handleLogout() {
    useAuthStore.getState().logout();
    navigate('/login', { replace: true });
  }

  return (
    <Layout style={{ minHeight: '100vh' }}>
      <Header
        style={{
          display: 'flex',
          alignItems: 'center',
          justifyContent: 'space-between',
          padding: '0 24px',
          background: '#fff',
          borderBottom: '1px solid #f0f0f0',
        }}
      >
        <Space size="large">
          <Text strong style={{ fontSize: 18 }}>
            SMCS
          </Text>
          <nav aria-label="주 메뉴">
            <Space size="middle">
              {user && (user.role === 'AGENT' || user.role === 'ADMIN') && (
                <Link to="/issues">이슈</Link>
              )}
              {user && (user.role === 'FIELD' || user.role === 'ADMIN') && (
                <Link to="/m">내 작업</Link>
              )}
            </Space>
          </nav>
        </Space>
        <Space>
          {user && <NotificationBell />}
          {user && (
            <>
              <Text>{user.displayName}</Text>
              <Tag color={ROLE_TAG_COLOR[user.role]} aria-label={`역할: ${user.role}`}>
                {user.role}
              </Tag>
            </>
          )}
          <Button onClick={handleLogout}>로그아웃</Button>
        </Space>
      </Header>
      <Content style={{ padding: 24 }}>{children}</Content>
    </Layout>
  );
}
