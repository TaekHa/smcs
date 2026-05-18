import { Navigate } from 'react-router-dom';
import { useAuth } from '../../auth/useAuthStore';

export function RoleRedirect() {
  const user = useAuth();
  if (!user) {
    return <Navigate to="/login" replace />;
  }
  if (user.role === 'FIELD') {
    return <Navigate to="/m" replace />;
  }
  return <Navigate to="/issues" replace />;
}
