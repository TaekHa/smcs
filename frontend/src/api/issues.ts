import { apiClient } from './client';
import type {
  AddCommentRequest,
  Comment,
  CreateIssueRequest,
  IssueActivity,
  IssueDetail,
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

export async function getIssue(id: number): Promise<IssueDetail> {
  const res = await apiClient.get<IssueDetail>(`/issues/${id}`);
  return res.data;
}

export async function addComment(id: number, req: AddCommentRequest): Promise<Comment> {
  const res = await apiClient.post<Comment>(`/issues/${id}/comments`, req);
  return res.data;
}

export async function listIssueEvents(id: number): Promise<IssueActivity[]> {
  const res = await apiClient.get<IssueActivity[]>(`/issues/${id}/events`);
  return res.data;
}
