# 无测试的 P0 规则清单（ARC-1021-COO-10 第一批衍生）

状态：草稿｜2026-10-21｜负责：one-architecture

one-coordinator 2026-10-21 要求：「221 条有规则无测试先按 P0 排出清单」。以 `rules/RULE_INDEX.md`（`build_rule_index.py` 生成，全部 417 张规则卡）为准，减去批次 1 分支 `ssot/i09-acceptance/tests.yaml` 中被任一测试引用的编号，得无测试规则卡 313 张。GAPS.md 的 221 是按「规则文件正文出现过的编号」计的口径，此处按全部规则卡计，更全。INV 148 条均无测试引用，见第 4 节。

## 1. 立即补测试：P0、新规则或沿用（38 张；模块已建 24 张，未建 14 张）

模块已建的 24 张查代码后分四类（sonnet 只读核查）：
- **有测试缺编号 7 张**（377–382、291）：只需给现有测试补规则编号，并入存量映射批，不另写测试；291 的「RULE-273/291」写法须让抽取脚本识别；
- **已实现缺测试 2 张**（340、412）：交模块会话补测试或守护；
- **部分实现 3 张**（388、390、391）与**未实现 12 张**：属功能缺口，不是补测试，交 one-coordinator 按功能卡排（292 是已推迟到 Phase 2 的决定）。
模块未建的随建造批补，不单独派。

| 规则 | 领域 | 标题 | 状态 | 模块 | 实现状态（2026-10-21 查代码） |
|---|---|---|---|---|---|
| RULE-377 | approval | A task's deadline is 23:59 of the n-th workday after the workday the task is cre | 新规则无需评审 | 已建（platform.approval） | 已实现，有测试缺编号（ApprovalDeadlineIT） |
| RULE-378 | approval | The number of workdays and the escalation interval of a node are attributes of t | 新规则无需评审 | 已建（platform.approval） | 已实现，有测试缺编号（ApprovalDeadlineIT） |
| RULE-379 | approval | A task that is still unhandled at its deadline is recorded as overdue once and i | 新规则无需评审 | 已建（platform.approval） | 已实现，有测试缺编号（ApprovalDeadlineIT） |
| RULE-380 | approval | Still unhandled after the escalation point, the task goes to the holders' superi | 新规则无需评审 | 已建（platform.approval） | 已实现，有测试缺编号（ApprovalDeadlineIT） |
| RULE-381 | approval | With no superior known, an overdue task goes to the process administrator and ra | 新规则无需评审 | 已建（platform.approval） | 已实现，有测试缺编号（ApprovalDeadlineIT） |
| RULE-382 | approval | A deadline never approves, rejects or skips anything | 新规则无需评审 | 已建（platform.approval） | 已实现，有测试缺编号（ApprovalDeadlineIT） |
| RULE-407 | approval | A document that needs a contract or agreement is refused when it cites none that | 新规则无需评审 | 已建（platform.approval） | 未实现（合同域校验） |
| RULE-408 | approval | A business date in a closed period is booked into the open period and keeps the | 新规则无需评审 | 已建（platform.approval） | 未实现（关闭期间改记开放期间） |
| RULE-409 | approval | A first-time supplier or subcontractor must have passed counterparty admission; | 新规则无需评审 | 已建（platform.approval） | 未实现（往来单位准入） |
| RULE-410 | approval | A payee or receipt party that is not the contract counterparty's registered acco | 新规则无需评审 | 已建（platform.approval） | 未实现（收付款账户校验） |
| RULE-411 | approval | An invoice before the agreement is countersigned is allowed only for the advance | 新规则无需评审 | 已建（platform.approval） | 未实现（发票域） |
| RULE-340 | identity | Role code `super_admin` bypasses every permission and role check | 新规则无需评审 | 已建（platform.identity） | 已实现，缺测试（规则卡已被 ADR-0026 D-7 取代，按「super_admin 不绕过」补反向断言） |
| RULE-387 | identity | A revoked or merged department in a custom scope raises an alert and a scope rev | 新规则无需评审 | 已建（platform.identity） | 未实现（范围待复核项与告警） |
| RULE-394 | identity | An overdue permission adjustment is escalated, never revoked automatically | 新规则无需评审 | 已建（platform.identity） | 未实现（权限调整项模型） |
| RULE-388 | timesheet | A project line is approved by the project manager of that line's project; each p | 新规则无需评审 | 已建（cost.timesheet） | 部分实现（缺工时行与项目维度、项目无经理走管理员） |
| RULE-390 | timesheet | An indirect line is approved by the submitter's supervisor | 新规则无需评审 | 已建（cost.timesheet） | 部分实现（生产代码不设间接工时标志，分支只有测试能触发） |
| RULE-391 | timesheet | A timesheet line is project, pre-sales or indirect; any other kind, or a submiss | 新规则无需评审 | 已建（cost.timesheet） | 部分实现（缺行类型三选一校验，因无行模型） |
| RULE-397 | timesheet | A workday must be filed; a non-workday may be filed but never has to be | 新规则无需评审 | 已建（cost.timesheet） | 未实现（填报义务） |
| RULE-398 | timesheet | The minimum hours of a workday are the standard day minus the approved and pendi | 新规则无需评审 | 已建（cost.timesheet） | 未实现（请假读取） |
| RULE-399 | timesheet | Leave is never a line of the timesheet; it only reduces the hours that must be f | 新规则无需评审 | 已建（cost.timesheet） | 未实现（请假不成行） |
| RULE-403 | timesheet | Hours filed plus approved leave hours of one day are at most 15 | 新规则无需评审 | 已建（cost.timesheet） | 未实现（15 小时上限） |
| RULE-412 | timesheet | The timesheet context never reads an overtime approval or a comp-time balance; t | 新规则无需评审 | 已建（cost.timesheet） | 已实现，缺测试（反向满足，宜补架构守护：cost.timesheet 不依赖请假模块） |
| RULE-291 | 审批（platform.approval） | A rejection by an added co-signer is advisory only | 沿用 | 已建（platform.approval） | 已实现，有测试（CountersignIT 写作「RULE-273/291」，抽取未识别斜杠写法） |
| RULE-292 | 审批（platform.approval） | Row approval: any rejected row rejects the whole submission, but all rows still | 沿用 | 已建（platform.approval） | 未实现（按 phase1a/DEFERRED.md:18 推到 Phase 2，属推迟决定） |
| RULE-414 | opportunity | The owner of the opportunity decides to bid or not, with a checklist, a required | 新规则无需评审 | 未建，随建造批补 | 模块未建 |
| RULE-415 | opportunity | A decision to bid that falls below the margin floor, exceeds capacity or meets a | 新规则无需评审 | 未建，随建造批补 | 模块未建 |
| RULE-139 | 售前与报价 | Server rates always overwrite client rates; at submission every work package mus | 沿用 | 未建，随建造批补 | 模块未建 |
| RULE-140 | 售前与报价 | At submit, cost and margin are recalculated from the current rates before approv | 沿用 | 未建，随建造批补 | 模块未建 |
| RULE-141 | 售前与报价 | Cost-tree work package value = hours × billing rate; labour cost = hours × cost | 沿用 | 未建，随建造批补 | 模块未建 |
| RULE-142 | 售前与报价 | Final quote equals the sum of line amounts plus the tail adjustment | 沿用 | 未建，随建造批补 | 模块未建 |
| RULE-147 | 售前与报价 | Role rates come from the master-data schemes in effect today, by role and city, | 沿用 | 未建，随建造批补 | 模块未建 |
| RULE-155 | 售前与报价 | During approval clauses are locked, so the tail adjustment is effectively frozen | 沿用 | 未建，随建造批补 | 模块未建 |
| RULE-158 | 售前与报价 | Conditions for submitting a quotation for approval | 沿用 | 未建，随建造批补 | 模块未建 |
| RULE-159 | 售前与报价 | Clause amounts must each be greater than 0 and add up exactly to the final quote | 沿用 | 未建，随建造批补 | 模块未建 |
| RULE-160 | 售前与报价 | Approval-time adoption recalculates final amount and margin; quotation must stay | 沿用 | 未建，随建造批补 | 模块未建 |
| RULE-168 | 售前与报价 | Only DRAFT or REJECTED quotations can be edited, submitted or deleted | 沿用 | 未建，随建造批补 | 模块未建 |
| RULE-172 | 售前与报价 | Only the applicant can save, submit or delete a quotation | 沿用 | 未建，随建造批补 | 模块未建 |
| RULE-173 | 售前与报价 | Users without cost-edit permission cannot change cost inputs; work-package cost | 沿用 | 未建，随建造批补 | 模块未建 |

