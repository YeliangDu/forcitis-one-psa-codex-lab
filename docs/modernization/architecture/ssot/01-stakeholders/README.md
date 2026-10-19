# 01 干系人（仓库内唯一来源）

`SSOT.yaml` 是 FORCITIS one 干系人节点的唯一来源（用户 2026-10-19，intent `2026-10-19-223711`）。仓库里其他文件中的干系人列表（`proposals/STAKEHOLDERS.md`、`value-streams.v2.yaml` 的干系人编号等）以本文件为准，旧编号按 `legacy-id-map.yaml` 对照。

- 结构：类型（内部 / 外部 / 两者）→ 类别（6，冻结）→ 具体干系人（25）；`family` 是标签，不是层；角色（`ROLE-`）与项目参与方（`PRJ-`）不是干系人（BIZBOK v11 §2.8，印刷页 266、268、269–274）。
- 来源：用户在桌面维护的 v10，经 one-coordinator 对照 BIZBOK v11 §2.8 审核后导入（v11），导入时按用户决定拆分合伙人。
- 待对账：见 `SSOT.yaml` 的 `meta.open_reconciliation`。

校验：

```bash
python3 docs/modernization/architecture/ssot/01-stakeholders/validate_categories.py
```

改动只改 `SSOT.yaml`，改后跑校验。下游节点（02 起）尚未导入，暂不据本文件改写下游文件。
