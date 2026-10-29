## 数据设计审查
- 审查：one-data，2026-10-16，依据 `docs/modernization/phase1a/cards/TS-UNIQ.md`（分支 `claude/one-1a-par-ts-uniq` @40edf239）
- 核对：卡对基线、状态机、事件目录、注册表的陈述我抽查了状态机 `MACHINE`、`CHECK` 五值、事件五个与转换的对应、唯一约束与旧索引，与卡一致
- 判定：
  - B0–B2、B4、B5、B7、B8：通过。B1 出处为 OBJ-66 的单据行；撤回 = `SUBMITTED → DRAFT`、没有「撤回」状态，`CHECK` 不改，与事件改名一致。
  - **B3 / B3a：通过**。三方（`CHECK`、事件目录、状态机、Java 枚举）一致；信息图 OBJ-66 的「已撤回、审批中、已过账」与用户 2026-10-16 决定不一致，是信息图过期，不由本卡改，交 one-architecture 改写（见裁定 4）；按门禁 §1.3，信息图改动不触发签发失效。
  - O3、O4、O5、O7、O8、O10、O10a：通过。业务键 `(employee_id, work_date)` 写成业务上「每人每个工作日至多一张」并有出处；删冗余 `ix_timesheets_employee_id`；`REJECTED → SUBMITTED` 加转换。
  - O12、T4：通过（基线开放期，改基线，附预期 diff，随库重建）。
  - C1、C2、C4、C5：通过。`employee_id`、`numbering_rule_version_id` 为代理键，不登记。
  - **C3：不适用**。本卡不改保留期；保留期 10 年的登记与归档作业另归 `DATA-REPAIR-timesheet-audit` TA-03（见裁定 3）。
  - O15：通过（ADR-0033 第 1 项第 5 条的版本 ID 列已含）。
  - T3：迁入违约取证（交 one-ops）是前置并行事项，结果不为 0 时的处理办法由用户或 one-rules 定，不阻塞本卡。
- 裁定（回答卡 §13 的需决定点；与卡文不同处以本记录为准）：
  1. **`numbering_rule_version_id` 改为可空**（不是 `NOT NULL`），列注释写「为空表示迁入单据（原编号作展示号，未经本系统发号，ADR-0036 第 4 条）」；新建工时单由应用保证必填（值对象构造处非空）。理由：迁入的约 12 万张历史工时单没有发号版本，`NOT NULL` 会在迁入阶段逼出第二次改表（CS-1.9 当期做对）；同时解决「表结构 PR 怎样保持 CI 绿」——新增可空列，现有插入不受影响，**不需要**表结构 PR 带 Java 改动，也不需要顺序豁免、不需要实现 PR 再改 `NOT NULL`。预期 diff 的第一条据此改为「新增列 `numbering_rule_version_id uuid`（可空）及列注释」，`NOT NULL` 与「无默认值」两处删去。
  2. **`platform.numbering` 的 API 扩展**（`IssuedNumber` 带版本 ID）：先开 numbering 小卡，由 one-coordinator 指定负责会话；它是 TS-UNIQ **实现 PR** 的前置，不是表结构 PR 的前置。
  3. **`timesheets` 保留期登记与归档作业**：归 `DATA-REPAIR-timesheet-audit` A 部分新增条目 TA-03（本次已写入 #321）；GUIDING_PRINCIPLES §6 补行交 one-architecture。
  4. **信息图 OBJ-66、聚合草稿 INV-64 / N-10 的改写**：交 one-architecture（已另发）。
  5. 状态列内联 `CHECK` 具名：不含在本卡，留 G-D12 / R-2a，确认。
  6. 新错误码中文名「工作日期已有工时单」与 HTTP 409：交 one-glossary，用户无需决定。
- 豁免与待用户决定：无。
- 结论：通过
- 数据设计：通过（one-data，2026-10-16，依据 `TS-UNIQ.md` @40edf239，含上述裁定）
