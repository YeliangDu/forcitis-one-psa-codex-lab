# N7 下游改名清单补充（结构引用字段）

状态：交接｜2026-10-17｜负责：one-architecture

任务 NAM-1017-ARC-10。补 N7_DOWNSTREAM_RENAMES.md 漏列的一类：YAML 里按整值引用名字的结构字段（状态机 from/to 列表项、reverse_of、emitted_by、emits，流程 states / outcomes / outcome，场景 outcome）。
依据 N7_RENAME_FINAL.json；匹配规则：整值精确等于旧名（emitted_by 等按「、」拆分），且所在聚合 / 对象（含流程的 aggregate、primary_object，场景按所属流程）在该行的所属集合内；同名但所属不符的不列。生成器 `~/.modernize/psa/handoff/evidence/NAM-1017-ARC-10/downstream.py`，只读 git show，未改三组分支。

自测：对 finance 回写前提交 a0f4f296 跑，90c32dba 实际改动的结构引用行 16 行（含 from 列表、reverse_of、emitted_by、outcome(s)）全部命中，漏报 0，误报 0；去掉新增匹配后 16 行全漏。finance 当前 d4679ab8 复跑为 0 处残留。

## revenue

读取：`origin/claude/one-arch-revenue-closeout` @ `ba245c50`；残留 12 处。

- `revenue/commands.yaml:3103` — 延期视为批准 → 延期已视为批准（NM6-09#7，event；所属 AGG-31；字段 `emits`）
- `revenue/processes.revenue.yaml:4134` — 延期视为批准 → 延期已视为批准（NM6-09#7，event；所属 AGG-31；字段 `emits`）
- `revenue/processes.revenue.yaml:5632` — 不再跟踪 → 已停止跟踪（NM6-13#4，state；所属 OBJ-38；字段 `outcomes`）
- `revenue/processes.revenue.yaml:5893` — 不再跟踪 → 已停止跟踪（NM6-13#4，state；所属 OBJ-38；字段 `outcome`）
- `revenue/processes.revenue.yaml:11485` — 取消 → 已取消（NM6-11#16，state；所属 OBJ-105；字段 `states`）
- `revenue/processes.revenue.yaml:11527` — 取消 → 已取消（NM6-11#16，state；所属 OBJ-105；字段 `to`）
- `revenue/processes.revenue.yaml:11535` — 取消 → 已取消（NM6-11#16，state；所属 OBJ-105；字段 `to`）
- `revenue/scenarios.revenue.yaml:6` — 结束 → 已结束（NM6-11#3，state；所属 AGG-08、OBJ-16；字段 `outcome`）
- `revenue/scenarios.revenue.yaml:37` — 取消 → 已取消（NM6-11#16，state；所属 OBJ-105；字段 `outcome`）
- `revenue/scenarios.revenue.yaml:151` — 停用 → 已停用（NM6-11#1，state；所属 AGG-01、OBJ-01；字段 `outcome`）
- `revenue/scenarios.revenue.yaml:418` — 到期 → 已到期（NM6-11#7，state；所属 AGG-51、OBJ-13；字段 `outcome`）
- `revenue/scenarios.revenue.yaml:1591` — 不再跟踪 → 已停止跟踪（NM6-13#4，state；所属 OBJ-38；字段 `outcome`）

## people

读取：`origin/claude/one-arch-people-closeout` @ `c5bc7bd2`；残留 4 处。

- `people/commands.yaml:2188` — 工作日历锁定日期被修改 → 工作日历锁定日期已修改（NM6-10#11，event；所属 AGG-68；字段 `emits`）
- `people/events.yaml:731` — 预算占用 → 占用预算（NM6-07#7，command；所属 AGG-80；字段 `emitted_by`）
- `people/parts/vs0911.events.yaml:29` — 预算占用 → 占用预算（NM6-07#7，command；所属 AGG-80；字段 `emitted_by`）
- `people/parts/vs13att.commands.yaml:500` — 工作日历锁定日期被修改 → 工作日历锁定日期已修改（NM6-10#11，event；所属 AGG-68；字段 `emits`）

说明：`people/events.yaml:731` 与 `people/parts/vs0911.events.yaml:29` 是同一槽（合并产物与分部源文件），回写时两处都改；`commands.yaml:2188` 与 `parts/vs13att.commands.yaml:500` 同理。
