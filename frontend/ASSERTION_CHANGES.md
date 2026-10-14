# 本分支（fix-c-frontend）删改的断言与理由

基线为 1606d45。下面逐项列出被删除、反转或改写的断言；新增的测试不在此列。集成 PR 引用本文。

编号 C-H1、C-M2 等见 docs/modernization/phase1a/review/REVIEW_fix-c.md；REVIEW_b12f1df 开头的是上一份复审报告的编号。

## 1. 办理按钮不再看权限码（反转）

| 位置 | 原断言 | 现断言 | 理由 |
|---|---|---|---|
| web `task-detail-page.test.tsx`「只有查看权限时不显示任何办理按钮」 | 只有查看权限时没有任何办理按钮 | 只有查看权限、但服务端 `canAct` 为真时，显示认领、通过、驳回、退回 | 契约 D-1a-179：办理只要求是处理人或候选人，不另需权限码；是否能办理由服务端的 `canAct` 决定 |
| h5 `task-detail-page.test.tsx`「没有办理权限时一个按钮都不显示」 | 没有办理权限码时没有按钮 | 同上 | 同上 |
| core `approval/model.test.ts`（原 web、h5 的 `task.test.ts`） | `availableActions(task, false)` 全部为假 | 删除这一条；`availableActions` 不再有权限参数 | 同上；`canAct` 为假时全部为假的断言保留 |

## 2. 四处界面文案（因「单字只作连接词」守护而改写）

| 位置 | 原文案 | 现文案 | 理由 |
|---|---|---|---|
| h5 `task-detail-page.test.tsx`（两处） | 审批任务不存在，或你不是它的参与人 | 审批任务不存在，或者你不是参与人 | 「或你」「它的」是连接词相连，守护（REVIEW_b12f1df M-16）不再放行 |
| h5 `session-gate.test.tsx` | 该企业微信用户尚未对应到员工，请联系管理员 | 企业微信用户尚未对应到员工，请联系管理员 | 句首的单字「该」 |
| h5 `session-gate.test.tsx` | 登录成功，但登录状态没有保存下来，请重新登录 | 登录成功，登录状态没有保存下来，请重新登录 | 句首的单字「但」 |
| web `sync-runs-page.test.tsx` | 该同步记录当前不是被中止状态，无法放行 | 这个同步记录当前不是被中止状态，无法放行 | 句首的单字「该」 |

## 3. 失败信息的形状（改写）

| 位置 | 原断言 | 现断言 | 理由 |
|---|---|---|---|
| `failureOf('boom')`（原 web `lib.test.ts`，现 core `failure.test.ts`） | `{ status, code, body, malformedAnswer }` | `{ status, code, legacyLoginUrl, malformedAnswer }` | 错误体改为按 ProblemDetail 解析，不再把原始 `body` 暴露给页面；登录页需要的 `legacyLoginUrl` 单独给出 |
| `describeFailure(error, 模块文案表)`（同上） | 「优先使用模块给出的业务错误码文案，其次按状态码」 | 文案表合并为全体错误码的一张表，`describeFailure(error)` 不再有第二个参数 | REVIEW_b12f1df M-14：错误码文案表按 `Record<ErrorCode, …>` 穷尽 |
| `describeFailure` 对 `ResponseShapeError`、网络错误 | 用鸭子类型的对象 | 用真实的 `ResponseShapeError`、`ApiError`、`TypeError` | 错误类现在可以直接引用，不再需要鸭子类型 |

## 4. HTTP 客户端测试的错误体与 code（改写）

