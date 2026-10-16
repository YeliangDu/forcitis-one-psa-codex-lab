## 数据设计审查
- 审查：one-data，2026-10-16，依据 `DATA-REPAIR-timesheet-audit.md` TB-07 之后的「数据设计（PR-1：TB-07 + TB-09 + TB-05）」一节（分支 `claude/one-1a-next-5-data-pr1` @599d0041），对照 ADR-0048 决定 1–7 与守护 1–5
- 判定：
  - B1、B2、B5、B7、B8：通过。审计表出处与聚合不变（技术类审计表，服务审计用例）。
  - O2、O3：通过。主键 `(id, created_at)`（ADR-0048 唯一例外，形状由守护锁定）、去重键 `(event_id, created_at)`，`id` 仍应用侧 UUIDv7，引用只用 `id`，任何表不得外键指向本表。
  - O4、O7：通过。行级 `BEFORE UPDATE OR DELETE` 建父表自动克隆；语句级 `BEFORE TRUNCATE` 由建分区函数在每个分区各建一条；验收逐分区含直接对分区的 UPDATE、DELETE、TRUNCATE。
  - O8：通过。`created_at` 去 `DEFAULT now()`（TB-09）；TB-05 具名 CHECK `BETWEEN 1 AND 2000` 与 `AuditEntry` 上限一致，可空列空值不违约。
  - O10：通过。四个索引含 `id DESC` 兜底，`action text_pattern_ops` 的前缀查询改写以 EXPLAIN 为证写进 PR。
  - O11：通过。按月 RANGE 分区，保留 3 年在线、10 年归档，建表时定分区键。
  - O12：通过。基线开放期改基线，附预期 diff；两函数、`REVOKE`、新角色 `one_*_audit_maintainer` 的可执行脚本待 one-ops 同步（PR 中列为待 one-ops）。
  - C3、C5：通过。注册表登记分区键、粒度、预建作业、删除方式、保留口径。
  - E1：通过（载荷不变）。
  - T3、T4：通过（基线开放期；随库重建）。
  - ADR-0048 第 7 项迟到事件必答：通过。无分区则插入报错、事件处理失败、事件发布保持未完成并告警；已归档月份照常写入并由对账告警；不建兜底分区、不改写时刻。
- 裁定（五个请裁点，与卡文不同处以本记录为准）：
  1. UTC 月边界与分区名 `audit_entries_pYYYYMM`：认可。
  2. 初建 6 个月、预建 3 个月：认可；预建作业失败告警与次月存在性核对保留。
  3. 建分区函数拒绝条件：认可，不再加「禁止早于最早分区」（迁入或补写需要时由归档位置兜底）。
  4. 维护角色删除校验口径：认可（分区存在、整月在归档位置之内、行数等于归档批次 `entry_count` 之和）；运行角色无 EXECUTE。
  5. `source_address` 上限：**改为 64，并加应用侧保护**（与 identity 卡 ID-12 同口径）：库内 `char_length(source_address) BETWEEN 1 AND 64`；应用写入前若长度 > 64 或不像地址，该条审计的地址置空、打 ALERT，**审计写入不得因此失败**（审计写入失败会让登录与越权拒绝失去记录）；不静默截断；`getRemoteAddr()` 取值来源核对（`SessionGuardFilter.java:91`，AE-7）写进 PR。
- 另：运维命令（DR-TB01-FU1）退出码——员工不存在、非 ID、缺、相同都走 `DomainException`、退出码 1，沿用 `PLATFORM_IDENTITY_OPERATION_WITNESS_REQUIRED`（消息改「须为两个不同的已存在员工」），存在性用 `OrgDirectory.findEmployee`，不要求在职；通过。是否要求在职是后续政策收紧项，不在本卡。
- 豁免与待用户决定：无。
- 结论：通过
- 数据设计：通过（one-data，2026-10-16，依据 `DATA-REPAIR-timesheet-audit.md` PR-1 节 @599d0041，含上述裁定）
