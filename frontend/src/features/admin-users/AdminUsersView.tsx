import { useState } from 'react';
import {
  Alert,
  Button,
  Card,
  Empty,
  Form,
  Input,
  Modal,
  Popconfirm,
  Radio,
  Space,
  Spin,
  Switch,
  Table,
  Tag,
  Tooltip,
  Typography,
  message,
} from 'antd';
import type { ColumnsType } from 'antd/es/table';
import { EditOutlined, PlusOutlined, CopyOutlined } from '@ant-design/icons';
import dayjs from 'dayjs';
import {
  useAdminUsers,
  useCreateAdminUser,
  useUpdateAdminUser,
} from '../../shared/hooks/useAdminUsers';
import { useAuth } from '../../auth/useAuthStore';
import type { Role } from '../../types/auth';
import type {
  UserAdminItem,
  UserCreateRequest,
  UserCreateResponse,
  UserUpdateRequest,
} from '../../types/admin-user';

const { Title } = Typography;

const ROLE_TAG_COLOR: Record<Role, string> = {
  AGENT: 'blue',
  FIELD: 'green',
  ADMIN: 'purple',
};

export function AdminUsersView() {
  const { data, isFetching, isError } = useAdminUsers();
  const create = useCreateAdminUser();
  const update = useUpdateAdminUser();
  const currentUser = useAuth();
  const [creating, setCreating] = useState(false);
  const [editing, setEditing] = useState<UserAdminItem | null>(null);
  const [tempPasswordResult, setTempPasswordResult] = useState<UserCreateResponse | null>(null);

  async function handleToggleActive(row: UserAdminItem, nextActive: boolean) {
    try {
      await update.mutateAsync({ id: row.id, req: { active: nextActive } });
      message.success(nextActive ? '활성화했습니다.' : '비활성화했습니다.');
    } catch {
      message.error('변경에 실패했습니다.');
    }
  }

  const columns: ColumnsType<UserAdminItem> = [
    { title: '사용자명', dataIndex: 'username', key: 'username', width: 140 },
    { title: '표시 이름', dataIndex: 'displayName', key: 'displayName', width: 180 },
    {
      title: '역할',
      dataIndex: 'role',
      key: 'role',
      width: 100,
      render: (role: Role) => <Tag color={ROLE_TAG_COLOR[role]}>{role}</Tag>,
    },
    { title: '전화번호', dataIndex: 'phone', key: 'phone', width: 140, render: (v) => v ?? '-' },
    {
      title: '활성',
      dataIndex: 'active',
      key: 'active',
      width: 120,
      render: (active: boolean, row) => {
        // AC5 — frontend mirror of the backend guard. ADMIN may not deactivate self.
        const isSelf = currentUser?.id === row.id;
        const control = (
          <Switch
            checked={active}
            disabled={isSelf}
            aria-label={`사용자 ${row.username} 활성화 토글`}
          />
        );
        if (isSelf) {
          return <Tooltip title="본인 계정은 비활성화할 수 없습니다">{control}</Tooltip>;
        }
        return (
          <Popconfirm
            title={active ? '비활성화하시겠습니까?' : '활성화하시겠습니까?'}
            description={
              active
                ? '비활성화 시 해당 사용자는 로그인할 수 없습니다. 기존 데이터는 유지됩니다.'
                : '활성화 시 해당 사용자가 다시 로그인할 수 있습니다.'
            }
            okText="계속"
            cancelText="취소"
            onConfirm={() => handleToggleActive(row, !active)}
          >
            {control}
          </Popconfirm>
        );
      },
    },
    {
      title: '생성일',
      dataIndex: 'createdAt',
      key: 'createdAt',
      width: 160,
      render: (v: string) => dayjs(v).format('YYYY-MM-DD HH:mm'),
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
          aria-label={`사용자 ${row.username} 수정`}
        />
      ),
    },
  ];

  return (
    <Card>
      <Title level={3} style={{ marginTop: 0 }}>
        사용자 관리
      </Title>

      <Space style={{ marginBottom: 16 }}>
        <Button
          type="primary"
          icon={<PlusOutlined />}
          onClick={() => setCreating(true)}
          aria-label="사용자 추가"
        >
          사용자 추가
        </Button>
      </Space>

      {isError && (
        <div role="alert" style={{ marginBottom: 16 }}>
          <Alert type="error" showIcon message="사용자 목록을 불러오지 못했습니다." />
        </div>
      )}

      {isFetching && !data ? (
        <div style={{ textAlign: 'center', padding: 40 }}>
          <Spin />
        </div>
      ) : (data ?? []).length === 0 ? (
        <Empty description="사용자가 없습니다 — 시드 데이터 확인 필요" />
      ) : (
        <Table<UserAdminItem>
          rowKey="id"
          dataSource={data ?? []}
          columns={columns}
          loading={isFetching || update.isPending}
          pagination={false}
          size="middle"
        />
      )}

      {creating && (
        <UserCreateModal
          onCancel={() => setCreating(false)}
          onSubmit={async (values) => {
            const result = await create.mutateAsync(values);
            setCreating(false);
            setTempPasswordResult(result);
          }}
        />
      )}

      {editing && (
        <UserEditModal
          initial={editing}
          onCancel={() => setEditing(null)}
          onSubmit={async (values) => {
            await update.mutateAsync({ id: editing.id, req: values });
            message.success('사용자를 수정했습니다.');
            setEditing(null);
          }}
        />
      )}

      {tempPasswordResult && (
        <TemporaryPasswordModal
          result={tempPasswordResult}
          onClose={() => setTempPasswordResult(null)}
        />
      )}
    </Card>
  );
}

