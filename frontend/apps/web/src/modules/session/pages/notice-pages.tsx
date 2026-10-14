import { Button, ResultPage } from '@forcitis/ui';
import { useNavigate } from 'react-router';

export function SessionExpiredPage() {
  const navigate = useNavigate();
  return (
    <ResultPage
      kind="expired"
      title="登录已过期"
      description="长时间未操作或登录时间已满，请重新登录。"
      actions={<Button onClick={() => void navigate('/login', { replace: true })}>重新登录</Button>}
    />
  );
}

export function ForbiddenPage() {
  const navigate = useNavigate();
  return (
    <ResultPage
      kind="forbidden"
      title="权限不足"
      description="你的用户账号没有访问此内容的权限，如需使用请联系管理员授权。"
      actions={<Button onClick={() => void navigate('/')}>返回首页</Button>}
    />
  );
}
