import { apiClient } from './client';
import type {
  CreateIssueRequest,
  IssueResponse,
  IssueListParams,
  IssueSummary,
  Page,
} from '../types/issue';

export async function createIssue(req: CreateIssueRequest): Promise<IssueResponse> {
  const res = await apiClient.post<IssueResponse>('/issues', req);
  return res.data;
}

export async function listIssues(params: IssueListParams): Promise<Page<IssueSummary>> {
  // indexes:null → arrays serialize as repeated `key=a&key=b` (Spring List<> binding)
  const res = await apiClient.get<Page<IssueSummary>>('/issues', {
    params,
    paramsSerializer: { indexes: null },
  });
  return res.data;
}
