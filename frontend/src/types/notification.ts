export type NotificationKind =
  | 'ISSUE_ASSIGNED'
  | 'ISSUE_COMMENTED'
  | 'ISSUE_STATUS_CHANGED'
  | 'ISSUE_REOPENED';

export interface Notification {
  id: number;
  kind: NotificationKind;
  issueId: number;
  actorName: string | null;
  message: string;
  readAt: string | null;
  createdAt: string;
}

export interface UnreadCount {
  count: number;
}
