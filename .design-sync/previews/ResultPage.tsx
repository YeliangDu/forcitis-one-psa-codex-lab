import { Button, ResultPage } from '@forcitis/ui';

export const Expired = () => (
  <ResultPage kind="expired" title="登录已过期" description="长时间未操作或登录时间已满，请重新登录。" actions={<Button>重新登录</Button>} />
);

export const Forbidden = () => (
  <ResultPage kind="forbidden" title="权限不足" description="你的用户账号没有访问此内容的权限，如需使用请联系管理员授权。" actions={<Button>返回首页</Button>} />
);

export const NotFound = () => (
  <ResultPage kind="not-found" title="页面不存在" description="链接可能已失效，请从菜单重新进入。" actions={<Button variant="default">返回首页</Button>} />
);

export const Error = () => (
  <ResultPage kind="error" title="出错了" description="服务暂时不可用，请稍后再试。" />
);
