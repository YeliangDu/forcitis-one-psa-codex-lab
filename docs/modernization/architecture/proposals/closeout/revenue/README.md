# 收入线组（one-arch-revenue）现状速查

更新：2026-10-17 13:26+02:00（画像审查 GOV-1017-COO-04 后重核待答项）。分支 `claude/one-arch-revenue-closeout`（文档类不开 PR，报分支与提交号由 one-merge 收合集）。

| 项 | 现状 |
|---|---|
| 范围 | 目录 group: revenue：业务流程 46、事件策略 10（含 PRC-146–148、150、179）、单聚合维护 13、读模型 3 |
| 产出 | `processes.revenue.yaml` 骨架、`scenarios.revenue.yaml` 场景、`data-model.revenue.yaml` 表（161 张，含 pi）、`commands.yaml`（命令目录）、`state-machines.yaml`（52 台）、`raci.yaml`（权责片段）、`NOTES.md`、`Q04_STORAGE.md`、`C_DEFAULTS.md`、`QC-VS-0x.md`、`QC-LIFECYCLE.md`、`STAGE_ALIGN.md`、`REVIEW.md` / `RECHECK.md`、`SAMPLE.md` 与 `bpmn/PRC-28.bpmn` |
| 质量 | closeout_check --qc 全 0；bpmn_gen 全过；converge_check 仅余财务组 AGG-52/53/59 |
| 用户页 | VS-01–04 评审包 `VS_REVIEW.md` 已交 one-architecture 出确认页（C7，5b31ba27） |
| 进行中 | 对象层主对象已确认（`OBJECT_LAYER.md`）；PRC-183 参投决策；报价发出编排 REF-04 接入 PRC-07、08（ARC-1017-ARE-01，58e97ab8，名称待 one-glossary N7） |
| 阶段层 | SD-11～15 已答并写回价值流（b4add5cb）；C7 复核：6a 未说明 0、6d 0，6b 18 条均写不加理由（STAGE_ALIGN 第 7 节） |
| 暂停 | CRUD 清零（S-43 骨架未改，QC-LIFECYCLE 8 条真缺口待补）；等能力层开（三组阶段对齐交齐、用户审对齐图后） |
| 等用户 | Q-04 / VS04-01；Q09（含 SD-09）、Q13、Q20（VS-04.2 退出对象状态 MS-17）、Q21；MS-23（产值状态）；合同负责人是谁（未找到答复，未核实）。SD-08 已定，SD-11～15 已答 |

接续第一步：读画像「当前状态」与本文件，再看 `STAGE_ALIGN.md` 第 7 节、`VS_REVIEW.md`；输入以 ADR-0052/0054/0055/0056 为准；脚本用 governance 分支 `closeout/` 下的 stage_align_check.py、closeout_check.py --qc、converge_check.py、object_layer_check.py 复跑；Codex 派卡按画像「全员工作守则」。
