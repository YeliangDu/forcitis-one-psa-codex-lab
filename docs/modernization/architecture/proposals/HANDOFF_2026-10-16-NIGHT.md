# one-architecture 交接（2026-10-16 深夜）

接续先读本文件，再读 `ARCH_CONTEXT.md`；下午交接 `HANDOFF_2026-10-16-PM.md` 只作历史。

## 分支与 PR

- 本会话分支 `claude/one-architecture-adr-governance`（PR #365，叠在 #350 上；#350 合入后改 base 为 main）。
- 收尾计划分支 `claude/one-architecture-closeout-plan`（PR #384，基于 main）：工作树 `<scratchpad>/closeout-wt`（会话结束后若丢，重新 `git worktree add`）。**两分支同步维护 `proposals/closeout/`**：每次改动两边都提交；推送用字面路径 `git -C <路径> push`（推送钩子不认 `$变量`）。
- 新规（SESSION_PLAYBOOK §11 第 13 条）：提议稿留分支到里程碑再合；文档类调度打包合入；每会话同时开 PR ≤3；开 PR 必报调度。
- 未跟踪 `.claude/launch.json`（本地预览 decision-pages，端口 8791），不提交。

## 架构收尾现状（计划 `ARCH_CLOSEOUT_PLAN.md`，用户确认 intent 151644）

- 三个临时会话 one-arch-revenue / finance / people 按价值流铺开（方法已批，intent 162820；样板答复 162821）。流程唯一目录 `closeout/process-catalog.yaml`（生成 `CATALOG.md`）：截至 PRC-157，业务流程约 100。
- revenue：四个价值流铺开完、质检脚本 0 缺口，41 张生成图全过；opus 首轮「改后再交」改稿中（S-27 给号 149–154）。
- finance：铺开中；样板 PRC-87 按 S-16/17/19/24 定稿。
- people：全组修订完，质检通过；收敛（commands、state-machines）部分完成；S-28 给号 155–157、AGG-119。
- 结构问题台账 `closeout/STRUCTURAL_ITEMS.md` S-01–S-28；聚合稿与信息图已合入 S-01–S-22 回补（AGG-113–118、OBJ-113–116、INV-118–123），**待合**：revenue INV-新A–C（给 INV-124–126）、S-23/S-25/S-26/S-27/S-28 的聚合与依赖边回补、INV-102 定预警、INV-23 字面（随 ADR-0047 修订）。
- 约定 `CONVENTIONS.md`（第 5 节含审批口径、engine 区分、类型级例外、平台依赖、负担参数、org_needs）；画法 `BPMN_STYLE.md` 第 2 版（非中断超时接「已提醒」）。
- 脚本（`closeout/`）：`build_catalog.py`、`bpmn_gen.py`、`bpmn_check.py`（R01–R15、X01–X05）、`closeout_check.py`（含 --qc）、`raci_build.py`、`converge_check.py`、`object_check.py`（CRUD 矩阵）。
- 质检 `PROCESS_QUALITY_CHECKLIST.md`（八维度＋人工审批须写 why_human）：每价值流不过不交用户。
- 用户页按 M-7：每价值流一页（只问业务判断与待定题）；**尚未出任何价值流页**（revenue 待首轮评审改完）。

## 审批与权责

- 规则表 `closeout/APPROVAL_RULES.yaml`：基础审批人＝归属对象责任人；guard: rule 六条（RULE-406–411，one-rules 已写回范围）；guard: approval：AR-01/02 阈值（待 one-ops 金额分布 E-B6 后出阈值建议页）、AR-06 冲销红冲 +1、AR-07 关联方 +2、AR-11/12 上级代批、AR-13/14 交付物校审按项目重要性 N/V/S（**S 与 V 高低待 one-ops 取证**）、AR-15 第三方代收代付 +2。intent 164324、171147。
- 权责对照表结构 `RESPONSIBILITY_MATRIX_SPEC.md`；`raci_build.py --write` 待各组骨架齐后出初稿。泳道先按角色，org_needs 收集，岗位体系定后一次性改（用户 intent，经调度）。

## 收入与 ADR 待办

- 过渡 ADR（未起草）须含：施工方案 34 条、ADR-0050 决定 3/16/19/20②/J8/⑥b 与 FRAMEWORKS §2、ADR-0036 决定 10（演练环境）、ADR-0047 第 4 条生效前提改「财务负责人书面确认并留档、每年复核加重大变化临时复核」（A1 后续页，one-rules intent 162827）、INV-23 字面、A8 停用、B-31 行政事务（organization.administration）、聚合稿状态机与命令目录以收敛产物为准、事件日志实现约束。
- Q-04 收窄为「权责收入确认记录怎么存」，并入 revenue VS-04 用户页（Q04_STORAGE.md），出页前给 one-rules 过目。
- 勘误 E-004（ADR-0030 witnessId=none）已登。

## 排期与后续

- 负担与理论跑完时间分析：`BURDEN_ANALYSIS_PLAN.md`，取证 E-B1–E-B8 交调度派 one-ops；全部铺开且质检通过后 1.5–2 天出页（人级只私下）。
- 方法 SSOT：`docs/modernization/architecture/PROCESS_METHOD.md`，素材 `closeout/METHOD_VALIDATION_LOG.md`；铺开收尾后 1 天出初稿，opus＋sonnet 后连同 ADR 修订交用户。
- 地基批、校准车道暂停（地基并入收尾 ③）。
