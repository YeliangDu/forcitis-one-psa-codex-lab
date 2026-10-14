import { Dialog, TextAreaField } from '@forcitis/ui';

const noop = () => {};

export const Confirm = () => (
  <Dialog open title="提交审批" confirmText="提交" onConfirm={noop} onClose={noop}>
    提交后报价单将进入部门负责人审批，审批期间不可修改。
  </Dialog>
);

export const DangerWithReason = () => (
  <Dialog open title="驳回报价单" confirmText="驳回" danger onConfirm={noop} onClose={noop}>
    <TextAreaField label="驳回原因" value="毛利率低于阈值 18%，请补充说明或调整报价。" onChange={noop} />
  </Dialog>
);
