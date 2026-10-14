import { TextField } from '@forcitis/ui';

const noop = () => {};
const frame = { width: 360, display: 'flex', flexDirection: 'column' as const, gap: 16 };

export const Default = () => (
  <div style={frame}>
    <TextField label="单据类型代码" value="QT" onChange={noop} hint="两位大写字母，用于业务编号前缀" />
  </div>
);

export const WithError = () => (
  <div style={frame}>
    <TextField label="起始流水号" value="0" onChange={noop} type="number" inputMode="numeric" error="起始流水号须大于 0" />
  </div>
);

export const PasswordAndDisabled = () => (
  <div style={frame}>
    <TextField label="登录密码" value="forcitis-2026" onChange={noop} type="password" autoComplete="current-password" />
    <TextField label="法人主体" value="弗思特（上海）" onChange={noop} disabled />
  </div>
);
