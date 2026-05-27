import { useCallback } from 'react';
import { useNavigate } from 'react-router-dom';
import { useMarkRead } from '../../shared/hooks/useNotifications';
import type { Notification } from '../../types/notification';

/**
 * Single SoT for opening a notification (Story 4.1):
 * marks the notification read, then navigates to its target.
 * Issue-scoped → /issues/:id. Report-scoped (issueId == null, Story 3.4) → /reports.
 */
export function useOpenNotification() {
  const navigate = useNavigate();
  const markRead = useMarkRead();
  return useCallback(
    (n: Notification) => {
      markRead.mutate(n.id);
      navigate(n.issueId == null ? '/reports' : `/issues/${n.issueId}`);
    },
    [navigate, markRead]
  );
}
