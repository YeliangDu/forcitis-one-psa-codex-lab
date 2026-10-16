# 收入线组（one-arch-revenue）现状速查

| 项 | 现状 |
|---|---|
| 范围 | 目录 `process-catalog.yaml` 中 group: revenue：业务流程 41、事件策略 8（含 PRC-146–148）、单聚合维护 13、读模型 2 |
| 约定 | `closeout/CONVENTIONS.md`（含第 5 节补充）、`BPMN_STYLE.md`、`APPROVAL_RULES.yaml`、`PROCESS_QUALITY_CHECKLIST.md`（均在 one-architecture 分支） |
| 产出 | `processes.revenue.yaml`（骨架）、`scenarios.revenue.yaml`（验收场景）、`data-model.revenue.yaml`（表级）、`NOTES.md`、`Q04_STORAGE.md`（VS-04 页用稿）、`SAMPLE.md` 与 `bpmn/PRC-28.bpmn`（用户确认的样板） |
| 进度 | 四个价值流骨架已铺开；按第 5 节统一改写（审批 why_human、cost_object_owner、负担参数、org_needs）与 QC 自检进行中 |
| Q-04 | 推荐 C，标 `pending: Q-04`（PRC-53、148），待 VS-04 页用户答复 |
| 下一步 | 合并改写 → 跑 bpmn_gen / closeout_check / build_catalog → 写 QC-VS-0x.md → opus 首轮评审 → 报 one-architecture 合成用户页 |