interface UserCreateModalProps {
  onCancel: () => void;
  onSubmit: (values: UserCreateRequest) => Promise<void>;
}

function UserCreateModal({ onCancel, onSubmit }: UserCreateModalProps) {
  const [form] = Form.useForm<UserCreateRequest>();
  const [submitting, setSubmitting] = useState(false);

  async function handleOk() {
    const values = await form.validateFields();
    setSubmitting(true);
    try {
      await onSubmit({
        username: values.username.trim(),
        displayName: values.displayName.trim(),
        role: values.role,
        phone: values.phone?.trim() || undefined,
      });
    } catch {
      message.error('사용자 추가에 실패했습니다.');
    } finally {
      setSubmitting(false);
    }
  }

  return (
    <Modal
      open
      title="사용자 추가"
      okText="저장"
      cancelText="취소"
      onCancel={onCancel}
      onOk={handleOk}
      confirmLoading={submitting}
    >
      <Form form={form} layout="vertical" initialValues={{ role: 'AGENT' as Role }}>
        <Form.Item
          label="사용자명"
          name="username"
          rules={[
            { required: true, message: '사용자명을 입력하세요.' },
            { min: 3, message: '3자 이상 입력하세요.' },
            { max: 50, message: '50자 이내로 입력하세요.' },
          ]}
        >
          <Input maxLength={50} />
        </Form.Item>
        <Form.Item
          label="표시 이름"
          name="displayName"
          rules={[
            { required: true, message: '표시 이름을 입력하세요.' },
            { max: 50, message: '50자 이내로 입력하세요.' },
          ]}
        >
          <Input maxLength={50} />
        </Form.Item>
        <Form.Item
          label="역할"
          name="role"
          rules={[{ required: true, message: '역할을 선택하세요.' }]}
        >
          <Radio.Group>
            <Radio value="AGENT">AGENT</Radio>
            <Radio value="FIELD">FIELD</Radio>
            <Radio value="ADMIN">ADMIN</Radio>
          </Radio.Group>
        </Form.Item>
        <Form.Item label="전화번호 (선택)" name="phone">
          <Input maxLength={100} inputMode="tel" placeholder="010-1234-5678" />
        </Form.Item>
      </Form>
    </Modal>
  );
}

interface UserEditModalProps {
  initial: UserAdminItem;
  onCancel: () => void;
  onSubmit: (values: UserUpdateRequest) => Promise<void>;
}

function UserEditModal({ initial, onCancel, onSubmit }: UserEditModalProps) {
  const [form] = Form.useForm<{ displayName: string; role: Role; phone?: string }>();
  const [submitting, setSubmitting] = useState(false);

  async function handleOk() {
    const values = await form.validateFields();
    setSubmitting(true);
    try {
      await onSubmit({
        displayName: values.displayName.trim(),
        role: values.role,
        phone: values.phone?.trim() || undefined,
      });
    } catch {
      message.error('수정에 실패했습니다.');
    } finally {
      setSubmitting(false);
    }
  }

  return (
    <Modal
      open
      title={`사용자 수정 — ${initial.username}`}
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
          displayName: initial.displayName,
          role: initial.role,
          phone: initial.phone ?? '',
        }}
      >
        <Form.Item label="사용자명">
          <Input value={initial.username} disabled />
        </Form.Item>
        <Form.Item
          label="표시 이름"
          name="displayName"
          rules={[
            { required: true, message: '표시 이름을 입력하세요.' },
            { max: 50, message: '50자 이내로 입력하세요.' },
          ]}
        >
          <Input maxLength={50} />
        </Form.Item>
        <Form.Item label="역할" name="role" rules={[{ required: true }]}>
          <Radio.Group>
            <Radio value="AGENT">AGENT</Radio>
            <Radio value="FIELD">FIELD</Radio>
            <Radio value="ADMIN">ADMIN</Radio>
          </Radio.Group>
        </Form.Item>
        <Form.Item label="전화번호 (선택)" name="phone">
          <Input maxLength={100} inputMode="tel" />
        </Form.Item>
      </Form>
    </Modal>
  );
}

interface TemporaryPasswordModalProps {
  result: UserCreateResponse;
  onClose: () => void;
}

function TemporaryPasswordModal({ result, onClose }: TemporaryPasswordModalProps) {
  async function handleCopy() {
    try {
      await navigator.clipboard.writeText(result.temporaryPassword);
      message.success('임시 비밀번호를 복사했습니다.');
    } catch {
      message.error('복사에 실패했습니다.');
    }
  }

  return (
    <Modal
      open
      closable={false}
      maskClosable={false}
      keyboard={false}
      title={`임시 비밀번호 — ${result.user.username}`}
      footer={[
        <Button key="close" type="primary" onClick={onClose}>
          확인 (저장했습니다)
        </Button>,
      ]}
    >
      <Alert
        type="warning"
        showIcon
        message="이 비밀번호는 다시 표시되지 않습니다."
        description="사용자에게 안전한 채널로 전달하세요. 분실 시 기존 계정을 비활성화하고 다른 사용자명으로 새 계정을 생성해야 합니다 (AC8)."
        style={{ marginBottom: 16 }}
      />
      <div
        role="region"
        aria-live="polite"
        aria-label="임시 비밀번호"
        style={{
          background: '#fafafa',
          border: '1px solid #d9d9d9',
          padding: 12,
          fontFamily: 'monospace',
          fontSize: 18,
          letterSpacing: 1,
          display: 'flex',
          alignItems: 'center',
          justifyContent: 'space-between',
        }}
      >
        <span data-testid="temp-password">{result.temporaryPassword}</span>
        <Button icon={<CopyOutlined />} onClick={handleCopy}>
          복사
        </Button>
      </div>
    </Modal>
  );
}
