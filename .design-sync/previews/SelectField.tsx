import { SelectField } from '@forcitis/ui';

const noop = () => {};
const frame = { width: 360, display: 'flex', flexDirection: 'column' as const, gap: 16 };
const resetOptions = [
  { value: 'NEVER', label: '不重置' },
  { value: 'YEARLY', label: '按年' },
  { value: 'MONTHLY', label: '按月' },
];

export const Default = () => (
  <div style={frame}>
    <SelectField label="流水号重置周期" value="MONTHLY" options={resetOptions} onChange={noop} hint="按月重置时，编号模板需包含年月" />
  </div>
);

export const WithError = () => (
  <div style={frame}>
    <SelectField label="流水号重置周期" value="NEVER" options={resetOptions} onChange={noop} error="编号模板含年月时不能选择不重置" />
  </div>
);
