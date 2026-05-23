import { useQuery } from '@tanstack/react-query';
import { listReports } from '../../api/reports';
import type { ReportKind } from '../../types/report';

/** Paged archive list for the given kind (newest first). */
export function useReports(kind: ReportKind, page = 0) {
  return useQuery({
    queryKey: ['reports', kind, page],
    queryFn: () => listReports(kind, page),
  });
}
