import { useEffect, useState } from 'react';
import {
  Button,
  Card,
  DatePicker,
  Input,
  Popconfirm,
  Select,
  Space,
  Table,
  Typography,
  message,
} from 'antd';
import type { ColumnsType } from 'antd/es/table';
import type { SorterResult } from 'antd/es/table/interface';
import { DownloadOutlined } from '@ant-design/icons';
import { useNavigate } from 'react-router-dom';
import dayjs from 'dayjs';
import { exportIssuesCsv } from '../../api/issues';
import { useAuth } from '../../auth/useAuthStore';
import { useIssues } from '../../shared/hooks/useIssues';
import { useCategories } from '../../shared/hooks/useCategories';
import { useUsers } from '../../shared/hooks/useUsers';
import { PriorityBadge } from '../../shared/components/PriorityBadge';
import { StatusBadge } from '../../shared/components/StatusBadge';
import type { IssueListParams, IssueStatus, IssueSummary } from '../../types/issue';

const { RangePicker } = DatePicker;
const { Title } = Typography;

const STATUS_OPTIONS: { value: IssueStatus; label: string }[] = [
  { value: 'NEW', label: '신규' },
  { value: 'ASSIGNED', label: '배정' },
  { value: 'IN_PROGRESS', label: '진행중' },
  { value: 'DONE', label: '완료' },
  { value: 'VERIFIED', label: '검수' },
];

const PAGE_SIZE = 50;

