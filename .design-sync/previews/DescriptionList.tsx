import { DescriptionList, StatusBadge } from '@forcitis/ui';

export const TaskDetail = () => (
  <div style={{ width: 520 }}>
    <DescriptionList
      items={[
        { label: '单据', value: '报价单 QT-FST-202610-0012' },
        { label: '当前节点', value: '部门负责人审批' },
        { label: '发起人', value: '王磊' },
        { label: '发起时间', value: '2026-10-14 09:32' },
        { label: '状态', value: <StatusBadge tone="pending">待审批</StatusBadge> },
      ]}
    />
  </div>
);
