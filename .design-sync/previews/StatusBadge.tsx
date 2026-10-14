import { StatusBadge } from '@forcitis/ui';

export const Tones = () => (
  <div style={{ display: 'flex', gap: 8, flexWrap: 'wrap' }}>
    <StatusBadge tone="posted">已过账</StatusBadge>
    <StatusBadge tone="pending">待审批</StatusBadge>
    <StatusBadge tone="rejected">已驳回</StatusBadge>
    <StatusBadge tone="reversal">已冲销</StatusBadge>
    <StatusBadge tone="neutral">老系统</StatusBadge>
    <StatusBadge tone="info">新系统</StatusBadge>
  </div>
);

export const InContext = () => (
  <div style={{ display: 'flex', alignItems: 'center', gap: 8 }}>
    <span>编号规则 QT</span>
    <StatusBadge tone="posted">启用</StatusBadge>
  </div>
);
