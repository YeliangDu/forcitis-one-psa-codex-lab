# 流程梳理

规则见 `../METHODS.md` 附 M4。每个阶段一个目录 `process/<阶段>/`（阶段编号与 brief 一致，如 `1a`、`1b`、`2`）。

业务流程的唯一登记是 `../architecture/ssot/09-processes/catalog.yaml`（PRC 编号、类型、阶段、可执行 BPMN；GOV-1023-ARC-01）。这里的文档是某阶段端到端流程的梳理细节（现状、目标、差异与契约），由目录条目的 `flow_docs` 指向，不另成流程清单；平台机制（审批引擎、编号、审计、登录）不进目录，梳理只在这里。

1a 现有：`TIMESHEET审批.md`（PRC-23 填报并审批工时单，可执行 BPMN `cost_timesheet_approval`）；平台机制 `审批通用流程.md`、`登录与会话.md`、`合并待办与通知.md`、`业务编号.md`、`审计.md`；`组织与人员同步.md`（已退役）；`contract.md`（行为契约汇总，`rules/build_brief.py` 读入 brief）。

## `<流程>.md` 模板

```markdown
# <流程名>（Phase <阶段>）

- 涉及 BC：
- 涉及的 A8 模板 / 老系统入口：
- 证据采集日期：

## 现状流程

（mermaid 或 BPMN 图）

| # | 步骤 | 角色 | 系统 | 数据 / 状态变化 | 证据 |
|---|---|---|---|---|---|
| 1 | | | | | `path/File.java:123`；OA_PROCESS_INVENTORY §x；生产计数（日期、出处） |

## 目标流程

（mermaid 或 BPMN 图；依据：规格 / ADR / 规则卡）

## 差异清单

| 编号 | 现状 | 目标 | 影响 | 建议 | 用户决定 |
|---|---|---|---|---|---|
| D-<阶段>-01 | | | | | 待定 / 原话（intent 片段文件名） |
```

## `contract.md` 格式

只收录已有用户决定的差异，每条一句可测试的行为：

```markdown
- D-<阶段>-01：<行为>（用户决定：intent/<片段文件名>）
```

`build_all.py` 把它生成到 brief §5「流程梳理结论」。
