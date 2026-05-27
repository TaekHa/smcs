import { useRef, useState } from 'react';
import { Button, Divider, Empty, Input, Progress, Space, Spin, Typography, message } from 'antd';
import { isAxiosError } from 'axios';
import dayjs from 'dayjs';
import { useParams } from 'react-router-dom';
import {
  useAddComment,
  useIssue,
  useTransitionIssue,
  useUploadAttachment,
} from '../../shared/hooks/useIssue';
import { PriorityBadge } from '../../shared/components/PriorityBadge';
import { StatusBadge } from '../../shared/components/StatusBadge';
import { AuthImage } from '../../shared/components/AuthImage';
import { ForbiddenView } from '../error/ForbiddenView';

const { Title, Paragraph, Text } = Typography;

const MAX_SIZE = 10 * 1024 * 1024;
const MAX_COUNT = 10;

/** Field worker mobile detail (Story 2.6): summary + photo gallery/upload + action + 완료 처리. */
export function MobileFieldDetailView() {
  const { id } = useParams();
  const issueId = Number(id);
  const { data: issue, isLoading, isError, error } = useIssue(issueId);
  const addComment = useAddComment(issueId);
  const transition = useTransitionIssue(issueId);
  const upload = useUploadAttachment(issueId);
  const [action, setAction] = useState('');
  const [progress, setProgress] = useState<number | null>(null);
  const fileRef = useRef<HTMLInputElement>(null);

  // AC: non-assigned FIELD gets backend 403 → Forbidden.
  if (isError && isAxiosError(error) && error.response?.status === 403) {
    return <ForbiddenView />;
  }
  if (isLoading || !issue) {
    return (
      <div style={{ display: 'flex', justifyContent: 'center', padding: 48 }}>
        <Spin size="large" />
      </div>
    );
  }

  const done = issue.status === 'DONE' || issue.status === 'VERIFIED';
  const completing = addComment.isPending || transition.isPending;

  function onFileSelected(e: React.ChangeEvent<HTMLInputElement>) {
    const file = e.target.files?.[0];
    e.target.value = '';
    if (!file || !issue) return;
    // AC7 client-side validation
    if (!file.type.startsWith('image/')) {
      message.error('이미지 파일만 업로드할 수 있습니다');
      return;
    }
    if (file.size > MAX_SIZE) {
      message.error('이미지는 10MB 이하만 가능합니다');
      return;
    }
    if (issue.attachments.length >= MAX_COUNT) {
      message.error('이슈당 사진은 최대 10장입니다');
      return;
    }
    setProgress(0);
    upload.mutate(
      {
        file,
        onProgress: (ev) => setProgress(ev.total ? Math.round((ev.loaded / ev.total) * 100) : 0),
      },
      {
        onSettled: () => setProgress(null),
        onError: () => message.error('업로드에 실패했습니다 — 다시 시도하세요'),
      }
    );
  }

  function complete() {
    if (!issue) return;
    const body = action.trim();
    if (!body) return;
    // SW-001 (P1): backend VALID_TRANSITIONS = {ASSIGNED→IN_PROGRESS, IN_PROGRESS→DONE} only;
    // mobile has no explicit "시작" button, so chain IN_PROGRESS before DONE when status is ASSIGNED.
    const needStart = issue.status === 'ASSIGNED';
    addComment.mutate(
      { body, kind: 'FIELD_ACTION' },
      {
        onSuccess: () => {
          if (needStart) {
            transition.mutate(
              { to: 'IN_PROGRESS' },
              {
                onSuccess: () =>
                  transition.mutate({ to: 'DONE' }, { onSuccess: () => setAction('') }),
              }
            );
          } else {
            transition.mutate({ to: 'DONE' }, { onSuccess: () => setAction('') });
          }
        },
      }
    );
  }

  return (
    <div style={{ maxWidth: 480, margin: '0 auto', padding: 16 }}>
      <StatusBadge status={issue.status} showProgress />
      <Title level={4} style={{ marginTop: 12 }}>
        #{issue.id} {issue.title}
      </Title>
      <Space size={8} wrap style={{ marginBottom: 8 }}>
        <PriorityBadge priority={issue.priority} />
        <StatusBadge status={issue.status} />
        <Text type="secondary" style={{ fontSize: 12 }}>
          {issue.categoryL1.name} &gt; {issue.categoryL2.name} &gt; {issue.categoryL3.name}
        </Text>
      </Space>
      <Paragraph style={{ whiteSpace: 'pre-wrap' }}>{issue.description}</Paragraph>

      <Divider />

      {/* AC1/AC2/AC3: photo gallery + add */}
      <Title level={5}>사진</Title>
      {issue.attachments.length === 0 ? (
        <Empty description="첨부된 사진이 없습니다" />
      ) : (
        <Space wrap>
          {issue.attachments.map((a) => (
            <AuthImage key={a.id} src={a.url} alt={a.originalName} />
          ))}
        </Space>
      )}
      <input
        ref={fileRef}
        type="file"
        accept="image/*"
        capture="environment"
        style={{ display: 'none' }}
        onChange={onFileSelected}
      />
      <div style={{ marginTop: 8 }}>
        <Button
          onClick={() => fileRef.current?.click()}
          loading={upload.isPending}
          disabled={done || issue.attachments.length >= MAX_COUNT}
          style={{ minHeight: 44 }}
          block
        >
          사진 추가
        </Button>
        {progress !== null && <Progress percent={progress} size="small" style={{ marginTop: 8 }} />}
      </div>

      <Divider />

      {/* AC4/AC5/AC6: action + 완료 처리 */}
      <Title level={5}>조치 내용</Title>
      {done ? (
        <Text type="secondary">완료 처리된 이슈입니다 ({dayjs(issue.resolvedAt).format('YYYY-MM-DD HH:mm')}).</Text>
      ) : (
        <>
          <Input.TextArea
            value={action}
            onChange={(e) => setAction(e.target.value)}
            placeholder="조치 내용을 입력하세요"
            autoSize={{ minRows: 3, maxRows: 8 }}
            maxLength={4000}
          />
          <Button
            type="primary"
            block
            onClick={complete}
            loading={completing}
            disabled={!action.trim()}
            style={{ minHeight: 44, marginTop: 8 }}
          >
            완료 처리
          </Button>
        </>
      )}
    </div>
  );
}
