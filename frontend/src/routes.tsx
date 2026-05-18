import { Navigate, Route, Routes } from 'react-router-dom';
import { LoginView } from './features/auth/LoginView';
import { RoleRedirect } from './features/home/RoleRedirect';
import { IssueListView } from './features/issue-list/IssueListView';
import { IssueFormView } from './features/issue-form/IssueFormView';
import { IssueDetailView } from './features/issue-detail/IssueDetailView';
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
  );
}
