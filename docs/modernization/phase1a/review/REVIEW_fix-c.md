# 修复批 C（前端）单独 opus 复审

条目编号以本文为准，外部引用加前缀 C-（例如 C-H1、C-M7）。

结论：**修后整合**。前端代码本身质量不错，`pnpm verify` 也全绿。但手写的 `openapi.json` 和后端现状差距很大：本分支单独开 PR 时，后端 CI 会挂；就算 CI 过了，所有带版本的写操作在运行时也会被客户端自己拒掉。所以必须和后端改造放在同一个整合分支里合入，下面几处也要先改。

## `pnpm verify`（frontend 目录，跑的是 `package.json` 里的 `verify` 链）
退出码 0。check:generated（42 个操作）、typecheck、lint-config 自检、lint、check:structure、check:copy（94 个文件）、test:scripts 都通过。各包单测 35+1+25+4+43+89 条全部通过。web 构建通过，H5 的 `taro build` 加 `check-bundle` 也通过（5 个脚本都能按 ES2020 解析）。

---

## Blocker

**B-1 手写的 `openapi.json` 与后端导出不一致，后端 CI 必然失败**
- 证据：`.github/workflows/ci.yml:37-38`。后端 job 跑完 `mvnw verify` 后，`OpenApiExportTest` 会重写 `frontend/packages/api/openapi.json`，然后执行 `git diff --exit-code`。本分支没有改后端代码（只改了 `backend/glossary/proposed/*`）。
- 后端 `src/main` 里查不到 `If-Match`、`Idempotency`、`COST_TIMESHEET_WORK_DATE_REQUIRED`、`PLATFORM_WEB_VERSION_REQUIRED`、`approval-task-records` 这些东西。
- 手写文档新增了 15 个 schema（ErrorCode 86 个值、ProblemDetail、TaskRecord* 等），给 18 个操作加了 If-Match / Idempotency-Key，删掉了 `SubmitTimesheetResponse`，另加了 `/approval-task-records`。
- 另外 `scripts/generate-api.mjs` 新增的约定检查要求每个操作都声明 ProblemDetail 错误响应。后端现在导出的文档会被生成器直接拒绝。
- 建议：只能和实现这些形状的后端分支一起整合，并以后端导出为准重新生成。用一行 diff 证明手写文件与导出完全一致；有差异就逐条回到契约上对齐，不能让前端文件压过后端导出。

**B-2 后端还没下发 ETag，所有带版本的写操作都会在客户端被拒**
- 证据：`packages/api/src/http.ts:170-174`。缓存里没有版本时直接抛 `MissingVersionError`。
- 受影响的有：审批的认领 / 通过 / 驳回 / 退回 / 转交，工时单的提交 / 撤回 / 取消，编号规则、工作日历、角色、法人的更新。
- 这个"不盲发"的设计本身是对的。问题在于前提：每个相应的 GET 都要返回 `ETag`，每个写接口都要校验 If-Match 并返回 412/428。
- 建议：整合前确认后端分支已经做到这两点，并为每个带版本的接口各补一条集成测试。

---

## High

**H-1 ETag 存在客户端的全局隐式缓存里，与界面上显示的那份数据脱钩，乐观并发保护可能失效**
- 证据：`http.ts:158,171,184`。任何一次 GET 成功都会覆盖该资源的版本，写操作取的是"最近一次读到的版本"，不是用户编辑所依据的那一版。
- 举例：`work-calendar-page.tsx:48-58` 把草稿留在组件状态里，保存时整年替换。如果期间这一年的 query 因为 invalidate 或重新挂载被重读，ETag 会悄悄变成新版本，而草稿还是基于旧数据，提交后会覆盖别人的修改，并且不会收到 412。工时单页每 5 秒轮询，也会刷新同一个资源的版本。
- 现在的界面路径下触发概率不高，但这是机制上的缺陷，以后的页面会踩到。
- 建议：让 operations 返回 `{ data, version }`，由调用方显式传 `ifMatch`。这样版本与界面数据一起存在 query 缓存里，412 后再统一 invalidate。

