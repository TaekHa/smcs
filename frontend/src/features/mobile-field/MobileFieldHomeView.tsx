import { Card, Typography } from 'antd';

const { Title, Paragraph } = Typography;

export function MobileFieldHomeView() {
  return (
    <div style={{ maxWidth: 480, margin: '0 auto', padding: 16 }}>
      <Card>
        <Title level={3}>내 작업 - 모바일</Title>
        <Paragraph type="secondary">
          Epic 2 에서 배정 카드 스택 + 카메라 첨부 UI 가 구현될 예정입니다.
        </Paragraph>
      </Card>
    </div>
  );
}
