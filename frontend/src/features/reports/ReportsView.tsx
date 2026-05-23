import { useState } from 'react';
import { Button, Card, Empty, Space, Table, Tabs, Typography, message } from 'antd';
import type { TableColumnsType } from 'antd';
import dayjs from 'dayjs';
import { fetchReportPdf } from '../../api/reports';
import { useReports } from '../../shared/hooks/useReports';
import type { ReportFileMode, ReportKind, ReportSummary } from '../../types/report';

const { Title } = Typography;

const KIND_LABEL: Record<ReportKind, string> = { DAILY: '일간', WEEKLY: '주간' };
const PERIOD_HEADER: Record<ReportKind, string> = { DAILY: '일자', WEEKLY: '주차' };

function formatSize(bytes: number): string {
  if (bytes < 1024) return `${bytes} B`;
  if (bytes < 1024 * 1024) return `${(bytes / 1024).toFixed(1)} KB`;
  return `${(bytes / 1024 / 1024).toFixed(1)} MB`;
}

async function openPdf(report: ReportSummary, mode: ReportFileMode) {
  // blob fetch keeps the JWT header attached (Story 3.5 — direct <a>/window.open(serverUrl) is 401).
  const blob = await fetchReportPdf(report.id, mode);
  const url = URL.createObjectURL(blob);
  const filename = `${report.kind}-${report.periodKey}.pdf`;
  if (mode === 'preview') {
    const win = window.open(url, '_blank', 'noopener,noreferrer');
    if (!win) {
      // popup blocker — fall back to download so the user still gets the file
      triggerDownload(url, filename);
    }
  } else {
    triggerDownload(url, filename);
  }
  // Revoke after the browser had a chance to load the blob (preview tab keeps a reference).
  setTimeout(() => URL.revokeObjectURL(url), 60_000);
}

function triggerDownload(url: string, filename: string) {
  const a = document.createElement('a');
  a.href = url;
  a.download = filename;
  document.body.appendChild(a);
  a.click();
  a.remove();
}

function ReportsTable({ kind }: { kind: ReportKind }) {
  const [page, setPage] = useState(0);
  const { data, isLoading } = useReports(kind, page);
  const rows = data?.content ?? [];

  const columns: TableColumnsType<ReportSummary> = [
    { title: PERIOD_HEADER[kind], dataIndex: 'periodKey', key: 'periodKey' },
    {
      title: '생성 시각',
      dataIndex: 'createdAt',
      key: 'createdAt',
      render: (v: string) => dayjs(v).format('YYYY-MM-DD HH:mm'),
    },
    {
      title: '크기',
      dataIndex: 'sizeBytes',
      key: 'sizeBytes',
      render: formatSize,
    },
    {
      title: '액션',
      key: 'actions',
      render: (_: unknown, r: ReportSummary) => (
        <Space>
          <Button
            size="small"
            aria-label={`${r.periodKey} ${KIND_LABEL[r.kind]} 보고서 미리보기`}
            onClick={() => openPdf(r, 'preview').catch(() => message.error('미리보기에 실패했습니다'))}
          >
            미리보기
          </Button>
          <Button
            size="small"
            type="primary"
            aria-label={`${r.periodKey} ${KIND_LABEL[r.kind]} 보고서 다운로드`}
            onClick={() => openPdf(r, 'download').catch(() => message.error('다운로드에 실패했습니다'))}
          >
            다운로드
          </Button>
        </Space>
      ),
    },
  ];

  return (
    <Table<ReportSummary>
      rowKey="id"
      loading={isLoading}
      dataSource={rows}
      columns={columns}
      locale={{ emptyText: <Empty description="아직 생성된 보고서가 없습니다" /> }}
      pagination={{
        current: page + 1,
        pageSize: data?.size ?? 20,
        total: data?.totalElements ?? 0,
        onChange: (p) => setPage(p - 1),
        showSizeChanger: false,
      }}
    />
  );
}

/** ADMIN-only report archive (Story 3.5). Native browser PDF viewer — no extra deps. */
export function ReportsView() {
  return (
    <Card style={{ maxWidth: 960, margin: '0 auto' }}>
      <Title level={4} style={{ marginTop: 0 }}>
        보고서 보관함
      </Title>
      <Tabs
        defaultActiveKey="DAILY"
        items={[
          { key: 'DAILY', label: '일간', children: <ReportsTable kind="DAILY" /> },
          { key: 'WEEKLY', label: '주간', children: <ReportsTable kind="WEEKLY" /> },
        ]}
      />
    </Card>
  );
}
