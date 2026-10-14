import { LoginPanel, Notice } from '@forcitis/ui';

const noop = () => {};

export const Default = () => (
  <LoginPanel
    title="FORCITIS one"
    subtitle="请选择登录方式"
    entries={[
      { key: 'wecom', label: '企业微信扫码登录', description: '使用企业微信扫描二维码登录', onSelect: noop },
      { key: 'sso', label: '致远单点登录', description: '从公司门户进入', onSelect: noop },
    ]}
  />
);

export const WithProblem = () => (
  <LoginPanel
    title="FORCITIS one"
    subtitle="请选择登录方式"
    notice={<Notice tone="error" title="企业微信授权失败，请重新扫码" />}
    entries={[
      { key: 'wecom', label: '企业微信扫码登录', description: '使用企业微信扫描二维码登录', onSelect: noop, loading: true },
      { key: 'sso', label: '致远单点登录', description: '从公司门户进入', onSelect: noop, unavailableText: '尚未配置致远单点登录' },
    ]}
  />
);
