## 数据设计审查
- 审查：one-data，2026-10-16，依据 `DATA-REPAIR-approval.md` 的「数据设计（PR-7：AP-09、11、13–17）」一节（分支 `claude/one-1a-next-5-data-pr7` @1a74b10d；基线 main 4d0b7c34；AP-10、AP-12、AP-18 不含）。此前基于 @cc58f053 的签发已撤回（one-architecture 更正 AP-14），本记录取代之
- 核对：AP-14 更正后 numbering 三表与种子不动、`DocumentType` 枚举不动、财务确认链保留（ADR-0030 U-3）；`approval_requests` 现有直插与启动路径只有 `TIMESHEET`，收窄不影响运行，我抽读核对；AP-09 的状态取值全集与唯一置 CONFIRMED 的路径、AP-16 的写入点表与「`REASSIGNED` 的原处理人可空」同前次抽读
- 判定：
  - O3、O4、O5：通过。AP-09 单向 `ck_numbering_rule_versions_confirmed`；AP-16 办理记录两条成对 CHECK；AP-17 `numbering_counters` 三条 CHECK（`document_type` 取 `('QUOTE', 'TIMESHEET', 'JOURNAL_VOUCHER')`，与编号规则表同集，不随 AP-14 收窄）。
  - O8、C4：通过。AP-15 `ck_approval_requests_summary_text`（与 `ApprovalSummary` 同口径）；`summary_text` 登记 `INTERNAL`、不加 `PI`。
  - AP-11：通过。删 `numbering_rules` 五个停用列，种子 `event_count` 写 0；`NumberingVersionMigrationIT:34-37` 的「旧四列仍在」断言反转为「五列均不存在」，在 PR 与报告单列理由（CLAUDE.md 第 5 条）。
  - B7、AP-14：通过。`approval_requests.document_type` 收窄为具名 `ck_approval_requests_document_type`（`IN ('TIMESHEET')`），待报价等模块上线时由该模块 PR 同批追加（CS-1.8）；numbering 侧的 `QUOTE`、`JOURNAL_VOUCHER` 取值随 ADR-0030 U-3 保留，B7 在 numbering 侧的既有提示不在本 PR。
  - O12、T4：通过（基线开放期，改基线，附预期 diff，随库重建）。验收目录无需改动，已核。
- 裁定（四个请裁点）：
  1. `summary_text` 登记 `INTERNAL`、不加 `PI`：接受；未来写入方扩内容须重判，C4 允许写入清单写入卡。
  2. `last_value` 上界 999999999999：追加（`{seq:12}` 的容量）。
  3. AP-13：`source_references.lineage` 照卡改；`retention` 先保留 `PERMANENT`，条目上加 `#` 注释「人员类 `source_id` 按 `RETENTION_PROPOSAL.md` 为员工离职满 10 年，清理作业另卡；R-2 守护建成后改为 gap」；`outbound_deliveries` 两档由实施者读 `OutboundDeliveryPurgeJob` 与状态枚举后落，状态值以代码为准，读不清就报回，不猜。
  4. `ddl-naming-baseline.txt:22-24` 三行：以 `DdlNamingTest` 为准——补 CHECK 后若守护报「已修好却没删」就删，若不报就保留，不凭判断改清单。
- 豁免与待用户决定：无。
- 结论：通过
- 数据设计：通过（one-data，2026-10-16，依据 `DATA-REPAIR-approval.md` PR-7 节 @1a74b10d，含上述裁定）
