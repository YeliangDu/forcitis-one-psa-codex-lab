# 命名体系 ADR 细则素材：受控动词表、状态词表、渲染模板、结构草案、未登记名词（2026-10-17，one-glossary）

交 one-architecture 并入 ADR 与细则。全部是候选，不是裁定；业务用语以用户答复为准。输入：naming-research.md（one-coordinator）、`NAMING_ATOMS_PREP.md`。

## 1. 受控动词表候选

列：中文动词｜英文原形（命令）｜过去分词（事件、状态）｜snake｜对应标准动作（权限码，候选）｜并入或区分。取自 312 条命令名的首词频次，近义组各取一个，其余作别名；撤销、撤回、作废、取消语义不同，不并。

| 中文 | 英文原形 | 过去分词 | snake | 标准动作 | 并入或区分 |
|---|---|---|---|---|---|
| 登记 | Register | Registered | register | create | 记录 |
| 提交 | Submit | Submitted | submit | submit | 提出、申请、发起 |
| 编制 | Compile | Compiled | compile | create | 起草 |
| 标记 | Mark | Marked | mark | update | — |
| 驳回 | Reject | Rejected | reject | reject | 退回（退回另保留：退回给提交人修改） |
| 退回 | Return | Returned | return | return | — |
| 生效 | TakeEffect | Effective | take_effect | execute | 事件用「已生效」 |
| 确认 | Confirm | Confirmed | confirm | confirm | 「确认收入」用 recognise，另立动词「确认入账」待判 |
| 发布 | Publish | Published | publish | publish | — |
| 停用 | Deactivate | Deactivated | deactivate | disable | 暂停另立 |
| 启用 | Activate | Activated | activate | enable | — |
| 关闭 | Close | Closed | close | close | 终止、完成按语义分开 |
| 批准 | Approve | Approved | approve | approve | 通过（审批结果用「已通过」，动作用批准） |
| 认定 | Determine | Determined | determine | determine | — |
| 取消 | Cancel | Cancelled | cancel | cancel | 撤销（对已生效对象）、撤回（对在途申请）、作废（对单据）三者保留，不并 |
| 撤回 | Withdraw | Withdrawn | withdraw | withdraw | — |
| 作废 | Void | Voided | void | void | — |
| 撤销 | Revoke | Revoked | revoke | revoke | — |
| 释放 | Release | Released | release | release | — |
| 占用 | Reserve | Reserved | reserve | reserve | — |
| 锁定 | Lock | Locked | lock | lock | — |
| 完成 | Complete | Completed | complete | complete | — |
| 终止 | Terminate | Terminated | terminate | terminate | — |
| 变更 | Change | Changed | change | update | 更新、追加 |
| 审核 | Review | Reviewed | review | review | — |
| 关联 | Link | Linked | link | update | — |
| 封存 | Seal | Sealed | seal | seal | — |
| 归档 | Archive | Archived | archive | archive | — |
| 冲销 | Reverse | Reversed | reverse | reverse | 红冲方言 |
| 开具 | Issue | Issued | issue | issue | — |
| 核销 | WriteOff | WrittenOff | write_off | write_off | — |
| 认领 | Claim | Claimed | claim | claim | — |
| 恢复 | Restore | Restored | restore | restore | — |
| 暂停 | Suspend | Suspended | suspend | suspend | — |
| 创建 | Create | Created | create | create | 建立 |
| 开始 | Start | Started | start | start | — |

说明：
- 中文一词多义要拆：「确认」对应 confirm（核对无误）、acknowledge（收到）、recognise（确认收入，会计准则用语，建议用「确认入账」另立动词，待用户答复）。
- 动词表登记即定英文原形与过去分词（Approve→Approved，WriteOff→WrittenOff），避免各处各自变形。
- 「标准动作」列与 G43 的 standard_actions 对齐，待 one-architecture 核对权限码清单。

## 2. 状态词表候选

