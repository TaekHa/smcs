export type Priority = 'URGENT' | 'HIGH' | 'NORMAL' | 'LOW';

export type IssueStatus = 'NEW' | 'ASSIGNED' | 'IN_PROGRESS' | 'DONE' | 'VERIFIED';

export interface CategoryOption {
  id: number;
  name: string;
  level: number;
  /** Story 4.2 — auto category suggestion source. Optional for backward compatibility. */
  keywords?: string[];
}

export interface CreateIssueRequest {
  title: string;
  callerName: string;
  callerPhone: string;
  categoryL1Id: number;
  categoryL2Id: number;
  categoryL3Id: number;
  priority: Priority;
  description: string;
}

export interface IssueResponse {
  id: number;
  title: string;
  priority: Priority;
  status: IssueStatus;
  createdAt: string;
}

export interface IssueSummary {
  id: number;
  title: string;
  categoryL1Name: string;
  categoryL2Name: string;
  categoryL3Name: string;
  priority: Priority;
  status: IssueStatus;
  assigneeName: string | null;
  createdAt: string;
}

export interface Page<T> {
  content: T[];
  totalElements: number;
  totalPages: number;
  number: number;
  size: number;
}

// ── Story 2.3: issue detail / comments / activity ──────────────────────────

export type CommentKind = 'NOTE' | 'FIELD_ACTION' | 'SYSTEM';

export type IssueEventType =
  | 'CREATED'
  | 'STATUS_CHANGED'
  | 'ASSIGNED'
  | 'COMMENTED'
  | 'ATTACHMENT_ADDED'
  | 'RESOLVED';

export interface CategoryRef {
  id: number;
  name: string;
}

export interface Comment {
  id: number;
  authorName: string;
  body: string;
  kind: CommentKind;
  createdAt: string;
}

export interface IssueActivity {
  id: number;
  eventType: IssueEventType;
  actorName: string;
  fromValue: string | null;
  toValue: string | null;
  createdAt: string;
}

export interface Attachment {
  id: number;
  originalName: string;
  url: string;
  mimeType: string;
  sizeBytes: number;
  createdAt: string;
}

export interface IssueDetail {
  id: number;
  title: string;
  description: string;
  categoryL1: CategoryRef;
  categoryL2: CategoryRef;
  categoryL3: CategoryRef;
  priority: Priority;
  status: IssueStatus;
  createdByName: string;
  assigneeName: string | null;
  resolvedAt: string | null;
  createdAt: string;
  updatedAt: string;
  // PII — non-null only for AGENT/ADMIN; null for FIELD (Deviation #2).
  callerName: string | null;
  callerPhone: string | null;
  comments: Comment[];
  attachments: Attachment[];
}

export interface AddCommentRequest {
  body: string;
  kind?: CommentKind; // default NOTE; mobile field action sends FIELD_ACTION (Story 2.6)
}

// ── Story 2.4: assignment / transition ─────────────────────────────────────

export interface AssignRequest {
  assigneeId: number;
}

export interface TransitionRequest {
  to: IssueStatus;
  reason?: string;
}

export interface IssueListParams {
  page?: number;
  size?: number;
  sort?: string; // e.g. 'createdAt,desc' — NOT 'priority' (severity order is server default, PO R2)
  status?: IssueStatus[];
  categoryL1Id?: number[];
  categoryL2Id?: number[];
  categoryL3Id?: number[];
  assigneeId?: number;
  from?: string; // ISO date (YYYY-MM-DD)
  to?: string;
  q?: string;
}
