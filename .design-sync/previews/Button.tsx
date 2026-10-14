import { Button } from '@forcitis/ui';

export const Variants = () => (
  <div style={{ display: 'flex', gap: 12, alignItems: 'center' }}>
    <Button>提交审批</Button>
    <Button variant="default">保存草稿</Button>
    <Button variant="danger">作废报价单</Button>
  </div>
);

export const States = () => (
  <div style={{ display: 'flex', gap: 12, alignItems: 'center' }}>
    <Button loading>提交中</Button>
    <Button disabled>提交审批</Button>
    <Button variant="default" disabled>保存草稿</Button>
  </div>
);

export const Block = () => (
  <div style={{ width: 320 }}>
    <Button block>企业微信扫码登录</Button>
  </div>
);
