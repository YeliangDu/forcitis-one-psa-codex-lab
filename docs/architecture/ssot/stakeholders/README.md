# 01 干系人（仓库内唯一来源）

`SSOT.yaml` 是 FORCITIS one 干系人节点的唯一来源（用户 2026-10-19，intent `2026-10-19-223711`）。仓库里其他文件中的干系人列表（`proposals/STAKEHOLDERS.md`、`value-streams.v2.yaml` 的干系人编号等）以本文件为准，旧编号按 `legacy-id-map.yaml` 对照。

- 结构：类型（内部 / 外部 / 两者）→ 类别（6，冻结）→ 具体干系人（38：法人与群体 25、客户侧对接条线 13）；`family` 是标签，不是层；角色（`ROLE-`）与项目参与方（`PRJ-`）不是干系人（BIZBOK v11 §2.8，印刷页 266、268、269–274）。
- 条线：`SH-C*-L*` 是 team 粒度干系人，`host` 指向法人级干系人；岗位差异写 `members`，流程扮演归 `roles`（TOGAF C220 Part0 §4.75、Part2 §3.4；ArchiMate 3.2 §6.2.1；BIZBOK v11 §2.8）。条线证据只记拜访编号与主题，明细在仓库外（ADR-0037）。
- 来源：用户在桌面维护的 v10，经 one-coordinator 对照 BIZBOK v11 §2.8 审核后导入为 V1，导入时按用户决定拆分合伙人；用户 v11.1（2026-10-20）加 13 条条线，审核后导入为 V2，保留 V1 的用户决定。
- 待对账：见 `SSOT.yaml` 的 `meta.open_reconciliation`。

校验：

```bash
python3 docs/architecture/ssot/stakeholders/validate_categories.py
```

改动只改 `SSOT.yaml`，改后跑校验。下游节点（02 起）尚未导入，暂不据本文件改写下游文件。
