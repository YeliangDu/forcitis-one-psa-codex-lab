import { Notice } from '@forcitis/ui';

const col = { display: 'flex', flexDirection: 'column' as const, gap: 12, width: 560 };

export const Tones = () => (
  <div style={col}>
    <Notice tone="error" title="待办加载失败">网络连接中断，请稍后刷新。</Notice>
    <Notice tone="warning" title="老系统的待办暂时无法读取">当前只显示新系统的待办，请稍后刷新。</Notice>
    <Notice tone="info" title="编号规则修改后从下一张单据开始生效" />
    <Notice tone="success" title="组织同步完成">共更新 12 个部门、86 名员工。</Notice>
  </div>
);

export const TitleOnly = () => (
  <div style={col}>
    <Notice tone="error" title="企业微信授权失败，请重新扫码" />
  </div>
);
