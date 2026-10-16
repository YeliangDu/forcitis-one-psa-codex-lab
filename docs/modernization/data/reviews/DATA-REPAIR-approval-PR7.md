## 数据设计审查（签发已撤回：one-architecture 更正 AP-14，ADR-0030 U-3 保留凭证号财务确认；设计节改后以新提交号重审，本记录作废）
- 审查：one-data，2026-10-16，依据 `DATA-REPAIR-approval.md` 的「数据设计（PR-7：AP-09、11–17）」一节（分支 `claude/one-1a-next-5-data-pr7` @cc58f053；基线 main 4d0b7c34；AP-10、AP-12、AP-18 不含）
- 核对：AP-09 的状态取值全集与唯一置 CONFIRMED 的路径、AP-14 的 `document_type` 取值出现处与删财务确认牵出的接口、权限、事件、错误码、前端、契约清单，我抽读了 `NumberingRule`、迁移基线与引用点，与卡一致；财务确认全链确实远大于「删两列」
- 判定（PR-7a，即本次签发范围）：
  - O3、O4、O5：通过。AP-09 `ck_numbering_rule_versions_confirmed`（单向：`status <> 'CONFIRMED' OR confirmed_by IS NOT NULL`）、AP-16 办理记录两条成对 CHECK（`REASSIGNED` 的 `on_behalf_of_id` 可空，已核）、AP-17 `numbering_counters` 三条 CHECK（`period_key` 正则、`last_value` BETWEEN 1 AND 999999999999、`document_type`）。
  - O8、C4：通过。AP-15 `ck_approval_requests_summary_text`（`char_length BETWEEN 1 AND 200 AND ~ '\S'`，与 `ApprovalSummary` 同口径）；`summary_text` 登记 `INTERNAL`、不加 `PI`（唯一写入点为单据编号、工作日、工时，不含个人信息；未来写入方扩内容须重判），C4 允许写入清单同卡。
  - AP-11：通过。删 `numbering_rules` 五个停用列（`pattern`、`reset_policy`、`start_value`、`confirmation_status`、`is_active`），种子 `event_count` 写 0，`NumberingVersionMigrationIT` 的「旧四列仍在」断言按用户的「删列」反转为「五列均不存在」，在 PR 与报告单列理由（CLAUDE.md 第 5 条）。
  - B7、AP-14（只做取值收窄这一半）：见裁定 1。
  - O12、T4：通过（基线开放期，改基线，附预期 diff，随库重建）。
- 裁定（七个请裁点；与卡文不同处以本记录为准）：
  1. **拆分**：AP-14 第 2 步（删财务确认全链）**不进本 PR**。该链的存废偏离已采纳的 ADR-0030 第 2 行、D-1a-157（`MODERNIZATION_BRIEF.md:618`）与 ADR-0010 第 5 项，偏离已采纳 ADR 须用户决定（门禁 §5），已登记收件箱。本 PR（PR-7a）做：AP-09、AP-11、AP-13、AP-15、AP-16、AP-17，以及 AP-14 的「`QUOTE` 取值收窄」；**`JOURNAL_VOUCHER` 与财务确认两列、两个确认人 CHECK、触发器分支、相关接口权限事件错误码前端契约全部保留**，`document_type` 的 CHECK（审批、编号规则、计数器）取 `('TIMESHEET', 'JOURNAL_VOUCHER')`；`QUOTE` 一律改 `TIMESHEET`，种子留 TIMESHEET 与 JOURNAL_VOUCHER 两行。`JOURNAL_VOUCHER` 属未建的核算模块，B7 的未建模块取值在本 PR 内暂留，由 PR-7b 随用户决定处理，不得在 PR-7a 里为过测试删财务确认用例。
  2. AP-09 用**单向**写法（`status <> 'CONFIRMED' OR confirmed_by IS NOT NULL`）。双向写法在财务确认存废定后另改。
  3. `ConfirmationKind` 与事件 `NumberingRuleConfirmationGiven.kind`：本 PR 不动（随 PR-7b）。
  4. `summary_text` 登记 `INTERNAL`、不加 `PI`：接受。
  5. `last_value` 上界 999999999999：追加，接受（`{seq:12}` 的容量）。
  6. AP-13：`source_references.lineage` 照卡改；`retention` 先保留 `PERMANENT`，在条目上加 `#` 注释「人员类 `source_id` 按 `RETENTION_PROPOSAL.md` 为员工离职满 10 年，清理作业另卡」（清理作业属功能补做，不在本 PR；R-2 守护建成后改为 gap）；`outbound_deliveries` 两档由实施者读 `OutboundDeliveryPurgeJob` 与状态枚举后落，状态值以代码为准，读不清就报回，不猜。
  7. 文字修订：D-1a-157、ADR-0030 第 2 行、ADR-0010 第 5 项的修订**不在本 PR**，随用户对财务确认存废的答复，由 one-architecture 起草；本 PR 只改 `document_type` 收窄涉及的基线注释。
- 夹具：`QUOTE → TIMESHEET`（含期望号码 `QT-` 改 `TS-`）按卡的总表做；验收目录的机械替换须走 Acceptance-Ruling，不放宽、不删业务断言；凡「两种单据并存才有意义」的断言逐个确认，不得为过而放宽。
- 豁免与待用户决定：财务确认全链存废（已登记收件箱）。
- 结论：通过（范围为 PR-7a）
- 数据设计：**未签发**（原依据 @cc58f053 的签发已撤回，等新提交号）
