# 权责对照表与聚合收敛：结构说明

依据：用户确认两项合并（one-coordinator 2026-10-16 转，intent 已记）；用户原则「看谁对费用负责，谁负责谁批」「能通过规则和不变量守卫的就不要人来守」「正常单人、异常加一、特殊加二」；ADR-0026（授权模型）、ADR-0032（敏感权限逐人授予）、ADR-0029（按岗位授予，搁置至岗位重梳）。

## A. 权责对照表 `RESPONSIBILITY_MATRIX.yaml`（唯一来源；审批规则、泳道、负担分析从此取数）

```yaml
entries:
  - object: AGG-59            # 业务对象（聚合）
    command: 核定费用报销      # 动作（聚合命令，取命令目录）
    raci:
      A: {resolve: instance, field: 费用归属对象.责任人}   # 唯一；持审批权。type 级写 {resolve: type, role: 财务负责人}
      R: [SH-E6]                                          # 执行
      C: []                                               # 咨询
      I: [SH-E3]                                          # 知会
    boundary:                                             # 授权边界，超界即加审（引用 APPROVAL_RULES 的 guard: approval 规则）
      limit_rule: AR-01
      segregation: [提交人 ≠ A]
      delegate: {when: AR-11, to: 汇报线上级}
      escalate: {when: AR-12, to: 上级组织负责人}
    sensitive: false                                      # true 的权限按 ADR-0032 逐人授予，不随岗位
roles:                                                    # 角色 → 职位 → 人（分层映射；岗位重梳只改这一层）
  SH-E6: {positions: [待岗位体系定], note: org_needs 汇总}
```

- `resolve: type`：类型级，按角色固定（如发票入账 → 财务负责人）；`resolve: instance`：实例级，取对象上的负责人字段（如该项目的项目经理）。
- 每个「对象×命令」恰一个 A；A 才持审批权；需审批的命令其骨架 `approval.approver` 必须等于 A。
- 角色到职位到人的映射在岗位体系重梳前只记 `org_needs` 汇总，不按岗位自动授权（ADR-0029 搁置）。

**A.1 生成与合并（`raci_build.py`）**：以三组流程骨架推出 R、I、审批推出的 A 与 boundary 为底；各组权责片段（people `raci.yaml`、finance `responsibility.finance.yaml`；revenue 无片段）覆盖同一对象×命令的 A，并带上 sensitive、boundary（片段键优先）、note 等字段，C 与 I 取并集，R 骨架为空时取片段。A 的优先级：手登类型级（`a_source: hand`）> 组片段（`group:<组>`）> 骨架（`skeleton`）> 无（`none`，A 为 TODO）。冲突只报告不裁决，全文见 `RACI_MERGE_REPORT.md`：片段 A 与骨架审批不一致、两组片段不一致、platform.approval 类型级 A 缺 basis 或 why_human 出处、角色写法非 SH- 编号（仅当 `value-streams.v2.yaml` 的 stakeholders 能唯一对应时规范为 SH-，原文记 `role_raw`）、片段独有而骨架与命令目录均无。

## B. 按聚合收敛（每个价值流质检通过后做其涉及的聚合）

| 产物 | 内容 | 检查（脚本） |
|---|---|---|
| 命令目录 `commands.yaml` | 合并同义命令；参数、前置条件（规则卡或不变量编号）、发令权（取 A 表）、幂等键 | 无孤儿命令（每个命令被某流程、事件策略或维护用到；未用的标出交 one-architecture） |
| 状态机 `state-machines.yaml` | 每聚合：状态、转换（由哪个命令触发）、守卫（规则卡或不变量，交 one-rules）、终态、逆向转换（冲销、撤回） | 状态全可达、每个非终态能到某终态；每个 BPMN 改状态步骤对得上一条转换 |
| 事件目录 | 每个事件的发布聚合与订阅方（或仅分析） | 复用 EventCatalogueTest 口径与 `processes_check.py` |

查询与报表另从报表需求出，不在此项。产物为提议稿，里程碑经用户认可进 SSOT（过渡 ADR 写明「聚合稿的状态机与命令目录以收敛产物为准」）。
