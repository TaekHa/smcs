import { apiClient } from './client';
import type { CreateIssueRequest, IssueResponse } from '../types/issue';

export async function createIssue(req: CreateIssueRequest): Promise<IssueResponse> {
  const res = await apiClient.post<IssueResponse>('/issues', req);
  return res.data;
}
