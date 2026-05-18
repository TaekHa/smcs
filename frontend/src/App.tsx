import { useEffect, useState } from 'react';
import { ConfigProvider, Spin } from 'antd';
import koKR from 'antd/locale/ko_KR';
import { BrowserRouter } from 'react-router-dom';
import { useAuthStore, useHydrated } from './auth/useAuthStore';
import { getMe } from './api/me';
import { AppRoutes } from './routes';

export default function App() {
  const hydrated = useHydrated();
  const [validating, setValidating] = useState(true);

  useEffect(() => {
    const state = useAuthStore.getState();
    state.hydrate();
    const { token } = useAuthStore.getState();
    if (!token) {
      setValidating(false);
      return;
    }
    getMe()
      .then((user) => {
        useAuthStore.getState().setSession(token, user);
      })
      .catch(() => {
        // axios interceptor handled 401 → logout. Other errors leave session as-is
        // so the user can retry once connectivity returns.
      })
      .finally(() => setValidating(false));
  }, []);

  if (!hydrated || validating) {
    return (
      <div style={{ display: 'flex', minHeight: '100vh', alignItems: 'center', justifyContent: 'center' }}>
        <Spin size="large" />
      </div>
    );
  }

  return (
    <ConfigProvider locale={koKR}>
      <BrowserRouter>
        <AppRoutes />
      </BrowserRouter>
    </ConfigProvider>
  );
}
