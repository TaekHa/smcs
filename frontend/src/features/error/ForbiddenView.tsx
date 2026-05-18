import { Button, Result } from 'antd';
import { useNavigate } from 'react-router-dom';

export function ForbiddenView() {
  const navigate = useNavigate();
  return (
    <Result
      status="403"
      title="권한이 없습니다"
      subTitle="이 페이지에 접근할 수 있는 권한이 없습니다."
      extra={
        <Button type="primary" onClick={() => navigate('/')}>
          내 메인 화면으로
        </Button>
      }
    />
  );
}
