import { useMemo, useState } from 'react';
import {
  Button,
  Card,
  Empty,
  Form,
  Input,
  InputNumber,
  Modal,
  Popconfirm,
  Select,
  Space,
  Spin,
  Switch,
  Table,
  Tabs,
  Tag,
  Typography,
  message,
} from 'antd';
import type { ColumnsType } from 'antd/es/table';
import {
  ArrowDownOutlined,
  ArrowUpOutlined,
  EditOutlined,
  PlusOutlined,
} from '@ant-design/icons';
import { useAdminCategories, useUpsertCategory } from '../../shared/hooks/useAdminCategories';
import type {
  CategoryAdminItem,
  CategoryLevel,
  CategoryUpsertRequest,
} from '../../types/category';

const { Title } = Typography;

const LEVEL_TABS: { key: string; label: string; level: CategoryLevel }[] = [
  { key: '1', label: '대분류 (L1)', level: 1 },
  { key: '2', label: '중분류 (L2)', level: 2 },
  { key: '3', label: '소분류 (L3)', level: 3 },
];

export function AdminCategoriesView() {
  const [activeKey, setActiveKey] = useState<string>('1');

  return (
    <Card>
      <Title level={3} style={{ marginTop: 0 }}>
        카테고리 관리
      </Title>
      <Tabs
        activeKey={activeKey}
        onChange={setActiveKey}
        items={LEVEL_TABS.map((t) => ({
          key: t.key,
          label: t.label,
          children: <LevelPanel level={t.level} />,
        }))}
      />
    </Card>
  );
}

function LevelPanel({ level }: { level: CategoryLevel }) {
  const { data, isFetching, isError } = useAdminCategories(level);
  const upsert = useUpsertCategory();
  const [editing, setEditing] = useState<CategoryAdminItem | null>(null);
  const [creating, setCreating] = useState(false);

  const rows = useMemo(() => data ?? [], [data]);

  async function handleToggleActive(row: CategoryAdminItem, nextActive: boolean) {
    try {
      await upsert.mutateAsync({
        id: row.id,
        level: row.level,
        name: row.name,
        keywords: row.keywords,
        sortOrder: row.sortOrder,
        active: nextActive,
      });
      message.success(nextActive ? '활성화했습니다.' : '비활성화했습니다.');
    } catch {
      message.error('변경에 실패했습니다.');
    }
  }

  async function handleSwap(row: CategoryAdminItem, neighbor: CategoryAdminItem) {
    // sortOrder swap (Deviation #4 — ↑/↓ inline adjacent reorder).
    try {
      await upsert.mutateAsync({
        id: row.id,
        level: row.level,
        name: row.name,
        keywords: row.keywords,
        sortOrder: neighbor.sortOrder,
        active: row.active,
      });
      await upsert.mutateAsync({
        id: neighbor.id,
        level: neighbor.level,
        name: neighbor.name,
        keywords: neighbor.keywords,
        sortOrder: row.sortOrder,
        active: neighbor.active,
      });
    } catch {
      message.error('순서 변경에 실패했습니다.');
    }
  }

  if (isError) {
    return <div role="alert">카테고리를 불러오지 못했습니다.</div>;
  }

  const columns: ColumnsType<CategoryAdminItem> = [
    { title: '이름', dataIndex: 'name', key: 'name', width: 200 },
    {
      title: '정렬',
      dataIndex: 'sortOrder',
      key: 'sortOrder',
      width: 80,
      render: (v: number) => v ?? '-',
    },
    {
      title: '키워드',
      dataIndex: 'keywords',
      key: 'keywords',
      render: (kws: string[]) =>
        kws && kws.length > 0 ? (
          <Space wrap size={4}>
            {kws.map((k) => (
              <Tag key={k}>{k}</Tag>
            ))}
          </Space>
        ) : (
          <span style={{ color: '#999' }}>-</span>
        ),
    },
    {
      title: '활성',
      dataIndex: 'active',
      key: 'active',
      width: 100,
      render: (active: boolean, row) => (
        <Popconfirm
          title={active ? '비활성화하시겠습니까?' : '활성화하시겠습니까?'}
          description={
            active
              ? '비활성화 시 신규 이슈 등록 폼 드롭다운에서 숨겨집니다. 과거 이슈는 영향받지 않습니다.'
              : '활성화 시 신규 이슈 등록 폼 드롭다운에 다시 표시됩니다.'
          }
          okText="계속"
          cancelText="취소"
          onConfirm={() => handleToggleActive(row, !active)}
        >
          <Switch checked={active} aria-label={`카테고리 ${row.name} 활성화 토글`} />
        </Popconfirm>
      ),
    },
    {
      title: '순서 변경',
      key: 'reorder',
      width: 100,
      render: (_, row, index) => (
        <Space>
          <Button
            type="text"
            icon={<ArrowUpOutlined />}
            disabled={index === 0 || upsert.isPending}
            onClick={() => handleSwap(row, rows[index - 1])}
            aria-label={`${row.name} 위로 이동`}
          />
          <Button
            type="text"
            icon={<ArrowDownOutlined />}
            disabled={index === rows.length - 1 || upsert.isPending}
            onClick={() => handleSwap(row, rows[index + 1])}
            aria-label={`${row.name} 아래로 이동`}
          />
        </Space>
      ),
    },
    {
      title: '수정',
      key: 'edit',
      width: 80,
      render: (_, row) => (
        <Button
          type="text"
          icon={<EditOutlined />}
          onClick={() => setEditing(row)}
          aria-label={`카테고리 ${row.name} 수정`}
        />
      ),
    },
  ];

  return (
    <>
      <Space style={{ marginBottom: 16 }}>
        <Button
          type="primary"
          icon={<PlusOutlined />}
          onClick={() => setCreating(true)}
          aria-label={`L${level} 카테고리 추가`}
        >
          카테고리 추가
        </Button>
      </Space>

      {isFetching && rows.length === 0 ? (
        <div style={{ textAlign: 'center', padding: 40 }}>
          <Spin />
        </div>
      ) : rows.length === 0 ? (
        <Empty description="카테고리가 없습니다 — 시드 마이그레이션 확인 필요" />
      ) : (
        <Table<CategoryAdminItem>
          rowKey="id"
          dataSource={rows}
          columns={columns}
          loading={isFetching || upsert.isPending}
          pagination={false}
          size="middle"
        />
      )}

      {creating && (
        <CategoryFormModal
          mode="create"
          level={level}
          onCancel={() => setCreating(false)}
          onSubmit={async (values) => {
            await upsert.mutateAsync(values);
            message.success('카테고리를 추가했습니다.');
            setCreating(false);
          }}
        />
      )}

      {editing && (
        <CategoryFormModal
          mode="edit"
          level={level}
          initial={editing}
          onCancel={() => setEditing(null)}
          onSubmit={async (values) => {
            await upsert.mutateAsync(values);
            message.success('카테고리를 수정했습니다.');
            setEditing(null);
          }}
        />
      )}
    </>
  );
}

