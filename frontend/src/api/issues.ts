import { apiClient } from './client';
import type {
  AddCommentRequest,
  AssignRequest,
  Attachment,
  Comment,
  CreateIssueRequest,
  IssueActivity,
  IssueDetail,
  IssueResponse,
  IssueListParams,
  IssueSummary,
  Page,
  TransitionRequest,
} from '../types/issue';
import type { AxiosProgressEvent } from 'axios';

export async function createIssue(req: CreateIssueRequest): Promise<IssueResponse> {
  const res = await apiClient.post<IssueResponse>('/issues', req);
  return res.data;
}

export async function getMyAssigned(): Promise<IssueSummary[]> {
  const res = await apiClient.get<IssueSummary[]>('/me/assigned');
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

export async function assignIssue(id: number, req: AssignRequest): Promise<IssueDetail> {
  const res = await apiClient.post<IssueDetail>(`/issues/${id}/assign`, req);
  return res.data;
}

export async function transitionIssue(id: number, req: TransitionRequest): Promise<IssueDetail> {
  const res = await apiClient.post<IssueDetail>(`/issues/${id}/transition`, req);
  return res.data;
}

export async function uploadAttachment(
  id: number,
  file: File,
  onProgress?: (e: AxiosProgressEvent) => void
): Promise<Attachment> {
  const form = new FormData();
  form.append('file', file);
  const res = await apiClient.post<Attachment>(`/issues/${id}/attachments`, form, {
    headers: { 'Content-Type': 'multipart/form-data' },
    onUploadProgress: onProgress,
  });
  return res.data;
}

/**
 * ADMIN-only CSV export of the issue list (Story 4.3). Returns a Blob; the caller is
 * responsible for triggering the download via a temporary {@code <a download>} and
 * revoking the object URL. {@code window.open(serverUrl)} would skip the JWT → 401.
 */
export async function exportIssuesCsv(
  params: IssueListParams,
  includePii: boolean,
): Promise<Blob> {
  const res = await apiClient.get<Blob>('/issues/export', {
    params: { ...params, format: 'csv', includePii },
    paramsSerializer: { indexes: null },
    responseType: 'blob',
  });
  return res.data;
}
