import { Card, Typography } from 'antd';

const { Title, Paragraph } = Typography;

export function IssueListView() {
  return (
    <Card>
      <Title level={3}>이슈 리스트</Title>
      <Paragraph type="secondary">
        Epic 2 에서 구현 예정입니다. 현재 시드 데이터 20건이 DB 에 존재합니다.
      </Paragraph>
    </Card>
  );
}
