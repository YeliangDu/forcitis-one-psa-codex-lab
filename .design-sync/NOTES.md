# design-sync 记录（FORCITIS one UI → Claude Design）

- 目标项目是**预演项目**「Forcitis One UI · 代码同步」（projectId 见 config.json），**不是正本**「Forcitis One Design System」。用户 2026-10-14 决定：代码组件覆盖正本全部组件之前，正本不动；切换回正本之前，先在主会话里完整备份正本（子代理调不到 DesignSync）。
- 同步目录放在仓库根（`.design-sync/`、`.ds-sync/`、`ds-bundle/`），不放在 `frontend/`：放在 frontend 下会被 `pnpm lint` 扫到，导致 verify 失败。
- 构建两步，见 `config.json` 的 `buildCmd`：
  1. `tsc -p .design-sync/tsconfig.dts.json` 生成 `frontend/packages/ui/dist/types`。`types: []` 用来避开 vite/client；`raw-shim.d.ts` 声明 `*?raw`。`packages/ui/package.json` 的 `publishConfig.types` 指向它，转换器靠它找到 `.d.ts` 入口。
  2. `vite build -c .design-sync/vite.lib.config.mjs` 生成 `dist/index.js` 和 `dist/ui.css`。必须用 Vite：`design-system/theme.ts` 用 `tokens.css?raw` 读令牌，esbuild 会把它解析成空对象，主题在加载时直接报错。入口 `lib-entry.ts` 额外导出 `DesignSystemProvider`，供 `cfg.provider` 使用。
- 转换器命令（从仓库根执行）：`node .ds-sync/package-build.mjs --config .design-sync/config.json --node-modules frontend/apps/web/node_modules --entry frontend/packages/ui/dist/index.js --out ./ds-bundle`
- 分组通过 `docsMap` 指向 `.design-sync/docs/<Name>.md` 的 category 存根来设定（actions / data / feedback / forms / layout）。多组件文件（data-display、feedback、form-fields）用 `componentSrcMap` 固定源码路径。
- playwright 固定 1.63.0，对应本机缓存的 chromium-1243。
- 字体：用户 2026-10-14 同意先用系统回退字体（PingFang / 微软雅黑 / system-ui），不随包提供 Montserrat、思源黑体、IBM Plex Mono 的字体文件。`[FONT_MISSING]` 属于预期告警。字体文件入库由调度会话另行派工。
- 本次在代码里补了字体层：`tokens.css` 新增 typography 段（`--font-display`、`--font-sans`、`--font-mono`，并在 `:root` 设置 font-family），`theme.ts` 把它映射到 antd 的 fontFamily 和 fontFamilyCode。补之前，antd 以外的文字在线上显示为浏览器默认的宋体。

## 已知渲染告警（已人工确认）
- `[RENDER_THIN] Dialog`：弹窗通过 portal 渲染，测得高度为 0。截图确认正常（cardMode single，760x420）。
- `[FONT_MISSING]`：见上方字体条目。

## 代码层面的已知差异（如实同步，没有在本次修复）
- SelectField 用的是原生 `<select>`，出错时边框不变红，观感也和 antd 输入框不一致。已登记到 DS_GAPS 候选。

## 重新同步要注意的风险
- `lib-entry.ts`、`vite.lib.config.mjs`、`tsconfig.dts.json` 都用相对路径指向 `frontend/packages/*`，包目录如果调整，这三个文件要一起改。
- `publishConfig.types` 依赖 `dist/types` 存在，每次重新同步前必须先跑 buildCmd。
- 新增组件时，要在 `docsMap` 加分组存根；如果它和其他组件写在同一个文件里，还要加 `componentSrcMap`。
- 预览中的业务数据（编号模板、待办标题）是示例数据，业务口径调整后要同步更新。
- 正本的约 45 个组件（DataGrid、LedgerTable、WbsGrid、MoneyInput 等）代码里还没有，预演项目只覆盖代码已有的 17 个。
