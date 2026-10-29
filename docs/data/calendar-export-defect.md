# C-6 日历导出缺陷：影响复核（one-data，2026-10-17）

起因：one-ops 发现 `c6_legacy_readonly_export.sql` STEP 3 的 HOLIDAY 条件写成 `workday = 0`，而老系统取值是 1 上班 / 2 休息，导出的 `legacy-calendar.csv` 因此没有任何法定假日，只有 65 个调休日。派工人：one-coordinator。本页只读仓库做影响复核，没有连任何数据库（生产取证不在本页范围）。

## 1. 缺陷确认与范围

- **确认**：STEP 3 的条件在两处，写法一样：`docs/ops/runbooks/sql/legacy-readonly-export.sql:46` 和实际执行用的 `phase1a/infra/runbooks/scripts/c6_relay.py:162`。周一至周五且 `workday = 0` 才标 HOLIDAY，而 `workday` 只有 1、2 两个值，条件永不成立；周末且 `workday = 1` 标 MAKE_UP_WORKDAY 的那一半条件是对的（65 个）。**两处都要改，只改 SQL 文件会让中转脚本继续出错。**
- **取值含义的证据**（来自读代码得到的规则卡与证据页，不是我对库的新取证）：`BUSINESS_RULES.md:1150`「Calendar workday = 1, rest day = 2」，`:672` 「`psa_calendar.workday` (1 = workday)」，`:1238` 例子「2026-10-01 (national holiday) is in the calendar with workday = 2」；`docs/architecture/proposals/delivery-resources-evidence.md:43` 「工作日 / 休息日 2 个取值」。再加 one-ops 的现象本身：只出 65 个调休日，与 `workday` 只取 1、2 一致。
- 正确条件：周一至周五且 `workday = 2` → HOLIDAY；周六日且 `workday = 1` → MAKE_UP_WORKDAY。

## 2. 谁用过这份 CSV，逐项结论

| 项 | 是否依赖 CSV 内容 | 结论 |
|---|---|---|
| `legacy-calendar.csv`（SHA256 `14ad4d12…be45c43`，新 ECS `/data/forcitis-one/import/c6/20261015/`）及 `manifest-roles-calendar.json` 里的日历条目 | 本身 | **作废，重做**：缺全部法定假日 |
| C-6 剧本「0 计数」：日历 4,018 天、无重复 | 只数行，与 `workday` 取值无关 | 仍有效 |
| C-6 STEP 3.1 完整性（每年 365 / 366 天） | 同上 | 仍有效；**但它是唯一的日历校验，只验行数，所以没拦住本缺陷**（见第 4 节补检查） |
| C-6 剧本表中「角色与日历…与 0 计数对账全部一致」 | 只对账行数 | 字面成立，但容易被读成「日历内容已核对」；需补一句 |
| **回放报告**（500 实例 / 561 节点，MATCH 474 等）、`TimesheetApproverReplay` | 否：回放只比较审批人集合，`TimesheetApproverReplay*.java` 与 `TIMESHEET_REPLAY.md` 中没有日历或时限引用（已 grep） | **不受影响，不用重做**；D-3 不因此改动 |
| 角色导出 `legacy-roles.json`、工时导出 `timesheet-export.json` | 否 | 不受影响 |
| PREDEPLOY C-6 行（`PREDEPLOY_CHECKLIST.md:53`「三类导出已完成」） | 日历一项不成立 | **改**：日历一项降为未完成 |
| 日历导入新系统（`PUT /api/v1/organization/work-calendars/{年}`，C-6 剧本第 18 行；TA-A 提到 one-ops 在 dev 重建后导入 2016–2026） | 若已导入，是用了缺假日的数据 | **待 one-ops 确认是否已导入 dev**（仓库里我没找到执行记录）。已导入则重做：重新导出后逐年 `PUT` 覆盖（剧本「回滚」一节写了有误可重新 PUT 该年）；未导入则只需新文件。生产导入在切换时做，尚未发生 |
| 依赖日历的新系统计算（审批时限 `DeadlinePlanner`、T+1 工作日截止、升级期限） | 导入后用 | dev 若已导入：这期间产生的审批时限把法定假日当工作日算；dev 无生产数据，重新导入后新单据即对，已产生的 dev 测试单据不用迁正 |
| TA-A 第 2a 项「旧日历 2016 至 2026 …导入的调休日均为周末，不受影响」 | 论据只覆盖调休日 | 结论仍成立（新增 CHECK 只约束调休日必须落周末）；但**重导出后的 HOLIDAY 行必须落周一至周五**，由 SQL 条件保证，`CalendarDay` 聚合入库时再校验一遍 |
| B-7 录入 2027 日历（手工清单） | 否 | 不受影响 |
| 我（one-data）此前的结论：TA-A 日历约束签发、已建表审查（工作日历表结构）、`BASELINE_TABLE_REVIEW` | 只看结构与约束，没有用到 CSV 内容 | 不受影响 |
| 证据页 `DELIVERY_RESOURCES_EVIDENCE.md`（4,018 行、2 个取值） | 来自表结构与行数 | 不受影响，且正好是本缺陷的旁证 |

## 3. 要重做的清单

1. **改两处条件**（one-ops）：`c6_legacy_readonly_export.sql:46` 与 `c6_relay.py:162`，HOLIDAY 条件改 `c.workday = 2`。
2. **补取值分布与内容检查**（one-ops，见第 4 节），使缺陷不再只靠行数核对。
3. **重新只读导出日历**（one-ops；生产只读取证，按 ADR-0037 登记一行，不交 Codex）：新文件、新 SHA256，旧 `legacy-calendar.csv` 在 ECS 上删除；明细不进会话与仓库。
4. **改文档**：C-6 剧本日历那一行写新 SHA 与新检查结果；`PREDEPLOY_CHECKLIST.md` C-6 行把日历一项改为「重导出后完成」。
5. **导入**：dev 若已导入，用新文件逐年 `PUT` 覆盖并逐年 `GET` 对条数与假日数；未导入则按剧本第 18 行做。
6. **不用重做**：回放及回放报告、角色导出、工时导出、0 计数、B-7、TA-A 签发、我的表结构审查。

## 4. 建议补的检查（防再犯，也回答「为什么 3.1 没发现」）

- `GROUP BY workday` 的分布必须只有 1 和 2，出现其他值（含 0、NULL）即停并报告。
- 每年 HOLIDAY 数 > 0；11 年合计应是几十到一百多，**为 0 即失败**（本缺陷正是这一项为 0）。
- 用已知答案点校：2026-10-01（国庆）`workday = 2` 且换算为 HOLIDAY；任取一个已知调休周末应为 MAKE_UP_WORKDAY。
- 可选的第二来源：A8 BI 表 `T_FST_CALENDAR`（同为 4,018 行，另有 `WORKDAY_BJ` 列，见证据页第 250 行）与 `psa_calendar` 逐日比对「休息日」集合，不一致的日期交 one-ops 报告。这是生产只读，需登记。
- 剧本里写明：日历核对 = 行数 + 取值分布 + 假日数 + 点校，缺一不可。

## 5. 局限

- 我没有查看 CSV 文件和生产库，所有结论来自仓库里的 SQL、脚本、剧本、规则卡和证据页；`workday` 的两个取值含义以代码推出的规则卡为准，重导出时的分布检查会再次验证。
- dev 是否已导入日历，我没有找到记录，需 one-ops 回答；该答案只影响第 3 节第 5 步的做法，不影响其余结论。
