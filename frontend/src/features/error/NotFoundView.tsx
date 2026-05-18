import { Button, Result } from 'antd';
import { useNavigate } from 'react-router-dom';

export function NotFoundView() {
  const navigate = useNavigate();
  return (
    <Result
      status="404"
      title="페이지를 찾을 수 없습니다"
      subTitle="요청하신 경로가 존재하지 않습니다."
      extra={
        <Button type="primary" onClick={() => navigate('/')}>
          홈으로
        </Button>
      }
    />
  );
}
