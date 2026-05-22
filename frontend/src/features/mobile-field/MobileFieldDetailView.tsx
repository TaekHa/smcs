import { Button, Card, Result } from 'antd';
import { useNavigate, useParams } from 'react-router-dom';

/**
 * Placeholder. The mobile detail (photo attach + field action + 완료 처리) is Story 2.6.
 * Story 2.5 only needs this route to exist so a card tap (AC4) lands somewhere meaningful.
 */
export function MobileFieldDetailView() {
  const { id } = useParams();
  const navigate = useNavigate();
  return (
    <div style={{ maxWidth: 480, margin: '0 auto', padding: 16 }}>
      <Card>
        <Result
          status="info"
          title={`이슈 #${id}`}
          subTitle="모바일 상세(사진 첨부·조치·완료)는 준비 중입니다 (Story 2.6)."
          extra={
            <Button type="primary" onClick={() => navigate('/m')}>
              내 작업으로
            </Button>
          }
        />
      </Card>
    </div>
  );
}
