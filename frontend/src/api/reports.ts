import { apiClient } from './client';
import type { Page } from '../types/issue';
import type { ReportFileMode, ReportKind, ReportSummary } from '../types/report';

export async function listReports(
  kind: ReportKind,
  page = 0,
  size = 20,
): Promise<Page<ReportSummary>> {
  const res = await apiClient.get<Page<ReportSummary>>('/reports', {
    params: { kind, page, size },
  });
  return res.data;
}

/**
 * Fetches the PDF as a blob via apiClient (JWT auto-attached).
 * Direct {@code window.open(serverUrl)} would skip the auth header → 401.
 * Caller is responsible for {@code URL.revokeObjectURL} after use.
 */
export async function fetchReportPdf(id: number, mode: ReportFileMode): Promise<Blob> {
  const res = await apiClient.get<Blob>(`/reports/${id}/file`, {
    params: { mode },
    responseType: 'blob',
  });
  return res.data;
}
