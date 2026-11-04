# apps/h5：企业微信 H5（Taro 4 + React 18）

Phase 1a 的 H5 页面：静默登录、待办、审批详情。接口以 `packages/api/openapi.json` 生成的客户端和类型为准；业务判定（能否办理、按钮显隐的依据）来自后端，前端只展示。

## 页面

| 页面 | 入口 | 说明 |
|---|---|---|
| 待办 | `src/app/pages/inbox.tsx` | 本系统审批待办：进入审批详情；支持知会筛选 |
| 审批详情 | `src/app/pages/task.tsx?taskId=` | 认领、通过、驳回、退回；驳回和退回必须填写理由；按钮按办理权限和 `canAct` 显示 |
| 静默登录 | `src/modules/session` | 不是页面，是整个应用外面的一道门（`SessionGate`） |

## 静默登录

1. 查询当前会话；已有会话直接进入。
2. 没有会话且地址里没有授权回跳：申请登录用的 state（入口类型 `WORKBENCH`），跳到企业微信 `snsapi_base` 授权地址。
3. 授权回跳时地址里带 `code` 和 `state`：**先**把它们从地址栏清掉，**再**提交登录。会话靠 Cookie 维持，令牌不进 URL。
4. 登录被拒绝或会话没有保存下来时，显示原因和「重新登录」，不自动循环。

企业 ID 通过构建时的环境变量 `TARO_APP_WECOM_CORP_ID` 注入；没有配置时登录失败并提示，不会静默放行。

## 命令

```
pnpm --filter @forcitis/h5 build       # taro build --type h5，再检查 dist/js 能否按 ES2020 解析（scripts/check-bundle.mjs）
pnpm --filter @forcitis/h5 dev         # 开发服务器，/api 代理到 localhost:8080
pnpm --filter @forcitis/h5 test        # Vitest + Testing Library
```

## 结构与守护

分层同 `apps/web`：`app/` → `modules/<m>/{api,hooks,model,pages,components,index.ts}` → `shared/`。F1–F14 守护同样覆盖这里（见 `frontend/eslint.config.js` 和 `frontend/scripts/check-lint-config.mjs`），另有：

- 业务代码不直接用 Taro 组件和 Taro API：组件用 `shared/ui`，路由和跳转用 `shared/platform`；
- 不使用 `Taro.request`，网络请求只经 `modules/<m>/api` 调生成的客户端；
- 不引用 `@forcitis/ui`（基于 antd，H5 用不了）；令牌来自 `@forcitis/design-system/mobile`（只加载 `tokens.css`）；
- 两个应用互不引用。

## 构建注意

- 工作区包（`packages/*`，TypeScript 源码）、`@tanstack/*`、`zod`、`query-string` 要过 Babel：Taro 的压缩器会把 `@tanstack/query-core` 的类私有字段写成非法语法，页面整页空白，单元测试看不出来，所以 `build` 之后有 `check-bundle.mjs`。
- 路由用 hash 模式；企业微信授权回跳的 `?code=…&state=…` 在 `#` 之前，登录后立即清除。
- Taro 的 `Button` 传 `disabled={false}` 会写出 `disabled="false"` 属性并变灰，`ActionButton` 只在为真时传。
- Taro 把页面当作 App 的 children 挂载，登录门（`SessionGate`）因此放在每个页面里（`app/page-frame.tsx`），不能放在 `app.tsx`。

## 为什么是 React 18

Taro 4.3 的 React 运行时（`react-reconciler` 0.29）要求 React 18，PC 端用 React 19。两端各自在自己的 `package.json` 里声明；类型包统一用工作区目录里的版本，避免 `@types/react` 双版本。Taro 升级支持 React 19 后再合并。

老 H5 只用来理解行为，代码没有搬运。
