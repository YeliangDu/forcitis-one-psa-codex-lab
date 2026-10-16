## 数据设计审查
- 审查：one-data，2026-10-16（补审，门禁生效前在途卡），依据卡版本 3602aab1（分支 `claude/one-1a-next-4-r1b-p5`）
- 判定：补审报告 M-1、M-2、M-3 已闭合，one-data 自行核对：
  - M-1：`platform_audit` 基线已有 `ix_audit_entries_created_at_id (created_at, id)`，旧的 `created_at` 单列索引已去除。
  - M-2：`organization_workforce` 基线已有 `ix_sync_run_held_separations_employee_id`。
  - M-3：卡 §15.1 不变量 → 约束表与 §15.2 `subject_kind` ↔ 聚合类型对照已补，守护写明为 `EventCatalogueTest`。
  - L-2、L-5 已改基线；L-1、L-3、L-4、L-6 写入卡 §15.3。
- 豁免与待用户决定：无。
- 结论：通过
- 数据设计：通过（one-data，2026-10-16，依据 3602aab1）

未核对项：Java 守护测试的实现与运行结果（由 opus 复审与 CI 把关）。
