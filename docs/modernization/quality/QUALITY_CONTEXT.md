# one-quality 速查（2026-10-15）

一行一项，过程放链接（SESSION_PLAYBOOK §2.1 第 13 条）。

## 在途

| 项 | 状态 | 出处 |
|---|---|---|
| FLAKY-SESS 会话清理与固定时钟不同源，整分钟偶发 401 | one-1a-next-3 按 H 修（会话时间基准例外） | 运行 38772041094 |
| Testcontainers 连接 PG 认证读无超时，CI 曾卡 54 分钟 | 待开卡（JDBC socketTimeout） | #197 运行 38746203340 |
| 覆盖率门槛是否恢复 | 待用户 | COORDINATOR_LEDGER「待用户」 |
| nightly-legacy-it 巡检 | 会话内定时每日 23:17（本机 CEST），7 天到期前续设；10-15 运行 38794167238 成功 11.5 分钟 | `gh run list --workflow nightly-legacy-it.yml` |
| 断言漂移漏报（中文路径、整删文件） | #243 已过 opus 复审入队；复审三条建议（引号路径告警、kept 断言、gpgsign）待合并后小 PR | #243 |
| 后端 CI 时长 | 不拆 runner 分片（同机 16 核，与 #237 降并发相悖）；改降每类开销：C1 共享容器 + 模板库试点，全量排在 P3b 合并后；C2 MSSQL 长类先跑待做 | 运行 38793746825：上下文 87 次 583 秒、PG 容器 79 个 209 秒 |
| PR 的 gitleaks 增量扫描 | 待用户：扫描只占 15–25 秒，作业慢在发布脚本自测（216–287 秒） | 调度认为可取消 |

## 不稳定测试台账

| 测试 | 现象 | 判断 | 处理 |
|---|---|---|---|
| `TimesheetApprovalScenarioIT.cancelledTimesheetIsADraft`（运行 38805349816） | 取消时查不到审批请求 | 偶发，等待只等事件、数据未提交（读代码推断） | 本分支改为条件等待（QUAL-009）；第二处同类等待（重提第 2 轮后读收件箱）未触发，再现再改 |
| `ApprovalAccountsIT.theInitiatorNeverApprovesTheirOwnTimesheet`（运行 38791490769，一次 401） | `expected 200 but was 401`，会话 cookie 被当未登录 | 无法判定，倾向偶发；后续约 8 次运行都通过；`SessionGuardFilter` 在主体非活跃时会作废会话，未找到触发原因 | 调度 2026-10-16 批复：先登记观察，**再出现一次就报调度派测试代理**（在验收目录，改动要带 Acceptance-Test-Author 或裁定）；现在不加诊断 |
