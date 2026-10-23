# 交接报告：one-1a-next-4 → one-1a-next-5

> 2026-10-22（23:35 复核仍成立）注：本报告第 3 节的数据修复批已全部完成并合入（见 `HANDOFF_one-1a-next-5.md` 第 2、5 节）；接手依据以 next-5 报告为准，本报告只留历史证据。


交接时点：2026-10-16，上下文约 75%（SESSION_PLAYBOOK TEAM-05、06），批次边界：数据修复 PR-2 开出之后。

**会话约定**：全程中文；派子代理显式指定模型并要求中文回复，一律 `isolation: worktree`；权限模式与 one-coordinator 一致；开 PR 后立即 `mcp__ccd_pr__set_monitor` 关 Auto-fix（以 `get_status` 为准）；PR 由 one-coordinator 审核入队，本线不合并；跨会话消息一到三行，只在对方要做事时发（§11）；用户决定走「待决定收件箱」，不在会话里用提问框。

## 1. 先读

1. 本报告；`docs/modernization/quality/defect-bank/one-1a-next-4.md`（错题本，交付前逐条回测）。
2. 数据修复卡 `docs/modernization/data/repair-cards/`（README 与四张卡）、`DATA_DESIGN_GATE.md`（数据设计门禁，§13）、one-data 签发记录 `docs/modernization/data/reviews/`。
3. 排序与切 PR 方案：`~/Library/Application Support/Claude/side-session-notes/local_10f279cb-81c7-4628-9080-2d5b1efd622d/one-1a-next-4-data-repair-plan.md`。
4. ADR-0048（审计分区，用户已批准；正文已随 #324 合入）、ADR-0043、ADR-0033。
5. 私有证据 `~/.modernize/psa/handoff/one-1a-next-4/notes.md`（按时间的决定与待办，不入仓）。

## 2. 本会话完成

| 项 | PR | 状态 |
|---|---|---|
| R-1b-0a A8 部门类型直读基表、外部读取登记 | #250 | 已合并 |
| R-1b P3b 员工、部门、岗位改自有 UUID | #260 | 已合并 |
| R-1b P4 审批 ID、Flowable id 生成器、去重键拆列、部门负责人审批改名 | #276 | 已合并 |
| R-1b P5 收口（信封与审计对象 UUID、删 source、四事件改名、守护、ID_CARRIERS） | #310 | 已合并 |
| 数据修复 PR-5 identity（ID-03、05–09、11 name、12） | #328 | 已入队 |
| 数据修复 PR-2 timesheet-audit TB-01、TB-02、TB-03 | #337 | 已开（opus 首轮 + 终审已过），待入队 |

## 3. 在途与下一步（数据修复批，生产前闸门，不挡 dev；one-coordinator 裁定 D1–D5）

- **PR-1 审计分区**（TB-07 + TB-09 + TB-05，ADR-0048）：前置已满足：#324 已合入，ADR-0048 正文在 `adr/src/ADR-0048.md`，可直接开（先请 one-data 逐条签）。one-architecture 判定：TB-09 选删 `created_at DEFAULT`，列注释「审计事实发生的时间：事件条目取事件信封的发生时刻，直接审计取注入的时钟」。体量最大；opus 首轮 + opus 终审（守护、凭据）。须 one-data 逐条签。
- **DR-TB01-FU1**（小卡，one-data 准许从 PR-2 移出）：运维证明的操作人、见证人员工 ID 须为存在员工（不存在 → 退出码 2，不写审计）；`OperationsCommands` 注入 `OrgDirectory`；须测试代理按 Acceptance-Ruling 在 5 个验收文件（`AccountsAcceptanceSupport`、`AccountEntriesConcurrencyIT`、`OperationsCommandEmployeeIdIT`、`EmergencyLoginEmploymentIT`、`ExternalSubjectLookupCommandIT`）造出 `…a001` / `…a002` 员工；同步 INFRA_RUNBOOK。开工时报 one-coordinator。另：PR-2 复审 L-1（`claim` 返回行 id、后续 UPDATE 带行 id，改 kernel 端口）建议另开小卡。
- **PR-3**：TB-04（导出去 count，先核 K-3b-4 是否已做）、TB-06、TB-08。
- **PR-6**：审批 AP-01、02、03、05、08（AP-01 指向 `start_rounds(instance_id)`，若非 uuid 同 AP-17 处理）；AP-04 目标为 uuid 唯一约束可放行（one-architecture ④）。
- **PR-7**：**须等 one-1a-par 的 numbering 小卡（`IssuedNumber` 带版本 ID、`TemplateVersion` 带 ID，TS-UNIQ 前置）合入后再开，以它为基线**，避免两边同时改 numbering。范围：编号 AP-09 等；AP-14 只留 TIMESHEET（删 QUOTE、JOURNAL_VOUCHER 种子与财务确认列，one-architecture ⑤）；AP-17 改 CHECK 不作外键。
- **PR-8**：workforce 中不含 sync_* 的条目；WF-09 撤 5 条 fk_exceptions 加外键（已接受）；WF-12 目标为 uuid 唯一约束可放行；B7-2 保留并在注册表写「使用方未建，由同步原样入库」（one-architecture ⑦）。
- **并入他卡，不单做**：WF-01/02/03/07/08/13/14 → 拆分 a；AP-06/07 → DA-10；ID-01/02/10/13 → W2；TA-02 → 日历版本化；WF-06 → DA-7；TA-01 → one-1a-par（TS-UNIQ 后）。这些卡也都在基线关闭前。
- **待用户**：ID-04（binding_hash 级别）、U-7b 真到期日已不需要（U-7b 已答改写员工 ID）；ADR-0033 第 9 项第 2 条时点偏离追认（收件箱 calendar-scope-timing）。
- **其他留下的**：DA-10 必补外发投递收件人唯一约束（P4 RULING-P4-D4）；企业微信 task_id 真机核对（dev 冒烟，one-ops）；ID-06 转 gap 挂 ADR-0043 实施 PR；两账号同一浏览器并发确认死锁（40P01，旧问题，另开卡）；F-2 守护卡承接 P5 L-11 守护盲区。

## 4. 规矩（本会话期间新增，照做）

- 数据设计门禁：涉数据的卡派验收前须 one-data 逐条签「数据设计：通过」；修复卡由 one-data 起草也须逐条签。
- 复审分级：代码 opus 首轮 + sonnet 复核；守护、权限、凭据部分 opus 首轮 + opus 终审，最多两轮。
- 模型：实现 / 测试 sonnet；opus 只用于首轮复审、终审、安全判定、结构性设计（写明理由）。
- 卡与已采纳 ADR 字面不一致：起草阶段交 one-architecture 判定，不自称落地细化（错题本 D1、D2）。
- 修好缺陷后 grep 已知故障 / 例外 / 基线清单（D3）；合 main 后跑全量或至少跑同夹具的全部测试（D4）。
- 验收目录改动须 `Acceptance-Ruling` 尾注，尾注须在提交信息最后一段连续书写，不能被空行隔开（acceptance guard）。
- zsh 推送写 `"${H}:refs/heads/<分支>"`。

## 23:59 交接（上下文 21%）
#634 终审通过待批；#599 已批待 one-merge 入队；REG-4 已实现未推送未复审，续作见 phase1a/HANDOFF_one-1a-next-5.md 末段「23:59 状态」。无未完成的用户请求。
