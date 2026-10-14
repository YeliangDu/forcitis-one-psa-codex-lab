# apps/h5：H5 / 小程序端（Taro 4 + React）

状态：Phase 1a 只有目录骨架，没有实现，也没有 `package.json`，所以不属于 pnpm workspace，`pnpm verify` 不会构建它。

## 下一步（在 1a 后续或 1b 开始时做）

1. 新增 `package.json`（名称 `@forcitis/h5`），依赖 Taro 4、React，以及工作区包 `@forcitis/api`、`@forcitis/ui`、`@forcitis/design-system`。
   `@forcitis/ui` 基于 antd，不能直接用于 Taro；H5 需要在 `packages/` 下为移动端另建组件包（或在 `apps/h5/src/shared/ui` 中按正本 BottomSheet、TimesheetRow 等组件实现），令牌仍然只来自 `@forcitis/design-system` 的 `tokens.css`。
2. 在 `src/` 下按 `apps/web` 的分层建：`app/`、`modules/<m>/{api,hooks,model,pages,components,index.ts}`、`shared/`。
3. 把 H5 加进守护，规则不放宽：
   - `eslint.config.js` 的 `boundaries/elements` 增加 H5 的 app、module、shared 三类，`ALL_SOURCES`、`MODULES_APP_SHARED` 等文件范围加上 `apps/h5/src/**`，`ignores` 去掉 `apps/h5`；
   - `scripts/check-lint-config.mjs` 的 `CATEGORIES` 增加 H5 各类文件；
   - `scripts/check-structure.mjs` 的 `MODULES`、`SOURCE_ROOTS` 增加 H5；
   - 只有 `modules/<m>/api` 可以引用 `@forcitis/api`（F10），其余照 `apps/web`。
4. 1a 的 H5 页面（REIMAGINED_ARCHITECTURE §3.10）：企业微信静默登录（`snsapi_base`）、待办（新旧合并，老待办跳转老系统）、审批详情（认领、通过、驳回、退回）。接口以 `packages/api/openapi.json` 为准，规则与 `apps/web` 的待办、任务详情模块一致，业务判定（能否办理、按钮显隐的依据）来自后端。
5. 登录：Cookie 会话，写请求带 `X-XSRF-TOKEN`（`@forcitis/api` 已处理）；小程序走 `jscode2session` 对应的登录入口，令牌不进 URL。
6. 老 H5（`psa` 仓库）只用来理解行为，代码不搬运。

## 目录

```
apps/h5/
  README.md
  config/     Taro 构建配置（待建）
  src/        应用代码（待建，分层同 apps/web）
```
