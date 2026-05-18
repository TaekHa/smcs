import { useState } from 'react';
import { Alert, Button, Card, Input, Radio, Space, Typography } from 'antd';
import { useForm, Controller } from 'react-hook-form';
import { zodResolver } from '@hookform/resolvers/zod';
import { z } from 'zod';
import { useNavigate } from 'react-router-dom';
import { createIssue } from '../../api/issues';
import { CategoryPicker } from '../../shared/components/CategoryPicker';
import { PriorityBadge } from '../../shared/components/PriorityBadge';
import type { ApiError } from '../../types/auth';
import type { Priority } from '../../types/issue';

const { Title } = Typography;

const PRIORITIES: Priority[] = ['URGENT', 'HIGH', 'NORMAL', 'LOW'];

const schema = z.object({
  title: z.string().min(1, '제목을 입력하세요.').max(200, '제목은 200자 이하여야 합니다.'),
  callerName: z.string().min(1, '발신자명을 입력하세요.'),
  callerPhone: z.string().min(1, '발신자 전화번호를 입력하세요.'),
  categoryL1Id: z.number({ required_error: '대분류를 선택하세요.', invalid_type_error: '대분류를 선택하세요.' }),
  categoryL2Id: z.number({ required_error: '중분류를 선택하세요.', invalid_type_error: '중분류를 선택하세요.' }),
  categoryL3Id: z.number({ required_error: '소분류를 선택하세요.', invalid_type_error: '소분류를 선택하세요.' }),
  priority: z.enum(['URGENT', 'HIGH', 'NORMAL', 'LOW']),
  description: z.string().min(1, '상세 내용을 입력하세요.'),
});

type FormValues = z.infer<typeof schema>;

interface ErrorState {
  message: string;
  traceId: string | null;
}

function mapError(err: unknown): ErrorState {
  const anyErr = err as { response?: { status?: number; data?: ApiError } };
  const status = anyErr.response?.status;
  const data = anyErr.response?.data;
  const traceId = data?.traceId ?? null;
  if (!status) {
    return { message: '서버에 연결할 수 없습니다.', traceId };
  }
  if (data?.code === 'VALIDATION_FAILED') {
    return { message: data.message ?? '입력값을 확인하세요.', traceId };
  }
  if (status === 403) {
    return { message: '이슈를 등록할 권한이 없습니다.', traceId };
  }
  return { message: data?.message ?? '이슈 등록에 실패했습니다.', traceId };
}

function FieldError({ message }: { message?: string }) {
  if (!message) return null;
  return (
    <div role="alert" style={{ color: '#ff4d4f', fontSize: 12, marginTop: 4 }}>
      {message}
    </div>
  );
}

export function IssueFormView() {
  const navigate = useNavigate();
  const [error, setError] = useState<ErrorState | null>(null);
  const {
    control,
    handleSubmit,
    watch,
    setValue,
    formState: { errors, isSubmitting },
  } = useForm<FormValues>({
    resolver: zodResolver(schema),
    defaultValues: {
      title: '',
      callerName: '',
      callerPhone: '',
      description: '',
      priority: 'NORMAL',
    },
  });

  async function onSubmit(values: FormValues) {
    setError(null);
    try {
      const res = await createIssue(values);
      navigate(`/issues/${res.id}`);
    } catch (err) {
      setError(mapError(err));
    }
  }

  function onKeyDown(e: React.KeyboardEvent) {
    if ((e.ctrlKey || e.metaKey) && e.key.toLowerCase() === 's') {
      e.preventDefault();
      void handleSubmit(onSubmit)();
    } else if (e.key === 'Escape') {
      e.preventDefault();
      navigate('/issues');
    }
  }

  return (
    <form onSubmit={handleSubmit(onSubmit)} onKeyDown={onKeyDown} aria-label="이슈 등록 폼">
      <Card style={{ maxWidth: 720, margin: '0 auto' }}>
        <Title level={3} style={{ marginTop: 0 }}>
          신규 이슈 등록
        </Title>

        {error && (
          <div role="alert" style={{ marginBottom: 16 }}>
            <Alert
              type="error"
              showIcon
              message={error.message}
              description={
                error.traceId ? (
                  <span style={{ fontSize: 11, opacity: 0.7 }}>traceId: {error.traceId}</span>
                ) : undefined
              }
            />
          </div>
        )}

        <div style={{ marginBottom: 16 }}>
          <label htmlFor="title">제목 *</label>
          <Controller
            name="title"
            control={control}
            render={({ field }) => <Input {...field} id="title" maxLength={200} />}
          />
          <FieldError message={errors.title?.message} />
        </div>

        <div style={{ marginBottom: 16 }}>
          <label htmlFor="callerName">발신자명 *</label>
          <Controller
            name="callerName"
            control={control}
            render={({ field }) => <Input {...field} id="callerName" />}
          />
          <FieldError message={errors.callerName?.message} />
        </div>

        <div style={{ marginBottom: 16 }}>
          <label htmlFor="callerPhone">발신자 전화번호 *</label>
          <Controller
            name="callerPhone"
            control={control}
            render={({ field }) => (
              <Input {...field} id="callerPhone" inputMode="tel" placeholder="010-1234-5678" />
            )}
          />
          <FieldError message={errors.callerPhone?.message} />
        </div>

        <div style={{ marginBottom: 16 }}>
          <span id="category-label">카테고리 (대/중/소분류) *</span>
          <div aria-labelledby="category-label" style={{ marginTop: 4 }}>
            <CategoryPicker
              value={{
                l1: watch('categoryL1Id'),
                l2: watch('categoryL2Id'),
                l3: watch('categoryL3Id'),
              }}
              onChange={(v) => {
                setValue('categoryL1Id', v.l1 as number, { shouldValidate: true });
                setValue('categoryL2Id', v.l2 as number, { shouldValidate: true });
                setValue('categoryL3Id', v.l3 as number, { shouldValidate: true });
              }}
            />
          </div>
          <FieldError
            message={
              errors.categoryL1Id?.message ??
              errors.categoryL2Id?.message ??
              errors.categoryL3Id?.message
            }
          />
        </div>

        <div style={{ marginBottom: 16 }}>
          <span id="priority-label">우선순위 *</span>
          <div style={{ marginTop: 4 }}>
            <Controller
              name="priority"
              control={control}
              render={({ field }) => (
                <Radio.Group
                  aria-labelledby="priority-label"
                  value={field.value}
                  onChange={(e) => field.onChange(e.target.value)}
                >
                  {PRIORITIES.map((p) => (
                    <Radio.Button key={p} value={p}>
                      <PriorityBadge priority={p} />
                    </Radio.Button>
                  ))}
                </Radio.Group>
              )}
            />
          </div>
          <FieldError message={errors.priority?.message} />
        </div>

        <div style={{ marginBottom: 24 }}>
          <label htmlFor="description">상세 내용 *</label>
          <Controller
            name="description"
            control={control}
            render={({ field }) => (
              <Input.TextArea {...field} id="description" rows={4} />
            )}
          />
          <FieldError message={errors.description?.message} />
        </div>

        <Space>
          <Button type="primary" htmlType="submit" loading={isSubmitting}>
            저장 (Ctrl+S)
          </Button>
          <Button onClick={() => navigate('/issues')}>취소 (Esc)</Button>
        </Space>
      </Card>
    </form>
  );
}
