import { Card, Result, Button } from 'antd';
import { useNavigate, useParams } from 'react-router-dom';

/**
 * Placeholder. Full issue detail (metadata, activity log, comments, authz guard
 * per architecture §9.5) is Story 2.3. Story 2.1 only needs this route to exist
 * so post-create navigation (AC4) lands somewhere meaningful.
 */
export function IssueDetailView() {
  const { id } = useParams();
  const navigate = useNavigate();
  return (
    <Card style={{ maxWidth: 720, margin: '0 auto' }}>
      <Result
        status="success"
        title={`이슈 #${id} 가 등록되었습니다`}
        subTitle="상세 화면은 준비 중입니다 (Story 2.3)."
        extra={
          <Button type="primary" onClick={() => navigate('/issues')}>
            이슈 목록으로
          </Button>
        }
      />
    </Card>
  );
}
