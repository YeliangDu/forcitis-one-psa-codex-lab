## 数据设计审查
- 审查：one-data，2026-10-16，依据 `DATA-REPAIR-workforce.md` 的「数据设计（PR-8：WF-04、05、09–12）」一节（分支 `claude/one-1a-next-5-data-pr8` @50a265dd；基线 main d150fc33；不含 `sync_*`、WF-01/02/03/06/07/08/13/14）
- 核对：我抽读了 `SnapshotReconciler` 对负责人与区域应收负责人置空、`EmployeeChecks.isolation`、`JdbcOrgSnapshotRepository` 的写入次序与 `revoked_at` 的 `case` 写法，与卡一致；版本表五条外键须 `DEFERRABLE INITIALLY DEFERRED`（部门版本先于员工入库）的分析成立
- 判定：
  - O4、O8：通过。WF-04 `ck_legal_entity_versions_code_format`（与主表同一正则，`$` 与 Java `matches()` 等价）；WF-05 `ck_org_units_revoked_at`（`(status = 'REVOKED') = (revoked_at IS NOT NULL)`，插入、冲突更新、复活三条路径相容）；WF-11 十五条非空白与长度 CHECK（只落有证据的：法人名称 ≤ 100、其余只「非空白」）；WF-12 空更正 CHECK（两列 NOT NULL，用 `<>`）。
  - O10、B4：通过。WF-09 五条延迟外键并撤注册表五条 `fk_exceptions`（`org_unit_versions` 补 `references` 的 `regional_receivables_manager_id`）、五个前导索引；WF-12 期间表与更正表指向聚合根的四个外键（目标为「仅由 uuid 列组成的唯一约束」，G-DDL1 已放行，根总在子行之前，立即检查）。
  - C1、C2、C4：通过。WF-10 读卡确认（OU-3(a)、LE-2、X-3、B7-2）以注册表 YAML 注释登记，不新增键；LE-2 补「根表与开放版本一致」测试。B7-2 五列按 one-architecture 判定保留，不加使用方、不改取值。
  - O12、T4：通过（基线开放期，改基线，附预期 diff：15 条 CHECK、9 个外键、5 个索引，随库重建）。
- 裁定（九个请裁点；与卡文不同处以本记录为准）：
  1. 验收目录夹具：发 Acceptance-Ruling——`AttributionSubjectColumnsIT.java:128-129` 两个正例前补建根归属，只改夹具、不改断言，PR 单列理由。
  2. `short_name` 空白：选 A，`RefreshShortNames` 把空白规范为 NULL，`OrgSnapshotSyncIntegrationTest:420-421` 夹具与断言原样。
  3. 无证据的长度上限：同意不落；请 one-ops 按 ADR-0037 对 Seeyon 视图取各来源列的 `MAX(LEN())`（只出聚合、登记一行），另起后续 PR 落长度。`correction_reason` 的上限取 **200**（与 `ReleaseSyncRun`、`ForceUserLogout` 的人填原因同口径，1 至 200 字），先在 `CorrectLegalEntityAttribution` 入口校验，再加库内 CHECK；放进同一后续 PR。
  4. 版本表外键前导索引：加。
  5. `ck_employees_email_address`：按 R-2a 目标列名起名。
  6. WF-10 登记形式：以 YAML 注释登记，同意；注册表结构化字段按 DG-0 另建，建成后迁入。
  7. WF-12 并存外键：保留指向 `org_units` / `employees` 的既有外键，同时新增指向根的外键，同意。
  8. 卡 §3 允许文件清单：按此执行并修订 §3（补 `positions`、`employees`、`legal_entities`、期间表、更正表；本节只加约束、不改列）。
  9. 不在域层 `Position`、`OrgUnit`、`Employee` 构造加非空白校验：同意（来源适配器已规范化，库作最后防线）。
- 豁免与待用户决定：无（WF-09 例外撤销已由 one-coordinator 按 §5 接受）。
- 结论：通过
- 数据设计：通过（one-data，2026-10-16，依据 `DATA-REPAIR-workforce.md` PR-8 节 @50a265dd，含上述裁定）
