import { Empty, Spin, Typography } from 'antd';
import { useNavigate } from 'react-router-dom';
import { useMyAssigned } from '../../shared/hooks/useMyAssigned';
import { IssueCard } from '../../shared/components/IssueCard';

const { Title } = Typography;

/** Field worker mobile home (Story 2.5): my assigned issues as a card stack, severity-ordered. */
export function MobileFieldHomeView() {
  const navigate = useNavigate();
  const { data, isLoading } = useMyAssigned();

  return (
    <div style={{ maxWidth: 480, margin: '0 auto', padding: 16 }}>
      <Title level={4} style={{ marginTop: 0 }}>
        내 작업
      </Title>
      {isLoading ? (
        <div style={{ display: 'flex', justifyContent: 'center', padding: 48 }}>
          <Spin size="large" />
        </div>
      ) : data && data.length > 0 ? (
        data.map((issue) => (
          <IssueCard
            key={issue.id}
            issue={issue}
            showAssignee={false}
            onClick={() => navigate(`/m/issues/${issue.id}`)}
          />
        ))
      ) : (
        <Empty description="배정된 이슈가 없습니다" />
      )}
    </div>
  );
}