状态取值只用「已 + 动词」「待 + 动词」「动词 + 中」三类，其余裸词（在途、草案、拟派、可撤销、只读）判为词汇化状态，单独登记为状态取值词条，不进组合规则。

| 中文 | 英文 |
|---|---|
| 已通过 | Approved |
| 已驳回 | Rejected |
| 已退回 | Returned |
| 已确认 | Confirmed |
| 已生效 | Effective |
| 已关闭 | Closed |
| 已作废 | Voided |
| 已撤销 | Revoked |
| 已撤回 | Withdrawn |
| 已完成 | Completed |
| 已锁定 | Locked |
| 已发布 | Published |
| 待确认 | PendingConfirmation |
| 待审批 | PendingApproval |
| 待认领 | PendingClaim |
| 进行中 | InProgress |
| 审批中 | InApproval |
| 已停用 | Deactivated |

结构：状态集合名（如「期间状态」）是 value_object 或枚举，状态取值挂在集合下；「申请」「增员」「减员」「公示」「计划」是动作或名词，不是状态，改名或移出（抽样发现 5 个）。

## 3. {object, verb} 槽位渲染模板样例

语序中英相反，不做字符串翻译，由结构化槽位按 locale 渲染：

| 槽位 | 中文（zh-CN） | 英文（en，代码标识） |
|---|---|---|
| 命令 `{object: 项目, verb: 发布}` | 发布项目 | `PublishProject` |
| 事件 `{object: 项目, verb: 发布}` | 项目已发布 | `ProjectPublished` |
| 状态 `{verb: 审批, aspect: 中}` | 审批中 | `InApproval` |
| 状态 `{verb: 确认, aspect: 待}` | 待确认 | `PendingConfirmation` |
| 命令 `{object: 开票申请, verb: 作废}` | 作废开票申请 | `VoidBillingRequest` |
| 事件 `{object: 开票申请, verb: 作废}` | 开票申请已作废 | `BillingRequestVoided` |
| 命令 `{object: 付款条件项, verb: 标记, complement: 可结算}` | 标记付款条件项可结算 | `MarkPaymentTermSettleable` |

第 7 行是带补语的槽位，须在细则里规定「补语槽」的语法，否则抽样中约 30% 的复合动作（C 档）无法生成：优先拆成两个命令，拆不开的才开补语槽。

## 4. terms.yaml 改为「概念 + 指称列表」的结构草案

```yaml
concepts:
- id: billing_request            # 永久不变，沿用现词条 id
  kind: aggregate                # DDD 分类
  bc: receivables.wip
  scope: domain
  definition: ...
  standard_ref:                  # SKOS 关系
  - {rel: closeMatch, ref: ADR-0016}
  status: approved               # proposed / approved / deprecated / retired
  designations:                  # 指称列表（ISO 704 的 term）
  - {locale: zh-CN, name: 开票申请, role: canonical, status: preferred}
  - {locale: en, name: BillingRequest, role: canonical, status: preferred}
  - {locale: code.snake, name: billing_request, role: derived}   # 由 en 投影，不手写
  - {locale: zh-CN, name: 开票单, role: alias, status: forbidden, reason: 方言}
- id: approve_command
  kind: command
  bc: platform.approval
  composition: {verb: approve, object: task}   # 命令、事件、状态：槽位而不是手写名
  designations: []               # 由槽位 + 动词表渲染，写出供校验，不手写
```

要点：概念 id 永不变，改名只增一条指称；zh/en/snake 由指称投影，`aliases`、`forbidden` 并入指称的 status；`homonyms` 并入关系；展示名 = (概念, locale, role)，role 取 canonical / terse / verbose / column_header / documentation（XBRL 标签角色）。

## 5. 迁移脚本思路

见 `NAMING_ATOMS_PREP.md` 末节；补充：第 1 步抽取按词条逐条生成 `designations`，zh、en 为 canonical，snake 为 derived，aliases 中类型为方言或 forbidden 的转 `status: forbidden`；脚本输出新旧投影对比（zh、en、snake、aliases、forbidden 逐项相等），不相等即失败，保证等价迁移后再改名。

