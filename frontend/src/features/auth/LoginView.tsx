import { useEffect, useRef, useState } from 'react';
import { Alert, Button, Card, Form, Input, Typography } from 'antd';
import type { InputRef } from 'antd';
import { useNavigate } from 'react-router-dom';
import { login as loginApi } from '../../api/auth';
import { useAuthStore } from '../../auth/useAuthStore';
import type { ApiError, LoginRequest } from '../../types/auth';

const { Title } = Typography;

interface ErrorState {
  message: string;
  traceId: string | null;
}

function mapError(err: unknown): ErrorState {
  const anyErr = err as { response?: { status?: number; data?: ApiError }; message?: string };
  const status = anyErr.response?.status;
  const data = anyErr.response?.data;
  const traceId = data?.traceId ?? null;

  if (!status) {
    return { message: '서버에 연결할 수 없습니다.', traceId };
  }
  if (data?.code === 'INVALID_CREDENTIALS' || (status === 401 && !data?.code)) {
    return { message: '사용자명 또는 비밀번호를 확인하세요.', traceId };
  }
  if (data?.code === 'ACCOUNT_LOCKED' || status === 423) {
    return {
      message: '로그인 실패가 누적되어 계정이 잠겼습니다. 10분 후 다시 시도하세요.',
      traceId,
    };
  }
  if (data?.code === 'RATE_LIMIT_EXCEEDED' || status === 429) {
    return { message: '요청이 너무 많습니다. 잠시 후 다시 시도하세요.', traceId };
  }
  return { message: data?.message ?? '로그인에 실패했습니다.', traceId };
}

export function LoginView() {
  const navigate = useNavigate();
  const usernameRef = useRef<InputRef>(null);
  const [error, setError] = useState<ErrorState | null>(null);
  const [submitting, setSubmitting] = useState(false);

  useEffect(() => {
    usernameRef.current?.focus();
  }, []);

  async function handleSubmit(values: LoginRequest) {
    setSubmitting(true);
    setError(null);
    try {
      const res = await loginApi(values);
      useAuthStore.getState().setSession(res.token, res.user);
      navigate('/', { replace: true });
    } catch (err) {
      setError(mapError(err));
    } finally {
      setSubmitting(false);
    }
  }

  return (
    <div
      style={{
        minHeight: '100vh',
        display: 'flex',
        alignItems: 'center',
        justifyContent: 'center',
        background: '#f0f2f5',
      }}
    >
      <Card style={{ width: 360 }}>
        <Title level={3} style={{ textAlign: 'center', marginBottom: 24 }}>
          SMCS 로그인
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

        <Form layout="vertical" onFinish={handleSubmit} requiredMark={false}>
          <Form.Item
            label="사용자명"
            name="username"
            rules={[{ required: true, message: '사용자명을 입력하세요.' }]}
          >
            <Input ref={usernameRef} autoComplete="username" />
          </Form.Item>
          <Form.Item
            label="비밀번호"
            name="password"
            rules={[{ required: true, message: '비밀번호를 입력하세요.' }]}
          >
            <Input.Password autoComplete="current-password" />
          </Form.Item>
          <Form.Item style={{ marginBottom: 0 }}>
            <Button type="primary" htmlType="submit" loading={submitting} block>
              로그인
            </Button>
          </Form.Item>
        </Form>
      </Card>
    </div>
  );
}
