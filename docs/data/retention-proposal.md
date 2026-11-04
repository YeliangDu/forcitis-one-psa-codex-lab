# 已建成表的保留期与匿名化建议

> 冻结（2026-11-04，#1263）：2026-10-16 的保留期建议，不再更新；现行保留期见 ADR-0043、ADR-0061。

- 日期：2026-10-16 · 会话：one-data。起因：已建表审查（`BASELINE_TABLE_REVIEW.md` §7 第 1 项）。用户答「按 one-data 建议」（经 one-coordinator 转达，intent 随 #319 合入）。
- 依据：个人信息保护法第十九条（保存期限为实现处理目的所必需的最短时间）、ADR-0043 R-2（含 `PI` 的表保留期不得永久，除非有用户决定出处）、GUIDING_PRINCIPLES §6「Phase 1a 表的保留与归档」（用户 2026-10-14「都认可」）、审批结论与审计共用 10 年合规保留（GUIDING_PRINCIPLES §6 审计行，用户 2026-10-14「不算，按 10 年」）。
- 范围：法律口径（个人信息保护法、会计档案管理办法对个别表的适用）以法务确认为准（ADR-0043 U-11）；本表是用户认可的**设计口径**，不是法律意见。
- 落点：每条写进对应修复卡（注册表 `retention` 与清理作业）；涉 ADR 或 GUIDING_PRINCIPLES §6 补行的，交 one-architecture 与 one-coordinator。

## 1. 建议

「现注册表」取 `backend/architecture/data-access-registry.yaml`（main）。**清理 = 作业到期后物理删除或匿名化**；匿名化指把能识别到人的列置空、保留业务与统计所需的列。

