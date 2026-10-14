# FORCITIS one UI（@forcitis/ui）使用约定

## 包装与初始化
- 所有组件必须放在 `DesignSystemProvider` 里面。它提供 antd 主题（石墨灰主色、财务状态色、Noto Sans SC 字体栈）；不包的话，按钮、表格、弹窗会退回 antd 默认蓝色主题。
```jsx
const { DesignSystemProvider, AppShell, PageTitle, Button } = window.ForcitisOneUI;
<DesignSystemProvider>
  <AppShell brand="FORCITIS one" navigation={nav} activePath="/inbox" onNavigate={go}
            userName="张敏" logoutText="退出登录" onLogout={logout}>
    <PageTitle text="待办" actions={<Button>新建报价单</Button>} />
  </AppShell>
</DesignSystemProvider>
```
- 字体由令牌在 `:root` 统一设置，不要给页面另设 font-family。

## 样式写法
- 组件没有对外的 CSS 类名可用，外观通过 props 控制：`Button` 用 `variant`（primary / default / danger），`StatusBadge` 用 `tone`（posted 已过账、pending 待审批、rejected 已驳回、reversal 已冲销、neutral 中性、info 信息），`Notice` 用 `tone`（error / warning / info / success），`ResultPage` 用 `kind`（forbidden / expired / not-found / error）。
- 自己写布局胶水时只用语义令牌：`var(--surface-page)`、`var(--surface-raised)`、`var(--text-primary)`、`var(--text-secondary)`、`var(--border-default)`、`var(--intent-primary-bg)`、`var(--status-posted-fg)` 等（`--surface-* / --text-* / --border-* / --intent-* / --status-* / --ledger-*`）。不要直接用 `--slate-*`、`--red-*` 这类色板原色，也不要写十六进制颜色。
- 字体族令牌：`var(--font-sans)`（正文）、`var(--font-mono)`（代码、编号）、`var(--font-display)`（品牌标题）。

## 真实来源
- 令牌全集在 `styles.css` 引入的 `_ds_bundle.css` 中（palette、semantic、typography 三段）。
- 每个组件的 props 契约见 `components/<分组>/<组件名>/<组件名>.d.ts`，用法示例见同目录的 `.prompt.md`。

## 组合示例
```jsx
const { DesignSystemProvider, PageTitle, Button, DataTable, StatusBadge, Notice } = window.ForcitisOneUI;
<DesignSystemProvider>
  <div style={{ padding: 24, background: 'var(--surface-page)', display: 'flex', flexDirection: 'column', gap: 16 }}>
    <PageTitle text="编号规则" actions={<Button variant="default">导出</Button>} />
    <Notice tone="info" title="编号规则修改后从下一张单据开始生效" />
    <DataTable
      rowKey={(r) => r.code}
      rows={[{ code: 'QT', type: '报价单', enabled: true }]}
      columns={[
        { key: 'type', title: '单据类型', render: (r) => r.type },
        { key: 'status', title: '状态', render: (r) => <StatusBadge tone={r.enabled ? 'posted' : 'neutral'}>{r.enabled ? '启用' : '停用'}</StatusBadge> },
      ]}
    />
  </div>
</DesignSystemProvider>
```
- 文案一律中文，状态用词与统一语言一致（已过账、待审批、已驳回、已冲销）。
