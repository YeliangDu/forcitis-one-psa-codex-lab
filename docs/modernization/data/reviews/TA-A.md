## 数据设计审查
- 审查：one-data，2026-10-16，依据 `docs/modernization/phase1a/cards/TA-A.md`（分支 `claude/one-1a-par-ta` @3dd1709c；`DATA-REPAIR-timesheet-audit` A 部分，A1 / A2 / A3 三个 PR 各一节「数据设计」）
- 核对：A2 的状态 × 轮次 × 提交日 × 版本 ID 对照表我逐行过了两条 CHECK（含迁入单：状态直接迁入、轮次 0、提交日空、版本 ID 空，两条为真；撤销审批回到草稿保留轮次与提交日，第一条非零与非空为真）；A1 的 `ISODOW` 对 `date` 不受会话时区影响；A3 只改注册表与注释，无 DDL 以外差异
- 判定：
  - **A1（TA-02）通过**：`ck_work_calendar_days_make_up_weekend`（`day_kind <> 'MAKE_UP_WORKDAY' OR EXTRACT(ISODOW FROM calendar_date) IN (6, 7)`）；规则只依赖本行两列，聚合构造器已在写库前校验，旧日历导入经聚合校验，无迁入风险。
  - **A2（TA-01 + T-8）通过**：`ck_timesheets_round_submitted`（`(round_number = 0) = (submitted_date IS NULL)`）、`ck_timesheets_round_after_submit`（补集：`status = 'DRAFT' OR round_number >= 1 OR numbering_rule_version_id IS NULL`，版本 ID 空 = 迁入单，不枚举状态）；三列注释草稿通过，`legal_entity_id` 注释的「创建后不改」由实现者核对 `Timesheet.revise` 后定去留，不属实则去掉该半句。
  - **A3（TA-03 + CE-1）通过**：`timesheets` 保留 `BUSINESS_RECORD`，`#` 注释写 owner、10 年口径、到期锚（工时迁入入口前归档作业须先建成），不加 gap；`consumed_events` 注册表改 `INSERT_DELETE`，表注释与 `unversioned-table-writes.txt:21` 改成与实现一致。
- 裁定（卡 §13 的需决定点）：
  1. **单 PR 内先验收（红）后 DDL（绿）：不是对门禁 §1.2 的豁免**。§1.2 的「表结构 PR 先合并」是为了让改表先于**业务代码**；本批三个 PR 只改约束、注释、注册表，没有业务代码，DDL 本身就是实现，一个 PR 里按「签发 → 测试代理验收（红）→ DDL（绿）」提交即满足「签发先于验收与实现」。凡修复卡只含约束、注释、登记、不含业务代码的，都按此执行；含业务代码的仍按表结构 PR 先行。
  2. 措辞：`unversioned-table-writes.txt:21` 采用「idempotency record of handled events: rows are inserted and never updated, the unique key is the check; the retention job deletes old rows (see the purge line)」；`consumed_events` 表注释采用「已处理的事件：幂等记录；粒度：每个消费方每个事件标识一行，只插入、不改写，由保留期清理作业删除过期行（保留 90 天）；消费方按事件标识去重，事件至少投递一次」。
  3. A3 的 `#` 注释放在 `timesheets` **条目上一行**（与注册表既有写法与 PR-3 先例一致），不放行尾。
  4. 决定点 6（迁入单的轮次与提交日口径）：E3 立项时由 one-data 回看；本卡不预设。
- 豁免与待用户决定：无（工时单是否会计档案原始凭证待财务，转归档作业卡）。
- 结论：通过
- 数据设计：通过（one-data，2026-10-16，依据 `TA-A.md` @3dd1709c，A1、A2、A3 三个 PR 均含上述裁定）
