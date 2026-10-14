import { TextAreaField } from '@forcitis/ui';

const noop = () => {};
const frame = { width: 420, display: 'flex', flexDirection: 'column' as const, gap: 16 };

export const Default = () => (
  <div style={frame}>
    <TextAreaField label="审批意见" value="同意，按报价执行。" onChange={noop} hint="审批意见会随单据一同归档" />
  </div>
);

export const WithError = () => (
  <div style={frame}>
    <TextAreaField label="驳回原因" value="" onChange={noop} rows={4} error="驳回时必须填写原因" />
  </div>
);