interface CategoryFormModalProps {
  mode: 'create' | 'edit';
  level: CategoryLevel;
  initial?: CategoryAdminItem;
  onCancel: () => void;
  onSubmit: (values: CategoryUpsertRequest) => Promise<void>;
}

function CategoryFormModal({ mode, level, initial, onCancel, onSubmit }: CategoryFormModalProps) {
  const [form] = Form.useForm<{
    name: string;
    keywords: string[];
    sortOrder?: number;
  }>();
  const [submitting, setSubmitting] = useState(false);

  async function handleOk() {
    const values = await form.validateFields();
    setSubmitting(true);
    try {
      await onSubmit({
        id: initial?.id ?? null,
        level,
        name: values.name.trim(),
        keywords: values.keywords ?? [],
        sortOrder: values.sortOrder ?? null,
        active: initial?.active ?? true,
      });
    } catch {
      message.error('저장에 실패했습니다.');
    } finally {
      setSubmitting(false);
    }
  }

  return (
    <Modal
      open
      title={mode === 'create' ? '카테고리 추가' : '카테고리 수정'}
      okText="저장"
      cancelText="취소"
      onCancel={onCancel}
      onOk={handleOk}
      confirmLoading={submitting}
    >
      <Form
        form={form}
        layout="vertical"
        initialValues={{
          name: initial?.name ?? '',
          keywords: initial?.keywords ?? [],
          sortOrder: initial?.sortOrder,
        }}
      >
        <Form.Item
          label="이름"
          name="name"
          rules={[
            { required: true, message: '이름은 필수입니다.' },
            { max: 100, message: '100자 이내로 입력해주세요.' },
          ]}
        >
          <Input maxLength={100} />
        </Form.Item>
        <Form.Item
          label="정렬 순서"
          name="sortOrder"
          extra="비워두면 자동으로 가장 뒤로 추가됩니다."
        >
          <InputNumber min={0} style={{ width: '100%' }} />
        </Form.Item>
        <Form.Item
          label="키워드 (자동 카테고리 제안 — Story 4.2)"
          name="keywords"
          extra="Enter 또는 쉼표(,)로 추가, x 로 제거."
        >
          <Select mode="tags" tokenSeparators={[',']} style={{ width: '100%' }} />
        </Form.Item>
      </Form>
    </Modal>
  );
}
