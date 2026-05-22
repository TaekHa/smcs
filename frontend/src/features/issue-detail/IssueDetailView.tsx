import { useState } from 'react';
import {
  Button,
  Card,
  Descriptions,
  Divider,
  Empty,
  Image,
  Input,
  List,
  Space,
  Spin,
  Tag,
  Typography,
} from 'antd';
import { isAxiosError } from 'axios';
import dayjs from 'dayjs';
import { useParams } from 'react-router-dom';
import { useIssue, useIssueEvents, useAddComment } from '../../shared/hooks/useIssue';
import { PriorityBadge } from '../../shared/components/PriorityBadge';
import { StatusBadge } from '../../shared/components/StatusBadge';
import { ForbiddenView } from '../error/ForbiddenView';
import type { CommentKind, IssueEventType } from '../../types/issue';

const { Title, Paragraph, Text } = Typography;

const KIND_LABELS: Record<CommentKind, string> = {
  NOTE: '메모',
  FIELD_ACTION: '현장 조치',
  SYSTEM: '시스템',
};

const EVENT_LABELS: Record<IssueEventType, string> = {
  CREATED: '생성',
  STATUS_CHANGED: '상태 변경',
  ASSIGNED: '배정',
  COMMENTED: '코멘트',
  ATTACHMENT_ADDED: '첨부 추가',
  RESOLVED: '완료',
};

function fmt(value: string | null): string {
  return value ? dayjs(value).format('YYYY-MM-DD HH:mm') : '-';
}

export function IssueDetailView() {
  const { id } = useParams();
  const issueId = Number(id);
  const { data: issue, isLoading, isError, error } = useIssue(issueId);
  const events = useIssueEvents(issueId);
  const addComment = useAddComment(issueId);
  const [body, setBody] = useState('');

  // AC6: non-accessible users (FIELD on unassigned issue) get backend 403 → Forbidden.
  if (isError && isAxiosError(error) && error.response?.status === 403) {
    return <ForbiddenView />;
  }

  if (isLoading || !issue) {
    return (
      <div style={{ display: 'flex', minHeight: '40vh', alignItems: 'center', justifyContent: 'center' }}>
        <Spin size="large" />
      </div>
    );
  }

  function submitComment() {
    const trimmed = body.trim();
    if (!trimmed) {
      return;
    }
    addComment.mutate({ body: trimmed }, { onSuccess: () => setBody('') });
  }

  return (
    <Card style={{ maxWidth: 880, margin: '0 auto' }}>
      {/* AC2: status flow as a horizontal progress bar */}
      <StatusBadge status={issue.status} showProgress />

      <Title level={3} style={{ marginTop: 16 }}>
        #{issue.id} {issue.title}
      </Title>

      <Descriptions column={2} size="small" bordered style={{ marginBottom: 16 }}>
        <Descriptions.Item label="카테고리" span={2}>
          {issue.categoryL1.name} &gt; {issue.categoryL2.name} &gt; {issue.categoryL3.name}
        </Descriptions.Item>
        <Descriptions.Item label="우선순위">
          <PriorityBadge priority={issue.priority} />
        </Descriptions.Item>
        <Descriptions.Item label="상태">
          <StatusBadge status={issue.status} />
        </Descriptions.Item>
        <Descriptions.Item label="접수자">{issue.createdByName}</Descriptions.Item>
        <Descriptions.Item label="담당자">{issue.assigneeName ?? '-'}</Descriptions.Item>
        <Descriptions.Item label="접수일">{fmt(issue.createdAt)}</Descriptions.Item>
        <Descriptions.Item label="수정일">{fmt(issue.updatedAt)}</Descriptions.Item>
        <Descriptions.Item label="해결일" span={2}>
          {fmt(issue.resolvedAt)}
        </Descriptions.Item>
        {/* Deviation #2: caller PII only present for AGENT/ADMIN (null for FIELD) */}
        {issue.callerName !== null && (
          <Descriptions.Item label="발신자명">{issue.callerName}</Descriptions.Item>
        )}
        {issue.callerPhone !== null && (
          <Descriptions.Item label="발신자 전화">{issue.callerPhone}</Descriptions.Item>
        )}
      </Descriptions>

      <Title level={5}>상세 내용</Title>
      {/* React escapes by default; no dangerouslySetInnerHTML (§6.9 XSS) */}
      <Paragraph style={{ whiteSpace: 'pre-wrap' }}>{issue.description}</Paragraph>

      <Divider />

      {/* AC1/AC5: attachment gallery — empty until upload lands (Story 2.6) */}
      <Title level={5}>첨부 이미지</Title>
      {issue.attachments.length === 0 ? (
        <Empty description="첨부된 이미지가 없습니다 (업로드는 모바일 조치에서 지원)" />
      ) : (
        <Image.PreviewGroup>
          <Space wrap>
            {issue.attachments.map((a) => (
              <Image
                key={a.id}
                src={a.url}
                alt={a.originalName}
                width={120}
                height={120}
                style={{ objectFit: 'cover' }}
                loading="lazy"
              />
            ))}
          </Space>
        </Image.PreviewGroup>
      )}

      <Divider />

      {/* AC3: comment section + form */}
      <section aria-label="코멘트">
      <Title level={5}>코멘트</Title>
      <List
        dataSource={issue.comments}
        locale={{ emptyText: '코멘트가 없습니다' }}
        renderItem={(c) => (
          <List.Item>
            <List.Item.Meta
              title={
                <Space>
                  <Text strong>{c.authorName}</Text>
                  <Tag>{KIND_LABELS[c.kind]}</Tag>
                  <Text type="secondary" style={{ fontSize: 12 }}>
                    {fmt(c.createdAt)}
                  </Text>
                </Space>
              }
              description={<span style={{ whiteSpace: 'pre-wrap' }}>{c.body}</span>}
            />
          </List.Item>
        )}
      />
      <Space.Compact style={{ width: '100%', marginTop: 8 }}>
        <Input.TextArea
          value={body}
          onChange={(e) => setBody(e.target.value)}
          placeholder="코멘트를 입력하세요"
          autoSize={{ minRows: 2, maxRows: 6 }}
          maxLength={4000}
        />
      </Space.Compact>
      <Button
        type="primary"
        onClick={submitComment}
        loading={addComment.isPending}
        disabled={!body.trim()}
        style={{ marginTop: 8 }}
      >
        코멘트 등록
      </Button>
      </section>

      <Divider />

      {/* AC4: activity log, newest first (backend desc order) */}
      <section aria-label="활동 로그">
      <Title level={5}>활동 로그</Title>
      <List
        loading={events.isLoading}
        dataSource={events.data ?? []}
        locale={{ emptyText: '활동 기록이 없습니다' }}
        renderItem={(a) => (
          <List.Item>
            <Space>
              <Tag>{EVENT_LABELS[a.eventType]}</Tag>
              <Text>{a.actorName}</Text>
              <Text type="secondary" style={{ fontSize: 12 }}>
                {fmt(a.createdAt)}
              </Text>
            </Space>
          </List.Item>
        )}
      />
      </section>
    </Card>
  );
}