export function IssueListView() {
  const navigate = useNavigate();
  const user = useAuth();
  const isAdmin = user?.role === 'ADMIN';
  const [params, setParams] = useState<IssueListParams>({ page: 0, size: PAGE_SIZE });
  const [searchText, setSearchText] = useState('');
  const [exporting, setExporting] = useState<'plain' | 'pii' | null>(null);

  // debounce search → params.q (reset to first page)
  useEffect(() => {
    const t = setTimeout(() => {
      setParams((p) => ({ ...p, q: searchText || undefined, page: 0 }));
    }, 300);
    return () => clearTimeout(t);
  }, [searchText]);

  const { data, isFetching } = useIssues(params);
  const l1 = useCategories(1);
  const l2 = useCategories(2);
  const l3 = useCategories(3);
  const users = useUsers();

  function patch(next: Partial<IssueListParams>) {
    setParams((p) => ({ ...p, ...next, page: 0 }));
  }

  async function handleExport(includePii: boolean) {
    const mode = includePii ? 'pii' : 'plain';
    setExporting(mode);
    try {
      const blob = await exportIssuesCsv(params, includePii);
      const url = URL.createObjectURL(blob);
      const filename = `issues-${dayjs().format('YYYYMMDD-HHmmss')}.csv`;
      const a = document.createElement('a');
      a.href = url;
      a.download = filename;
      document.body.appendChild(a);
      a.click();
      document.body.removeChild(a);
      // Revoke after a delay so the browser has time to start the download (3.5 pattern).
      setTimeout(() => URL.revokeObjectURL(url), 60_000);
    } catch (err: unknown) {
      message.error(await extractErrorMessage(err));
    } finally {
      setExporting(null);
    }
  }

  const columns: ColumnsType<IssueSummary> = [
    { title: 'ID', dataIndex: 'id', width: 72, sorter: true },
    { title: '제목', dataIndex: 'title', sorter: true, ellipsis: true },
    {
      title: '카테고리',
      key: 'category',
      render: (_, r) => `${r.categoryL1Name} > ${r.categoryL2Name} > ${r.categoryL3Name}`,
    },
    // priority is NOT user-sortable (PO R2 — severity order is server default)
    { title: '우선순위', key: 'priority', width: 110, render: (_, r) => <PriorityBadge priority={r.priority} /> },
    { title: '상태', key: 'status', width: 90, render: (_, r) => <StatusBadge status={r.status} /> },
    { title: '담당자', dataIndex: 'assigneeName', width: 120, render: (v) => v ?? '-' },
    {
      title: '접수일',
      dataIndex: 'createdAt',
      width: 160,
      sorter: true,
      render: (v: string) => dayjs(v).format('YYYY-MM-DD HH:mm'),
    },
  ];

  function catOptions(q: ReturnType<typeof useCategories>) {
    return (q.data ?? []).map((c) => ({ label: c.name, value: c.id }));
  }

  return (
    <Card>
      <Title level={3} style={{ marginTop: 0 }}>
        이슈 리스트
      </Title>

      <Space wrap style={{ marginBottom: 16 }}>
        <Select
          mode="multiple"
          allowClear
          placeholder="상태"
          style={{ minWidth: 160 }}
          options={STATUS_OPTIONS}
          onChange={(v: IssueStatus[]) => patch({ status: v.length ? v : undefined })}
        />
        <Select
          mode="multiple"
          allowClear
          placeholder="대분류"
          style={{ minWidth: 140 }}
          options={catOptions(l1)}
          onChange={(v: number[]) => patch({ categoryL1Id: v.length ? v : undefined })}
        />
        <Select
          mode="multiple"
          allowClear
          placeholder="중분류"
          style={{ minWidth: 140 }}
          options={catOptions(l2)}
          onChange={(v: number[]) => patch({ categoryL2Id: v.length ? v : undefined })}
        />
        <Select
          mode="multiple"
          allowClear
          placeholder="소분류"
          style={{ minWidth: 140 }}
          options={catOptions(l3)}
          onChange={(v: number[]) => patch({ categoryL3Id: v.length ? v : undefined })}
        />
        <Select
          allowClear
          placeholder="담당자"
          style={{ minWidth: 140 }}
          loading={users.isLoading}
          options={(users.data ?? []).map((u) => ({ label: u.displayName, value: u.id }))}
          onChange={(v?: number) => patch({ assigneeId: v })}
        />
        <RangePicker
          onChange={(range) =>
            patch({
              from: range?.[0] ? range[0].format('YYYY-MM-DD') : undefined,
              to: range?.[1] ? range[1].format('YYYY-MM-DD') : undefined,
            })
          }
        />
        <Input.Search
          allowClear
          placeholder="제목/내용/전화번호 검색"
          style={{ width: 240 }}
          value={searchText}
          onChange={(e) => setSearchText(e.target.value)}
        />
        {isAdmin && (
          <>
            <Button
              icon={<DownloadOutlined />}
              loading={exporting === 'plain'}
              disabled={exporting !== null}
              onClick={() => handleExport(false)}
              aria-label="이슈 CSV 내보내기"
            >
              CSV 내보내기
            </Button>
            <Popconfirm
              title="개인정보 포함 내보내기"
              description="발신자명/전화번호가 포함된 CSV가 다운로드됩니다. 계속하시겠습니까?"
              okText="계속"
              cancelText="취소"
              onConfirm={() => handleExport(true)}
            >
              <Button
                danger
                icon={<DownloadOutlined />}
                loading={exporting === 'pii'}
                disabled={exporting !== null}
                aria-label="이슈 CSV 내보내기 - 개인정보 포함"
              >
                CSV 내보내기 (PII 포함)
              </Button>
            </Popconfirm>
          </>
        )}
      </Space>

      <Table<IssueSummary>
        rowKey="id"
        columns={columns}
        dataSource={data?.content ?? []}
        loading={isFetching}
        size="middle"
        onRow={(record) => ({ onClick: () => navigate(`/issues/${record.id}`) })}
        pagination={{
          current: (params.page ?? 0) + 1,
          pageSize: params.size ?? PAGE_SIZE,
          total: data?.totalElements ?? 0,
          showSizeChanger: false,
        }}
        onChange={(pagination, _filters, sorter) => {
          const s = sorter as SorterResult<IssueSummary>;
          const sortParam =
            s && s.order && s.field
              ? `${String(s.field)},${s.order === 'ascend' ? 'asc' : 'desc'}`
              : undefined;
          setParams((p) => ({
            ...p,
            page: (pagination.current ?? 1) - 1,
            sort: sortParam,
          }));
        }}
      />
    </Card>
  );
}

/**
 * Pulls the backend's `{ code, message }` out of an error response. Because the export call
 * uses {@code responseType: 'blob'}, axios hands us a Blob body even on 4xx — we read and
 * parse it here so the user sees the Korean message from EXPORT_TOO_MANY_ROWS.
 */
async function extractErrorMessage(err: unknown): Promise<string> {
  const fallback = '내보내기에 실패했습니다.';
  if (typeof err !== 'object' || err === null) return fallback;
  const response = (err as { response?: { data?: unknown } }).response;
  const data = response?.data;
  if (data instanceof Blob) {
    try {
      const text = await data.text();
      const parsed = JSON.parse(text) as { message?: string };
      if (parsed.message) return parsed.message;
    } catch {
      // fall through to fallback
    }
  } else if (data && typeof data === 'object' && 'message' in data) {
    const msg = (data as { message?: unknown }).message;
    if (typeof msg === 'string') return msg;
  }
  return fallback;
}
