import { lazy, Suspense } from 'react';
import { Navigate, Route, Routes } from 'react-router-dom';
import { Spin } from 'antd';
import { LoginView } from './features/auth/LoginView';
import { RoleRedirect } from './features/home/RoleRedirect';
import { IssueListView } from './features/issue-list/IssueListView';
import { ForbiddenView } from './features/error/ForbiddenView';
import { NotFoundView } from './features/error/NotFoundView';
import { RequireAuth } from './auth/RequireAuth';
import { RequireRole } from './auth/RequireRole';
import { AppLayout } from './layout/AppLayout';
import { useAuth } from './auth/useAuthStore';

// architecture §9.5 / Story 1.5 carry-over #3 — heavy feature routes are
// code-split (RHF+Zod / mobile). Boot-path views stay eager. Named exports
// are remapped to `default` for React.lazy.
const IssueFormView = lazy(() =>
  import('./features/issue-form/IssueFormView').then((m) => ({ default: m.IssueFormView }))
);
const IssueDetailView = lazy(() =>
  import('./features/issue-detail/IssueDetailView').then((m) => ({ default: m.IssueDetailView }))
);
const MobileFieldHomeView = lazy(() =>
  import('./features/mobile-field/MobileFieldHomeView').then((m) => ({
    default: m.MobileFieldHomeView,
  }))
);

function RouteFallback() {
  return (
    <div style={{ display: 'flex', minHeight: '60vh', alignItems: 'center', justifyContent: 'center' }}>
      <Spin size="large" />
    </div>
  );
}

function LoginRoute() {
  const user = useAuth();
  if (user) {
    return <Navigate to="/" replace />;
  }
  return <LoginView />;
}

export function AppRoutes() {
  return (
    <Suspense fallback={<RouteFallback />}>
    <Routes>
      <Route path="/login" element={<LoginRoute />} />

      <Route
        path="/"
        element={
          <RequireAuth>
            <RoleRedirect />
          </RequireAuth>
        }
      />

      <Route
        path="/issues"
        element={
          <RequireAuth>
            <RequireRole roles={['AGENT', 'ADMIN']}>
              <AppLayout>
                <IssueListView />
              </AppLayout>
            </RequireRole>
          </RequireAuth>
        }
      />

      <Route
        path="/issues/new"
        element={
          <RequireAuth>
            <RequireRole roles={['AGENT', 'ADMIN']}>
              <AppLayout>
                <IssueFormView />
              </AppLayout>
            </RequireRole>
          </RequireAuth>
        }
      />

      <Route
        path="/issues/:id"
        element={
          <RequireAuth>
            <AppLayout>
              <IssueDetailView />
            </AppLayout>
          </RequireAuth>
        }
      />

      <Route
        path="/m"
        element={
          <RequireAuth>
            <RequireRole roles={['FIELD', 'ADMIN']}>
              <AppLayout>
                <MobileFieldHomeView />
              </AppLayout>
            </RequireRole>
          </RequireAuth>
        }
      />

      <Route path="/403" element={<ForbiddenView />} />

      {/* Future routes (Epic 2~4):
          /m/issues/:id, /dashboard, /reports, /reports/:kind/:date,
          /notifications, /admin/users, /admin/categories
          NOTE: /issues/:id is a Story 2.1 placeholder — full detail +
          §9.5 authz guard (assignee/AGENT/ADMIN) is Story 2.3. */}

      <Route path="*" element={<NotFoundView />} />
    </Routes>
    </Suspense>
  );
}
