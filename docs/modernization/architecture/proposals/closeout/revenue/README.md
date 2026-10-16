# 收入线组（one-arch-revenue）现状速查

更新：2026-10-16（批次边界，准备 /clear 接续）。分支 `claude/one-arch-revenue-closeout`（不开 PR，里程碑再合）。

| 项 | 现状 |
|---|---|
| 范围 | 目录 group: revenue：业务流程 45、事件策略 10（含 PRC-146–148、150、179）、单聚合维护 13、读模型 3 |
| 产出 | `processes.revenue.yaml` 骨架、`scenarios.revenue.yaml` 场景、`data-model.revenue.yaml` 表（161 张，含 pi）、`commands.yaml`（命令目录）、`state-machines.yaml`（52 台）、`raci.yaml`（权责片段）、`NOTES.md`、`Q04_STORAGE.md`、`C_DEFAULTS.md`、`QC-VS-0x.md`、`QC-LIFECYCLE.md`、`STAGE_ALIGN.md`、`REVIEW.md` / `RECHECK.md`、`SAMPLE.md` 与 `bpmn/PRC-28.bpmn` |
| 质量 | closeout_check --qc 全 0；bpmn_gen 全过；converge_check 仅余财务组 AGG-52/53/59 |
| 用户页 | VS-01–04 四页改版后在收件箱；用户在对齐完成前暂不作答 |
| 进行中 | 业务架构逐层对齐（阶段 → 能力 → 信息 → 干系人），现做第一层「阶段」 |
| 等 one-architecture 判 | STAGE_ALIGN 第 6 节：6a 第四类（改用语 3 / 疑脚本 3）；8 条只靠进入条件归段（PRC-03、05、12、13、17、26、41、152）与 PRC-01、02（SD-14）同类处理 |
| 暂停 | CRUD 清零（S-43 骨架未改，QC-LIFECYCLE 8 条真缺口待补）；等能力层开（三组阶段对齐交齐、用户审对齐图后） |
| 等用户 | Q-04（VS04-01）；SD-08、09、12、13、14；合同负责人是谁；Q09、Q13、Q21 |

接续第一步：读本文件与 `STAGE_ALIGN.md` 第 5、6 节，收 one-architecture 对第 6 节的裁定后改用语或改标；脚本用 governance 分支 `closeout/` 下的 stage_align_check.py、closeout_check.py --qc、converge_check.py 复跑。
