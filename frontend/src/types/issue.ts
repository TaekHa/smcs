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
