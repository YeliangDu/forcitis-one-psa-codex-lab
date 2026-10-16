## 数据设计审查
- 审查：one-data，2026-10-16，依据 `DATA-REPAIR-timesheet-audit.md` 的「数据设计（PR-3：TB-06、TB-08）」一节（分支 `claude/one-1a-next-5-data-pr3` @a7e34932；基线为 PR-1 合入后的 main）
- 核对：TB-08 的写入点表（15 处 `withTrace`）与「方式非空而结果为空的有 4 处、地址为空而结果非空的有 2 处」两项我抽读了 `AuditEntry` 与 `LoginAdmission` 调用点，与卡一致；`sha256_hash` 取自 `HexFormat.of().formatHex`（小写 64 位），格式 CHECK 与实际一致
- 判定：
  - O3、O4、O7、O8：通过。`audit_archive_batches` 新增 `uk_audit_archive_batches_first_entry_id`、`uk_audit_archive_batches_object_key`、`ck_audit_archive_batches_sha256_hash`；`audit_entries` 新增 `ck_audit_entries_login_method_outcome`（`login_method_kind IS NULL OR outcome_kind IS NOT NULL`）；`AuditEntry` 构造器加同一断言。
  - B5：通过。卡「三列同空同非空」的前提经核不成立，只落单向一条，不把应用的审计清单抄进 DDL。
  - C1：通过。三条列注释按核实结果纠正；`RequestClientOrigin` 在无请求上下文返回字面量 `"unknown"`，由 PR-1 的「不像地址则置空并 ALERT」兜住。
  - O12、T4：通过（基线开放期，附预期 diff，随库重建）。
- 裁定（六个请裁点，与卡文不同处以本记录为准）：
  1. 三列不做成对约束、不按 `subject_kind` 限定：同意。
  2. 只落「方式非空 ⇒ 结果非空」一条：同意。
  3. 「地址非空 ⇒ 结果非空」不落（需改验收夹具，且夹具行是构造数据）：同意不落。
  4. 触发器统一命名：**转卡**，不在本 PR。本会话起草 `NAMING_STANDARD` §2.4 触发器命名条款（拟按基线多数写法 `tg_<表名>_<动作>`），交 one-coordinator 排改全库的小卡（排在 R-2a 之后）；AB-4 的触发器部分关闭为「转卡」。
  5. 注册表用 `#` 注释表达「区间不重叠、无缺口由对账承担」：可接受（注册表结构化字段按 DG-0 另建，建成后迁入）；注释须诚实写明已摘除月份的缺口库内无法再核验。
  6. 批次表「首末条同一 UTC 月」CHECK：不落，同意（作业与摘除函数已按月核对，PR-1 写明批次表列与约束不变）。
- 豁免与待用户决定：无（`retention` 与 GUIDING §6 的一致性随保留期事项，已按 `RETENTION_PROPOSAL.md`）。
- 结论：通过
- 数据设计：通过（one-data，2026-10-16，依据 `DATA-REPAIR-timesheet-audit.md` PR-3 节 @a7e34932，含上述裁定）
