export type Priority = 'URGENT' | 'HIGH' | 'NORMAL' | 'LOW';

export type IssueStatus = 'NEW' | 'ASSIGNED' | 'IN_PROGRESS' | 'DONE' | 'VERIFIED';

export interface CategoryOption {
  id: number;
  name: string;
  level: number;
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
