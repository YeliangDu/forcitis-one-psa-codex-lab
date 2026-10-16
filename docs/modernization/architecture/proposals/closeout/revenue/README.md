# 收入线组（one-arch-revenue）现状速查

| 项 | 现状 |
|---|---|
| 范围 | 目录 `process-catalog.yaml` 中 group: revenue：业务流程 41、事件策略 8（含 PRC-146–148）、单聚合维护 13、读模型 2 |
| 约定 | `closeout/CONVENTIONS.md`（含第 5 节补充）、`BPMN_STYLE.md`、`APPROVAL_RULES.yaml`、`PROCESS_QUALITY_CHECKLIST.md`（均在 one-architecture 分支） |
| 产出 | `processes.revenue.yaml`（骨架）、`scenarios.revenue.yaml`（验收场景）、`data-model.revenue.yaml`（表级）、`NOTES.md`、`Q04_STORAGE.md`（VS-04 页用稿）、`SAMPLE.md` 与 `bpmn/PRC-28.bpmn`（用户确认的样板） |
| 进度 | 骨架 45 业务流程 / 9 策略 / 13 维护 / 3 读模型，质检脚本全过（QC-VS-01–04）；opus 首轮评审改完、sonnet 复核通过；命令目录 253 条、状态机 52 台（converge_check 仅余财务组 AGG-52/53/59）；VS-01–04 用户页已发布（94 题），待用户答 |
| Q-04 | 推荐 C，标 `pending: Q-04`（PRC-53、148），待 VS-04 页用户答复 |
| 下一步 | 用户答复后按 one-architecture 转来的结论写回骨架、场景与表；raci_build 统一生成后核对 authority |