| 位置 | 原断言 | 现断言 | 理由 |
|---|---|---|---|
| `http.test.ts` 401 | 错误体 `{ errorCode: 'X' }`，`code` 为 `'X'` | 错误体为合法的 ProblemDetail，`code` 为 `PLATFORM_WEB_UNAUTHENTICATED` | 错误响应按 ProblemDetail 校验，任意字符串不再被当作错误码 |
| `http.test.ts` 409 | 错误体 `{ errorCode, message }` | 合法的 ProblemDetail | 同上 |
| `http.test.ts` | 错误体 `{ outcome: … }` 也能读出 code | 不是 ProblemDetail 的错误体只剩状态码，`code` 与 `problem` 均为 undefined | 同上；旧的 `outcome` 形状随契约取消 |
| `http.test.ts` | 读过资源后，版本由客户端全局记住（同资源的 `:action` 共用） | 版本随读到的数据走（`versionOf(data)`），由调用方显式传 `ifMatch`；没有全局记忆 | C-H1：隐式版本缓存会把旧版本发给别的数据 |
| `ApiError.malformedProblem` 的三条断言 | 错误体不合规时为真 | 删除，该属性已删除 | C-M9：没有调用方 |
| 错误码未知时 | 错误体不合规，丢弃 | 保留整个错误体（含 `legacyLoginUrl`），`code` 为 undefined，并上报一次 | C-M1 |

## 5. 其他被改写或删除的断言

| 位置 | 变化 | 理由 |
|---|---|---|
| core `pure.test.ts`（原 web `lib.test.ts`）`safeExternalUrl('http://old.example.com/')` | 原断言放行 `http:`，现归入被拒绝的一组 | C-Nit：外部链接只允许 https |
| `paging.test.ts` | 删除 `collectPages` 的两条测试 | `collectPages` 没有调用方，已删除（C-M9） |
| core `failure.test.ts` | 删除「文案表的键等于 `ERROR_CODES`」 | `ERROR_CODES` 已删除；穷尽性由 `Record<ErrorCode, …>` 的类型检查和 `expectTypeOf` 保证 |
| web `numbering-rules-page.test.tsx` 422 | 服务端 body 为字符串，页面显示「…不符合规则（原文）」 | 服务端为 ProblemDetail，页面只显示固定文案，不展示服务端的英文说明 | 界面文案由前端用语守护控制 |
| web/h5 `inbox-page.test.tsx` | 夹具里的标题由服务端给出（「工时单审批 TS…」） | 夹具给 `processKey`、`documentId`，标题由前端拼 | REVIEW_b12f1df M-16：待办标题按 `processKey` 映射 |
| web `sync-runs-page.test.tsx` 409 | 错误体 `{ outcome: 'NOT_HELD' }` | ProblemDetail，`ORGANIZATION_WORKFORCE_SYNC_RUN_TRANSITION_ILLEGAL` | 同第 4 节，旧 `outcome` 取消 |
| web `task-detail-page.test.tsx`「办理时别人已经改过任务（412）」 | 期望只读取一次任务 | 期望 412 之后立即再读一次，拿到新版本 | C-M5：412 后让对应查询失效 |
| 所有页面测试的桩 | 按「地址包含片段」匹配，先到先得 | 按路径末尾在段边界精确匹配 | C-Nit：避免 `approval-tasks` 误匹配 `approval-tasks/t-1` |
| `businessToday` 的断言（原在工时单模型测试与工作日历模型测试各一份） | 迁移到 `packages/core/src/business-day.test.ts`：上海时间的跨日、跨年各一条；工作日历模型测试里的那一条随函数一起移走，没有删减 | 函数移到 core（C-Nit），断言随函数走 |
| web `work-calendar-page.test.tsx`「保存时日历已被别人修改（412）」 | 期望页面上的修改保留 | 期望重新读到不同内容后草稿被丢弃并提示「已载入最新内容，你的修改未保存」；内容相同只是版本变了时草稿保留（新增一条） | C-H1 的守护：版本变化时不能带着旧基础保存 |
| 工时单页面的全部测试与 `timesheet.test.ts` | 删除 | 契约 D-1a-107：1a 不对外暴露工时提交，页面已撤掉 |

## 6. 已恢复的断言（C-H4）

- web `model/paths.test.ts`、h5 `model/paths.test.ts`：`taskPath` 对 `a/b c` 的编码。
- core `approval/model.test.ts`：新旧待办编号相同时 `inboxRowId` 不同。