| 表 | 现注册表 | 建议保留期 | 到期处理 | 理由 | 落点 |
|---|---|---|---|---|---|
| organization_workforce.employees（姓名、工号） | `CURRENT_STATE` | 随被其引用的业务单据与审计存续，不单独清理 | 不匿名化 | 单据、审计要显示历史姓名与工号；清空会让已过账单据读不出经办人 | 注册表登记「随引用存续」，理由写入 `CURRENT_STATE` 的说明 |
| employees（登录名、邮箱、企业微信 ID） | 同上 | 离职满 180 天 | 置空，记审计 | 离职后只用于交接与补单；不再需要账号标识（PIPL 第十九条最短必要） | workforce 卡（离职清除作业，随 W2 DA-7 版本化一并设计）；ADR-0043 实施 PR 的 R-2 gap 引用本条 |
| organization_workforce.assignment_versions 等个人任职史版本表 | `PERMANENT` | 员工离职满 10 年 | 删除该员工的任职版本行，保留部门级统计 | 劳动争议、薪酬审计的追溯期；与审批、审计的 10 年同口径；含个人信息，不得永久（R-2） | workforce 卡；W2 版本化 |
| platform_approval.approval_requests / start_rounds / approval_tasks / approval_task_records | `BUSINESS_RECORD`（无期限） | 10 年 | 归档到对象存储（现有 `platform.approval.process.archive` 的口径），在线行按期删除 | 用户已定审批结论按 10 年（GUIDING_PRINCIPLES §6 审计行）；四表含办理人 ID 与手填原因 | approval 卡；GUIDING_PRINCIPLES §6 补一行 |
| platform_approval.process_archive_skips | `PERMANENT`（注册表另有一行 `3y`，同表两行，须去重） | 引擎历史清理完成后再留 1 年 | 删除 | 只用于排查归档跳过原因；失败文本已改不含异常原文（DATA-REPAIR-approval） | approval 卡 |
| platform_integration.outbound_deliveries | `90d` | 成功（SENT）与跳过（SKIPPED）90 天；失败（FAILED、DEAD_LETTER，含已人工处理过、`handled_note` 非空者）1 年；待发（PENDING）不删 | 删除 | 与 GUIDING_PRINCIPLES §6 原文一致；注册表现为单档 90 天，改两档 | approval 卡（同批）；注册表改两档 |
| platform_identity.authorization_requests | `PERMANENT` | 10 年 | 归档到对象存储，在线按期删除 | 授权申请是权责证据，对应审计 10 年；只含用户 ID 与原因 | identity 卡；GUIDING_PRINCIPLES §6 补一行 |
| platform_idempotency.idempotent_responses | `EXPIRY`（无期限值） | 24 小时 | 删除 | 重放窗口只需覆盖幂等键有效期；应答正文可能含个人信息，越短越好；另限长度、限接口（DATA-REPAIR-timesheet-audit TB） | timesheet-audit 卡 B 部分；注册表写明 `24h` |
| platform_audit.audit_archive_batches | `PERMANENT` | 保留至其所含最后一条审计满 10 年 | 删除批次登记 | 批次登记只含边界与摘要，不含个人信息，永久也无合规风险；取与归档内容同寿命更干净 | timesheet-audit 卡 B 部分（随 ADR-0048 分区归档一起改） |
| organization_workforce.sync_run_held_separations | `1y` | 随所属同步运行：运行保留 2 年，被拦截的运行放行后再留 1 年 | `ON DELETE CASCADE` 随运行删除 | 实际 FK 级联随 `sync_runs`（2 年）删除，注册表 1 年与实际不符；改注册表，不再给作业加单独删除 | workforce 卡；GUIDING_PRINCIPLES §6 同步运行行补「待离职名单随运行」 |
| platform_integration.source_references（`source_id` 为人员标识的 `EMPLOYEE` 类） | `PERMANENT` | 与所对应员工的任职史同期：离职满 10 年 | 删除该对照行，同批清理迁入对账登记 | 来源人员标识是个人信息（去标识化仍是个人信息，ADR-0043 第 4 项第 2 条）；需与自有对象同寿命；其他类别（组织单元、部门、法人）非个人信息，维持永久 | approval 卡（`source_references` 在 platform_integration）；ADR-0043 R-2 的「用户决定出处」即本条 |
| platform_identity.user_accounts.last_login_address | `PERMANENT`（整表） | 在职只留最近一次；账号停用或离职起 30 天内清除 | 置空 | U-12 已定「账号停用或离职后清除」，期限由本条补 | identity 卡 ID-06；ADR-0043 实施 PR |
| platform_identity.browser_identifiers | `EXPIRY`（天数待核） | 退役后 30 天 | 删除 | 只用于重放检测；不含明文个人信息 | identity 卡；保留天数以代码值为准（待核 `JdbcBrowserIdentifiers`），不一致则改代码 |
| platform_identity.sso_ticket_uses | `EXPIRY` | 票据到期后 7 天（GUIDING_PRINCIPLES §6 原文） | 删除 | 用户 2026-10-14 已认可的口径；代码现为「使用后保留 replayWindow（默认 7 天）」，两者不同，以 GUIDING 为准 | identity 卡（改代码对齐） |
| cost_timesheet.timesheets | `BUSINESS_RECORD`（无期限） | 10 年 | 归档到对象存储，在线按期删除 | 与审批结论同口径；已过账的人工成本事实随 `accounting.ledger` 的会计档案存续，不依赖本表 | timesheet-audit 卡 A 部分（TS-UNIQ 同批登记）；若财务认定工时单为会计档案原始凭证，改 30 年，待财务确认 |
| cost_timesheet.consumed_events / event_publication_archive | 90 天 / 30 天 | 维持 | 维持 | 技术幂等记录，用户已认可 | 无改动 |
| organization_attendance.work_calendar_* | `PERMANENT` | 维持 | 维持 | 非个人信息，配置型版本 | 无改动 |

## 2. 须同步改的规范文字（交 one-architecture / one-coordinator）

1. GUIDING_PRINCIPLES §6 表补行：审批四表 10 年、授权申请 10 年、工时单 10 年、同步运行行补「待离职名单随运行」、`source_references` 人员类 10 年。
2. ADR-0043 R-2 的「用户决定出处」：`source_references`、`assignment_versions`、`authorization_requests` 三处的有限期限以本表为出处（intent 随 #319）。
3. `data-access-registry.yaml` 的 `retention` 条目随各修复卡改；`process_archive_skips` 的重复两行去重。

## 3. 未核实与待财务 / 法务

- `browser_identifiers` 与 `sso_ticket_uses` 的代码保留值；
- 工时单是否属会计档案原始凭证（财务）；
- 个人信息保护法对「离职后保留姓名与工号」的合规口径（ADR-0043 U-11，法务）。