## 6. 未登记名词清单（按 bc；ADR 批准后第一批登记）

来源：全企业聚合稿的聚合名与架构业务对象名，对照 terms.yaml 与提议术语（草稿文件不算已登记）。名词先登记，命令、事件、状态才能生成。活动名当对象名的要先规范（见 `ARCH_NAME_AUDIT.md`）。

### accounting.cashbank
- 聚合：资金账户、银行流水
- 业务对象：—
### accounting.fixedasset
- 聚合：公司资产、资产类别与折旧政策版本
- 业务对象：—
### accounting.ledger
- 聚合：会计分录、受控手工凭证、口径转换规则版本、外部法定账对账、控制科目授权表版本、收付 ↔ 权责科目映射表版本、期初余额、期末结转模板版本、期末结转运行、期间余额快照、现金流量项目表版本、科目表版本
- 业务对象：会计科目
### accounting.period
- 聚合：第 13 期启用登记
- 业务对象：—
### accounting.reporting
- 聚合：报表行定义版本
- 业务对象：报表行定义
### accounting.tax
- 聚合：增值税、附加税、企业所得税申报
- 业务对象：增值税发票、法定申报
### contract.agreement
- 聚合：协议变更、框架协议限额
- 业务对象：—
### cost.costing
- 聚合：人工成本分配运行、分摊规则版本、分摊运行、售前投入归属登记、成本核算参数版本
- 业务对象：分摊规则
### delivery.acceptance
- 聚合：发包人提供资料、客户满意度调查、设计变更、证据、证据关联
- 业务对象：不符合项
### delivery.execution
- 聚合：交付计划版本、延期申请、资源分配、资源需求、项目团队、项目复盘、项目预算版本、项目风险与问题
- 业务对象：WBS 节点、项目团队成员、项目预算
### delivery.inspection
- 聚合：外部检测、检查、检查表模板版本、检查计划
- 业务对象：检查表模板
### expense.spend
- 聚合：出差申请、出差补贴标准版本、差旅订单月账单、费用事前申请、费用报销
- 业务对象：—
### km.knowledge
- 聚合：知识条目
- 业务对象：—
### masterdata.catalog
- 聚合：协议价目、服务产品版本、标准成本费率表版本、计费系数版本、阶段类型版本
- 业务对象：服务产品、标准成本费率表
### masterdata.counterparty
- 聚合：资质证书
- 业务对象：—
### operations.facts
- 聚合：过账
- 业务对象：—
### operations.planning
- 聚合：经营计划版本、预算账户
- 业务对象：—
### organization.administration
- 聚合：IT 资产使用记录、IT 资产维修单、IT 需求工单、员工活动申请、用印申请
- 业务对象：—
### organization.attendance
- 聚合：标准工时版本
- 业务对象：—
### organization.leave
- 聚合：假期余额账户、假期类型与额度规则、加班、月度考勤确认、考勤记录、请假
- 业务对象：—
### organization.payroll
- 聚合：专项附加扣除声明、个税扣缴与社保公积金申报、奖金核定、月度薪酬结算、激励方案版本、社保与公积金参保、薪酬规则版本
- 业务对象：人员薪酬档案、激励方案
### organization.workforce
- 聚合：劳动合同、招聘需求、注册执业证书、绩效评估、职位目录与职级
- 业务对象：人员
### payables.payment
- 聚合：付款、利润分配、合伙人出资、应付往来款项
- 业务对象：往来款项、资金收付
### presales.opportunity
- 聚合：客户活动、市场活动、报价版本、独立性检查、线索
- 业务对象：报价
### procurement.subcontracting
- 聚合：供方履约确认
- 业务对象：—
### receivables.collection
- 聚合：保证金与押金、应收往来款项、收款
- 业务对象：—
### receivables.wip
- 聚合：可触发确认的证据类型开放配置版本
- 业务对象：—
