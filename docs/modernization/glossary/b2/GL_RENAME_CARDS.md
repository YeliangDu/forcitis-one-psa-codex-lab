# GL 确认单引出的改名卡（用户 2026-10-16 逐组提交，76 题全部按推荐）

词表这边已写回：登记新名的词条已转正式；下列代码类名仍是旧名，由负责会话开卡改名，改完同批删掉对应的存量清单行（`glossary/code-term-baseline.txt` 与 `glossary/term-rules-baseline.json`）并调低计数。词表里的词条随改名同批改英文名（提议条目 `backend/glossary/proposed/code-aggregates-events.yaml`）。

| 题 | 旧类名 | 新类名 | 中文名 | 负责会话 |
|---|---|---|---|---|
| B2-B01 | AccountDisabled（身份，事件） | UserAccountDisabled | 用户账号已停用（词条已登记，状态词条「账号停用」不动） | 身份模块 |
| B2-B02 | ApprovalTaskOverdue（审批，事件） | ApprovalTaskBecameOverdue | 任务已超时（不变） | one-1a-next-4（P4 之后） |
| B2-B03 | TaskResulted（审批，事件） | TaskHandled（第三轮已定） | 任务已办理；改完删「任务已出结果」那条提议 | one-1a-next-4（P4 之后） |
| B2-B05 | SourceReferenceRegistrationRefused（集成，事件） | SourceReferenceRefused | 来源对照登记已被拒绝 | 集成模块 |
| B2-B06 | RoleDeleted（身份，事件） | RoleAbolished | 角色已废除 | 身份模块 |
| B2-B07 | TimesheetCancelled（工时，事件） | TimesheetApprovalWithdrawn | 工时单已撤回审批 | 工时模块 |
| B2-B09 | ContractModification、EclAlert、RetentionMoneyReleaseDue、RevenueReduction、RuleExecution（事件） | ContractModified、EclAlerted、RetentionMoneyReleaseDueReached、RevenueReduced、RuleExecuted | 合同已变更、预期信用损失已预警、质保金已到期、收入已冲减、规则已执行 | 各自模块 |
| B2-B10 | ForcedLogout（命令） | ForceLogout | 强制登出 | 身份模块 |

不改代码的：B2-B04（登记员工保留为独立聚合）、B2-B08（外部读取登记类别 entity）、B2-B11（允许 Manager 作角色称谓）、B2-B12（来源有效，已合入）。
