import { Navigate, Route, Routes } from 'react-router-dom';
import { LoginView } from './features/auth/LoginView';
import { RoleRedirect } from './features/home/RoleRedirect';
import { IssueListView } from './features/issue-list/IssueListView';
import { MobileFieldHomeView } from './features/mobile-field/MobileFieldHomeView';
import { ForbiddenView } from './features/error/ForbiddenView';
import { NotFoundView } from './features/error/NotFoundView';
import { RequireAuth } from './auth/RequireAuth';
import { RequireRole } from './auth/RequireRole';
import { AppLayout } from './layout/AppLayout';
import { useAuth } from './auth/useAuthStore';

function LoginRoute() {
  const user = useAuth();
  if (user) {
    return <Navigate to="/" replace />;
  }
  return <LoginView />;
}

export function AppRoutes() {
  return (
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
          /issues/new, /issues/:id, /m/issues/:id, /dashboard,
          /reports, /reports/:kind/:date, /notifications,
          /admin/users, /admin/categories */}

      <Route path="*" element={<NotFoundView />} />
    </Routes>
  );
}