**H-2 每次调用都生成新的 Idempotency-Key，幂等不起作用**
- 证据：`http.ts:176`。全仓没有调用方传 `idempotencyKey`（已 grep）。
- 后果：网络超时后用户重点一次，就是新的键。第一次如果其实已经成功，第二次会因为版本已变收到 412，界面提示"内容刚刚被其他人修改"，结论是错的。服务端"键 → 响应"的重放能力完全没用上。
- 建议：每个 mutation 的"一次用户意图"生成一个键，存在 mutation 的上下文或 `useRef` 里，重试时复用，成功或用户修改内容后再换新键。补一条测试：先断网再重试，断言两次请求的键相同。

**H-3 H5 里有一份与 core 已经分叉的死代码，正是 M-15 要消除的复制，守护没有拦住**
- 证据：`apps/h5/src/modules/approval/model/paths.ts`（由 `inbox.ts` 改名而来）仍保留 `filterInbox`（没有 `notice` 分支）、`inboxRowId`、`InboxFilterKey`、`MergedInbox`、`InboxItem`，页面只用到其中的 `taskPath` 和 `INBOX_PAGE`。
- 两个 app 的待办页之间还有近似复制：`viewOf`、`stateOf`/`badgeOf`，以及已办行的标题拼接（`PROCESS_LABELS[...] + documentId`），见 web `inbox-page.tsx:24-47` 和 h5 `inbox-page.tsx:29-58`。
- 建议：删掉这些死导出；已办标题、状态标记、视图解析移到 core。

**H-4 删改了断言却没有单列理由**
- CLAUDE.md 要求删改断言写明理由，并在报告和 PR 中单列。几条提交说明里都没有。
- 被删且没有等价替代的一条：H5 原 `model/task.test.ts` 中 `taskPath('a/b c')` 应编码为 `/app/pages/task?taskId=a%2Fb%20c`，以及"新旧待办编号相同时 `inboxRowId` 不相同"。core 的测试里也没有覆盖 `inboxRowId`。
- 理由正当、但需要单列的改动：
  - D-1a-179：web 和 h5 的"没有权限码 → 不显示按钮"两条测试，反转为"canAct 为真时显示"。
  - 文案改动："该…" → "这个…"；"或你不是它的参与人" → "或者你不是参与人"；"该企业微信用户" → "企业微信用户"；"但登录状态" → "登录状态"。这些是为配合单字连接词规则改的。
  - `failureOf` 的返回形状变了；`http.test` 中 `code: 'X'` 那条改为 ProblemDetail。
- 建议：把 taskPath 编码和 inboxRowId 两条断言补回来；PR 中列出上面这张清单。

---

## Medium

- **M-1 ProblemDetail 的 `errorCode` 按严格枚举校验，向前不兼容**（`http.ts:147-151`）。后端新增一个错误码，前端还没重新生成时，整份 ProblemDetail 都会被判为无效，`legacyLoginUrl` 也一起丢掉。建议：`errorCode` 先按字符串解析，查表时再收窄成 `ErrorCode`；遇到未知码时上报一次（G-1 要求不静默）。成功响应里的枚举（ProcessKey、TaskRecordAction）同理：一个新值就会让整个收件箱报错。
- **M-2 收件箱后台刷新失败时，整页被错误提示替换**
  - web `inbox-page.tsx:56`：判断条件是 `isError && !isFetchNextPageError`，这包括了已经有数据时的刷新失败。
  - h5 `inbox-page.tsx:66`：任何错误都整页替换。
  - 收件箱每 60 秒轮询，网络抖一下列表就消失。建议只在没有数据时整页报错，有数据时只在列表上方提示。
  - 另外，`useInfiniteQuery` 每 60 秒会顺序重拉所有已加载的页，游标可能漂移，出现重行或漏行（`core/approval/hooks.ts:13-21`）。建议只轮询第一页，或者刷新时回到第一页。