## 2. 先修正规则再补测试：P0、需修正（9 张）

| 规则 | 领域 | 标题 | 状态 | 模块 |
|---|---|---|---|---|
| RULE-144 | 售前与报价 | Gross margin is calculated on the ex-tax final quote using the header tax rate, | 需修正 | 未建，随建造批补 |
| RULE-145 | 售前与报价 | Approval-time edits recalculate the final quote from the stored line total plus | 需修正 | 未建，随建造批补 |
| RULE-169 | 售前与报价 | How BPM final results map to quotation outcomes | 需修正 | 未建，随建造批补 |
| RULE-265 | 审批（platform.approval） | The initiator of a customized OA process is the caller-supplied `creator`, other | 需修正 | 已建（platform.approval） |
| RULE-304 | 审批（platform.approval） | Reconcile job re-sends missed results from the bpm records | 需修正 | 已建（platform.approval） |
| RULE-308 | 审批（platform.approval） | End-of-process write-back of approve_status to the business table | 需修正 | 已建（platform.approval） |
| RULE-311 | 审批（platform.approval） | The task-result consumer routes by business key, and a hook replaces the handler | 需修正 | 已建（platform.approval） |
| RULE-337 | identity | The gateway does no authentication | 需修正 | 已建（platform.identity） |
| RULE-376 | identity | WeCom card callback takes the acting user from the button key, not from FromUser | 需修正 | 已建（platform.identity） |

## 3. 先评审再定：P0、老行为未评审（130 张）

老行为卡须先按 评审，决定沿用、修正或废弃，再定是否补测试；不在本批派。按领域分布：项目成本与核算 52、交付、预算与收入 37、工时与资源 30、售前与报价 7、审批（platform.approval） 4。

## 4. INV

148 条 INV 无一被测试引用：测试引用的是 RULE 编号，且多数 INV 所属 BC 尚未建造。已建 BC 的 INV 经 `c1-rules/rule_cards.yaml` 对上 RULE 后，由第 1、2 节间接覆盖；对不上的，随第二批（对象、命令、事件、聚合）登记时逐条判定。

## 5. 去向

- 第 1 节已建模块部分：交 one-coordinator 分给 platform.approval、cost.timesheet、platform.identity 的负责会话补验收测试（测试名带规则编号）。
- 第 2 节：交规则卡负责会话先修正。
- 第 3 节：交 one-rules 排评审。
