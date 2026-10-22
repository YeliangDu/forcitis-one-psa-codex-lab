# 行政事务模块（B-31）需新增的平台枚举常量

状态：起草，随施工卡加（W0 平台模块未冻结，不走冻结通道），one-architecture 已登记进收口窗口批量审核清单 `proposals/freeze/W0_CONTRACT_BATCH_REVIEW.md`｜2026-10-21｜负责：one-1a-admin

依据：`B31_ARCH_RULINGS_2026-10-21.md` 第 10、11 项及其更正（W0 平台模块未冻结，流程键、权限码、投递用途三类枚举新增属正常建造；单据类型与审计主体走注册表，不新增常量）。

英文名取自 `backend/glossary/proposed/organization-administration.yaml`，其中仍待 one-glossary 评审的词标「待定」；定名后本表跟改。常量随各施工卡落地，**申请时一次列全**。

## 1. 流程键（G47：`{SUBDOMAIN}_{BC}_{NAME}`）

**更正（one-architecture，`REG4_PROCESS_KEY_RULING_2026-10-22.md`）：不往 `platform.approval` 的 `ProcessKey` 枚举加常量。** 该枚举由 REG-4（one-1a-next-5）改成值对象，改完后三个流程键放在本模块自己的 api 里；REG-4 合入前，用到审批的卡（7.4 无来源用印、7.5 受控类别、借阅）只做不依赖流程键的部分。

| 常量 | 对应审批 | 路由标记 | 单据类型码 |
|---|---|---|---|
| `ORGANIZATION_ADMINISTRATION_SEAL_USAGE_APPROVAL` | 用印审批（无来源路径、特批、加批） | 需要（印章类型×用途、高风险用途决定加批分支） | `SEAL_USAGE_REQUEST`（待编号模块登记） |
| `ORGANIZATION_ADMINISTRATION_IT_SERVICE_APPROVAL` | 信息技术服务审批（仅类别配置为需审批） | 不需要 | `IT_SERVICE_TICKET` |
| `ORGANIZATION_ADMINISTRATION_CONTRACT_ORIGINAL_LOAN_APPROVAL` | 合同原件借阅审批（外借加审、续借为新审批） | 需要（外借） | `CONTRACT_ORIGINAL_LOAN_RECORD` |

流程定义文件各一份 `processes/{key}.bpmn20.xml`；审批人由本模块按配置解析后以 `FormFieldUsers` 传入。流程键与单据类型码的中文名、bc 归属待 one-glossary（`B31_TERM_GAPS.md` M18）。

## 2. 权限码（`platform.identity.api.Permission`，`organization.administration:{资源}:{动作}`）

动作只取标准动作 read create update delete submit approve reject export import execute。

| 资源 | 动作 | 说明 |
|---|---|---|
| `seal-usage-request` | read create update submit | 用印申请 |
| `seal-usage-request-withdrawal` | execute | 撤回 |
| `seal-usage-request-closure` | execute | 行政关闭 |
| `seal-usage-remaining-waiver` | execute | 放弃剩余份数 |
| `seal-usage-registration` | read create | 实际用印登记 |
| `seal-usage-approver-table` | read create approve | 用印审批人表；approve 表示定版 |
| `blank-page-pre-stamp` | read create execute | 预盖登记；execute 表示收回或作废 |
| `it-service-ticket` | read create update submit | |
| `it-service-ticket-acceptance` | execute | 受理 |
| `it-service-ticket-resolution` | execute | 解决 |
| `it-service-ticket-return` | execute | 退回 |
| `it-service-ticket-closure` | execute | 关闭（提出人） |
| `it-service-ticket-withdrawal` | execute | 撤回 |
| `it-service-ticket-reopening` | execute | 重开 |
| `it-service-category-table` | read create approve | 类别表；approve 表示定版 |
| `it-asset-card` | read create | 登记资产卡片（建根；名字待定） |
| `it-asset-issue` | read create execute | 领用 |
| `it-asset-transfer` | read create execute | 调拨 |
| `it-asset-transfer-revocation` | execute | 撤销调拨申请 |
| `it-asset-return` | execute | 归还 |
| `it-asset-repair` | read create execute | 报修与维修结果 |
| `position-asset-standard` | read create approve | 岗位资产配置标准 |
| `reminder-rule` | read create approve | 提醒规则定版 |
| `contract-original-loan-approver-table` | read create approve | 借阅审批人表定版（物理承载待 one-data） |
| `employee-activity-request` | read create | |
| `employee-activity-completion` | execute | |
| `employee-activity-cancellation` | execute | |
| `employee-activity-type` | read create approve | 活动类型 |
| `contract-original-loan-record` | read create execute | 借阅登记与归还 |
| `contract-original-loan-renewal` | execute | 续借 |
| `transition-channel-review` | read create | 过渡通道季度复核（资源名待定：中性用语） |
| `transition-channel-spot-check` | read create | 过渡通道月度抽检（同上） |
| `transition-channel-source` | read execute | 既有审批凭证核对（待定：不得含老系统名称） |