- **M-3 工时单的 5 秒轮询没有上限**（`timesheet/hooks/use-timesheet.ts:14`）
  - "审批中"可能持续好几天，只要页面开着就每 5 秒请求一次。H-1 是"审批订阅后再发起"，如果订阅失败，界面会永远停在"审批中"，没有任何异常信号。
  - 页面卸载或切到后台时会停止（`gcTime: 0`，后台不刷新），所以不算泄漏。
  - 建议：刚提交后的一段时间内退避轮询（比如 5 秒到 60 秒、最多 N 次），之后改为手动刷新。契约里是否需要一个"审批已发起 / 发起失败"的可见状态，交协调者确认。
  - 轮询本身没有测试。
- **M-4 退出登录时没有清理版本缓存**：`forgetVersions` 只在测试替身里被调用；`use-logout.ts:10-13` 和 `use-api-guards.ts` 的未认证处理只做了 `queryClient.clear()`。版本缓存跨用户残留，而且没有上限，每个带 ETag 的 GET 都会存一条。
- **M-5 412 之后没有自动重读**：版本被删除后，用户再点一次只会收到"版本缺失"的提示，必须手动刷新。建议在 `onError` 里遇到 412 就 invalidate 对应的 query。
- **M-6 React 18/19 混用的残余风险**
  - 现有措施是够的：webpack alias（`h5/config/index.ts`）、vitest 的 `dedupe` 让 H5 测试里 core 的 hooks 跑在 React 18 上、`check-bundle` 检查 `react.transitional.element`。
  - 缺口一：core 的类型检查和单测只跑 React 19 的类型和运行时。core 一旦用了 React 19 才有的 API（`use`、`useActionState`、`useOptimistic`），类型检查能过，H5 运行时会崩，`check-bundle` 也查不出来。建议给 `packages/core` 加 lint 禁止这些 API，或者用 React 18 的类型再做一遍类型检查。
  - 缺口二：`check-bundle` 的注释说"只能打包一份 React"，实际只检测有没有 React 19，没有检测是否打进了两份 React 18（`check-bundle.mjs:4,24`）。
  - peerDeps `react >=18.3.1` 本身没问题。
- **M-7 外部票据登录存在登录 CSRF**（`/login/ticket`）
  - 片段处理是对的：`login-callback-page.tsx:29-30` 读取后立即用 `replaceState(pathname)` 清掉，片段本来也不会进 Referer 和服务端日志，有测试断言 `hash === ''`，查询参数里带票据会被拒。
  - 问题：攻击者可以拿自己的一次性票据做成链接发给受害者，受害者就会以攻击者的身份登录。致远 SSO 的 `?ticket` 也一样，这是原来就有的。
  - 建议：登录完成后显示"已以某某身份登录"并要求确认；或者后端把票据与发起端绑定。属于后端和契约层面的问题，记入问题清单。
- **M-8 相同内容检查很容易绕过**（`check-structure.mjs:20-36`）：只比对完全相同的字节，改一个空格或注释就过；也不比对 app 与 `packages/core` 之间。H-3 就是实例。建议去掉空白和注释后再比哈希，并把 `packages/core/src` 加入比对。
- **M-9 当前阶段用不到的导出**（违反"只实现当前阶段所需"）：`collectPages`、`MAX_PAGE_LIMIT`、`ERROR_CODES`、`ApiError.malformedProblem`、`isVersionConflict`、`messageForStatus` 的导出，在生产代码里都没有调用方。用得上的保留，其余删掉。
- **M-10 守护第 9 条第三项没完成**：后端 TaskNotifier 一类的对外文案没有纳入用语守护。如果这部分由后端代理负责，需在报告中说明归属。

## Nit

