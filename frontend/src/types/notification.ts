export type NotificationKind =
  | 'ISSUE_ASSIGNED'
  | 'ISSUE_COMMENTED'
  | 'ISSUE_STATUS_CHANGED'
  | 'ISSUE_REOPENED'
  // Story 3.4 — V7 added report-scoped kinds (issueId is null for these).
  | 'REPORT_READY'
  | 'REPORT_FAILED';

export interface Notification {
  id: number;
  kind: NotificationKind;
  /** Null for REPORT_READY / REPORT_FAILED (no owning issue). */
  issueId: number | null;
  actorName: string | null;
  message: string;
  readAt: string | null;
  createdAt: string;
}

export interface UnreadCount {
  count: number;
}