共 33 个资源、约 65 个权限码；审批任务操作复用审批平台权限，领用与活动不增加批准权限；印章台账维护归签章服务。

## 3. 投递用途与主体种类（`platform.integration.api.DeliveryPurpose`、`DeliverySubjectKind`）

现状只有审批任务与审批实例两种主体。B-31 的提醒经企业微信投递，按统一写法由本模块到点调用，用途与主体种类需新增（名字待定，待 one-glossary）：

| 用途（候选） | 主体种类（候选） | 触发 | 接收 |
|---|---|---|---|
| `IT_ASSET_RETURN_REMINDER_DUE` | `IT_ASSET_USAGE_PERIOD` | 离职生效满七个日历日仍未归还 | 原组织单元负责人 |
| `ANNIVERSARY_CARD_GENERATED` | `EMPLOYEE_ACTIVITY_REQUEST` | 满周年当日生成 | 员工关系负责人（施工设计 §10 行政清单；是否另通知本人待 one-rules） |
| `ACCESS_VALIDITY_REMINDER_DUE` | `IT_SERVICE_TICKET` | 权限工单有效期或复核日到期 | 待定（Q5 只说「提醒」，接收人待 one-rules） |
| `LOAN_OVERDUE_REMINDED` | `CONTRACT_ORIGINAL_LOAN_RECORD` | 借阅逾期 | 借阅人、其部门负责人 |
| `LOAN_OVERDUE_ESCALATED` | `CONTRACT_ORIGINAL_LOAN_RECORD` | 逾期满七天 | 行政负责人 |
| `TRANSITION_CHANNEL_REVIEW_DUE` | `TRANSITION_CHANNEL_REVIEW` | 每季度 | 行政负责人 |
| `TRANSITION_CHANNEL_SPOT_CHECK_DUE` | `TRANSITION_CHANNEL_SPOT_CHECK` | 每月 | 行政负责人 |
| `BACKUP_APPROVER_NOTIFIED` | `APPROVAL_TASK`（既有） | 第一审批人不可用，改派备用审批人 | 待定（§11.2 只说「事后知会」，接收人待 one-rules） |

接收人列标「待定」的不得以确定值进入收口窗口批量审核。投递用途名进的是平台共享枚举，`LOAN_`、`ANNIVERSARY_` 等是否加 bc 前缀由 one-architecture 在批量审核时定规则。

提醒投递失败须可重试、幂等键含固定到期时点（施工设计 §5.2）。到期列表是本模块聚合上的到期时点列与查询，不建通用待办（ADR-0066 决定 9 第 3 条）。

## 4. 不新增的

- 单据类型与审计主体种类：在编号模块单据类型注册表登记类型码，审计主体以类型码记录，不新增 `DocumentType`、`AuditSubjectKind` 常量。该注册表改造卡先于本模块用到它们。
- 定时作业锁名、指标名、配置键属 B-31 自身（G46），不是平台枚举。