- `businessToday` 在 `timesheet/model/timesheet.ts:35-39` 和 `attendance/model/work-calendar.ts:63-67` 各写了一份，应移到 core。
- `safeExternalUrl` 允许 `http:`（`core/src/safe-url.ts:6`）。服务端下发的 `legacyLoginUrl` 不构成开放重定向，但建议只允许 https，或用部署配置的白名单。
- GET 成功但没有 ETag 时，没有清除旧版本（`http.ts:184-185`），版本缓存可能留着过期值。
- 工时单页的工作包、项目经理字段要用户直接输入数字 ID，工时没有上限和步长（`timesheet-page.tsx:67-69`）。超出本阶段可以接受，但应记入规则卡。
- 允许清单新增了约 57 个词，其中"外部系统""维护""计算""结果"偏业务含义，建议改为登记术语。
- 测试替身 `stubServer` 按表项先后匹配子串（`core/testing/stub-server.ts:33`），表项顺序会影响结果，容易误配。
- 新增注释共 33 行，基本是"为什么"类的说明，可以接受。提交信息和代码里没有用「PSA」指代系统；致远文案是原有的。

## 符合契约与用户决定的部分
- H-1：提交接口返回 202、没有响应体，`SubmitTimesheetResponse` 已删除，前端读取工时单状态。
- H-6：工作日期由用户填写，必填，不晚于上海时间的今天。
- T2：过去的日期显示为锁定，保存时要二次确认（`lockedDaysConfirmed`）。"只有组织数据管理员例外"取决于更新权限只授予该角色，需后端确认。
- `/h5/` 子路径：构建期校验，产物里的资源地址检查，企业微信回调地址保留子路径并有测试。
- RULE-280：空意见显示「（未填写意见）」，不写入数据。
- D-1a-130：收件箱 60 秒轮询加窗口聚焦时刷新。
- 错误码穷尽：`Record<ErrorCode, string|null>`，有测试双向核对。

---

## 必须在整合前修
1. **B-1**：和实现这些形状的后端分支在同一个整合分支里合入，以 `OpenApiExportTest` 的导出为准重新生成，并证明手写文件与导出一致（不一致的逐条回到契约对齐）。
2. **B-2**：确认后端每个带版本的接口都下发 ETag、校验 If-Match，并有集成测试。
3. **H-2**：Idempotency-Key 按"一次用户意图"复用，补重试测试。
4. **H-1**：版本随数据显式传递，至少工作日历和编号规则要做到；412 后 invalidate（M-5）。
5. **H-3**：删除 `apps/h5/.../model/paths.ts` 中的死代码副本，近似复制移到 core。
6. **H-4**：补回 taskPath 编码和 inboxRowId 两条断言；PR 中单列全部删改的断言及理由。
7. **M-1**：ProblemDetail 的 `errorCode` 宽松解析，未知码告警而不是丢掉整份错误体。
8. **M-2**：收件箱刷新失败时不再整页替换。

相关路径（worktree：`/Users/yeliang/Projects/FORCITIS/forcitis-one-psa/.claude/worktrees/agent-a9e107db6b488ef0a`）：
- `frontend/packages/api/src/http.ts`
- `frontend/packages/api/openapi.json`
- `frontend/packages/core/src/`
- `frontend/apps/h5/src/modules/approval/model/paths.ts`
- `frontend/apps/web/src/modules/{timesheet,attendance,approval}/`
- `frontend/scripts/check-structure.mjs`
- `.github/workflows/ci.yml`

---

## 复核（C 修复 b825753、01f4eb7 之后）

除 B-1、B-2 留到集成外，上面的「必须在整合前修」均已修。复核新增：
- **N-1**：宽松枚举在类型上仍是封闭联合，几处直接查表会显示 undefined。
- **N-2**：工作日历用 `version === undefined` 判断首次创建。
- H-1 的 `versionOf(data)` 做法可接受，条件是提供 `useVersionedQuery`（关闭结构共享、禁止 select）并以守护强制。

以上由 75ba247 修复。
