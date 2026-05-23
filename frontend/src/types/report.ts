export type ReportKind = 'DAILY' | 'WEEKLY';

export type ReportFileMode = 'preview' | 'download';

/** Archive list row (Story 3.5) — mirrors backend ReportSummary (no filePath, security). */
export interface ReportSummary {
  id: number;
  kind: ReportKind;
  periodKey: string; // 'YYYY-MM-DD' (daily) | 'YYYY-Www' (weekly)
  sizeBytes: number;
  createdAt: string;
}
