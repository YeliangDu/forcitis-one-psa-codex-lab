# 收入线组（one-arch-revenue）现状速查

更新：2026-10-22 18:55+02:00（批次边界，准备 /clear）。分支 `claude/one-arch-revenue-closeout`（文档按 TEAM-53 由 one-merge 自扫远端收合集）。

| 项 | 现状 |
|---|---|
| 范围 | 目录 group: revenue：业务流程 46、事件策略 10（含 PRC-146–148、150、179）、单聚合维护 13、读模型 3 |
| 产出 | `processes.revenue.yaml` 骨架、`scenarios.revenue.yaml` 场景、`data-model.revenue.yaml` 表（161 张，含 pi）、`commands.yaml`（命令目录）、`state-machines.yaml`（52 台）、`raci.yaml`（权责片段）、`NOTES.md`、`Q04_STORAGE.md`、`C_DEFAULTS.md`、`QC-VS-0x.md`、`QC-LIFECYCLE.md`、`STAGE_ALIGN.md`、`REVIEW.md` / `RECHECK.md`、`SAMPLE.md` 与 `bpmn/PRC-28.bpmn` |
| 质量 | closeout_check --qc 全 0；bpmn_gen 全过；converge_check 仅余财务组 AGG-52/53/59 |
| 用户页 | VS-01–04 评审包 `VS_REVIEW.md` 已交 one-architecture 出确认页（C7，5b31ba27） |
| 近期产出 | C7（STAGE_ALIGN 第 7 节、VS_REVIEW.md、56 张 BPMN）；N7 改名回写；transition-review/revenue.yaml 判定；GL5-01（AGG-13 已签署 / 已生效）；legal-entity/ 三份法人口径（LEB 已落地）；AGG-21 基线引用 INV-140、141；DEFAULT_PAYMENT_TERM.md（AGG-136、PRC-202） |
| 阶段层 | SD-11～15 已答并写回价值流（b4add5cb）；C7 复核：6a 未说明 0、6d 0，6b 18 条均写不加理由（STAGE_ALIGN 第 7 节） |
| 后续未派 | 终止 / 取消项目复核（已攒确认页，one-coordinator）；CRUD 清零（QC-LIFECYCLE 8 条） |
| 状态机缺口 | AGG-20 / AGG-16 / AGG-02（CF-039、042、048）已补，ARC-1022-ARE-02，见 NOTES「状态机缺口补齐」；one-architecture 三件已定（5849fa0f5），INV-42 文字本分支已改 |
| 等用户 | Q-04 / VS04-01；Q09（含 SD-09）、Q13、Q20（VS-04.2 退出对象状态 MS-17）、Q21；MS-23（产值状态）；合同负责人是谁（未找到答复，未核实）。SD-08 已定，SD-11～15 已答 |

接续第一步：读画像「当前状态」与本文件，再看 `STAGE_ALIGN.md` 第 7 节、`VS_REVIEW.md`；输入以 ADR-0052/0054/0055/0056 为准；脚本用 governance 分支 `closeout/` 下的 stage_align_check.py、closeout_check.py --qc、converge_check.py、object_layer_check.py 复跑；Codex 派卡按画像「全员工作守则」。
