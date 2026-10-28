# 领域事件的分析必带字段（提议，交 1a 事件目录定稿）

- 日期：2026-10-14 · 会话：psa-olap-architecture · 关联：ADR-0025（OLAP 物理架构与数据流，起草中）、ADR-0016、ADR-0023、BI_SCOPE §3
- 用户决定（经 psa-coordinator 转达）：问「OLTP、OLAP 现在架构清晰吗」，选「现在写，并先定事件必带字段 (Recommended)」。
- 起点：psa-coordinator 给 1a 的临时信封——event_id、event_type、schema_version、occurred_at（Clock）、recorded_at、legal_entity_id（适用时）、accounting_period（会形成事实的事件）、aggregate_type / aggregate_id / aggregate_version、correlation_id / causation_id、actor（用户 / 系统）。
- 方法：从 L1 增量构建、L2 期间快照、重放与回填、与 OLTP 全量对账、口径版本变更后的重算，逐项倒推事件必须带什么；凡是只为分析而存在、OLTP 自己不需要的字段，都不放进信封。
- 序列化：JSON 键名按现有守护用 camelCase（`ApplicationIntegrationTest.serializedDomainEventKeysAreCamelCaseGlossaryWords`）。下文用 camelCase 书写。

## 1. 结论（相对临时信封的增删）

| 变化 | 字段 | 理由 |
|---|---|---|
| 保留 | `eventId`、`eventType`、`schemaVersion`、`occurredAt`、`recordedAt`、`aggregateType`、`aggregateId`、`aggregateVersion`、`correlationId`、`causationId` | 见 §2 |
| **新增** | `businessDate` | 期间归属看业务日期（客户签字日、开票日、工时日、支付日），不看系统发生时间。没有它，迟到事件和补录会落进错误的期间（K12「新签」按哪个日期判定，就是这类问题） |
| 修改 | `actor` → `actorType` + `actorId` | 分析只需要「谁」的稳定 ID 与类型（用户 / 系统，§6 第 5 条），不放姓名 |
| 修改 | `accountingPeriod` | 只在过账管道发布的事件上出现，由 `accounting.period` 判定后写入，**生产者不得自填**；业务单据事件只带 `businessDate` |
| 修改 | `legalEntityId` | 从「适用时」改为：凡属于某个法人的聚合一律必填，只有平台级（身份、编号规则）可空 |
| 移到负载 | 过账序号、冲销关系、原期间、税额拆分、维度引用、规则版本 | 这些只对会形成事实的事件有意义，放在过账事件的负载契约里（§3），不扩大所有事件的信封 |

## 2. 信封字段（所有领域事件）

| 字段 | 类型 | 可空 | 用途（分析侧） |
|---|---|---|---|
| `eventId` | UUIDv7 | 否 | 幂等键：L1 按它去重，重放与重复投递不产生重复行；v7 自带时间序，便于按时间分区 |
| `eventType` | 文本：`<子域>.<BC>.<事件名>` | 否 | 路由到 L1 的事实表；事件名取自 `terms.yaml` 的事件词条 |
| `schemaVersion` | 整数 | 否 | 负载演进：L1 按 (eventType, schemaVersion) 选解析器；旧版本事件在回填时仍能解析 |
| `occurredAt` | 时间戳（带时区，取自 Clock） | 否 | 状态变化在系统中发生的时刻；用于事件顺序与延迟监控，**不用于期间归属** |
| `recordedAt` | 时间戳 | 否 | 写入事件发布登记表的时刻；L1 的增量水位按 (`recordedAt`, `eventId`) 推进，迟到事件靠它被发现 |
| `aggregateType` | 文本 | 否 | 与 `aggregateId` 组成实体键 |
| `aggregateId` | UUID（该聚合自己的主键，§6 第 10 条） | 否 | 累积快照（合同、商机、条款）按它更新同一行 |
| `aggregateVersion` | 长整数，按聚合单调递增 | 否 | 乱序检测：L1 只接受 version = 已处理 + 1；出现空洞时挂起该聚合，等补齐或触发全量对账 |
| `correlationId` | UUID | 否 | 同一业务操作的全部事件串起来（例如审批通过 → 收入确认 → 过账），用于血缘与「为什么这个数变了」 |
| `causationId` | UUID | 是（根事件为空） | 直接上游事件 |
| `actorType` | 枚举：`USER`、`SYSTEM` | 否 | 区分人工操作与系统作业；历史迁入不再单列（§6 第 5、10 条） |
| `actorId` | UUID | 否 | 稳定 ID；`USER` 时为账号 ID，`SYSTEM` 时为系统主体目录里名字的 UUIDv5；**不带姓名** |
| `legalEntityId` | 类型化 ID | 仅平台级事件可空 | 法人是期间、账簿、授权的共同维度；L1 和 L2 都按 (期间, 法人) 分区 |
| `businessDate` | 日期 | 只有不形成业务事实的事件可空（例如登录、通知） | 期间归属；累积快照的里程碑日期 |

## 3. 过账事件的负载契约（`operations.facts` 发布的事实事件）

L1 的事实表以过账事件为准；业务 BC 的事件只用于累积快照与维度。

| 字段 | 可空 | 用途 |
|---|---|---|
| `postSeq` | 否 | 过账管道分配的单调序号（ADR-0016）；事实表的增量水位与全量对账以它为准 |
| `accountingPeriod` | 否 | 由 `accounting.period` 判定的入账期间 |
| `originalPeriod` | 是（仅关账后更正） | 关账后的更正落在开放期间，标注原期间（ADR-0016 第 2 条）；L2 用它出「本期对以前期间的更正」 |
| `reversalOfPostSeq` | 是（仅冲销） | 冲销行指向原行；L1 不删除、不更新原行，冲销行与原行同时存在 |
| `factType` | 否 | 过账类型（工时成本、费用、收入确认、开票、回款、付款……），对应 L1 事实表 |
| 金额 | 否 | 一律 `{amount, currency}`；收入、开票、费用类同时带 `amountExTax`、`taxAmount`、`taxRate`（按单据实际税率，K2） |
| 维度引用 | 按事实类型必填 | `projectId`、`contractId`、`clauseId`、`orgUnitId`、`personId`、`customerId`、`supplierId` 等**只放稳定 ID，不放名称**。L1 按 `businessDate` 解析当时有效的维度版本（Q10 按生效期保留历史） |
| `ruleVersion` | 是（用到可配置规则时必填） | 产生该事实时使用的规则或配置版本（科目映射 DMN、费率、分摊）；口径版本变更后，L1 能区分旧规则产生的事实（配置类变更不追溯） |
| `documentNumber` | 是 | 业务单据编号（ADR-0010），用于从分析下钻到单据 |

## 4. 约束（写进事件目录的规则）

1. 事件**不得携带敏感个人信息**（银行卡、证件号、手机号）和姓名；只带 ID。L1 不落这些字段，展示时经授权查询主数据。
2. 维度只带 ID，不带名称或冗余属性。改名、调岗不产生事实事件，而是维度事件。
3. 事件发布与业务写入在同一事务（Modulith 事件发布登记表）。L1 消费按 `eventId` 幂等，不依赖「恰好一次」。
4. 已发布的事件不改；更正一律是新事件（冲销、重述）。L1 不提供「更新事实行」的通道。
5. 每个事件类型在 AsyncAPI 中写明：是否形成事实、`businessDate` 取哪个业务日期、对应 L1 的哪张表或快照。

## 5. 不放进事件、由 L1 / L2 自己处理的

| 事项 | 为什么不放进事件 |
|---|---|
| 指标口径版本（`policyVersion`） | 口径在分析侧；同一事实可被不同口径版本解释。口径版本记在 L2 快照和语义层查询结果上 |
| 维度名称、层级路径 | 按 `businessDate` 从维度表解析，随生效期变化 |
| 期间是否已关账 | 由 L2 依据 `accounting.period` 的关账事件判断 |
| 汇率折算 | 事件只记交易币，折算在语义层（BI-olap ADR-0007 的做法沿用） |

## 6. 修订（ADR-0025 评审后，2026-10-14；以本节为准，覆盖上文冲突处）

architecture-critic（opus）评审 C-9、C-16 之后，ADR-0025 改为：财务事实由 L1 直接读 `operations.facts`，不依赖事件；事件只用于过程事实（如审批时效）与累积快照，监听器收到事件后从 OLTP 读取当前状态。据此修订信封：

1. **删除 `recordedAt`**：生产者序列化时拿不到发布登记表的写入时间，它与 `occurredAt` 重复；监控用发布登记表自己的时间列。
2. **期间规则**：
   - 过账事实的期间一律用 `accountingPeriod`（过账负载）；
   - `businessDate` 只用于解析维度版本，以及不过账的过程里程碑；
   - `businessDate` 按法人所在时区（Asia/Shanghai）截日；
   - 一个事件涉及多个业务日期（费用的权责日与支付日、开票日与到期日、合同签订日、生效日与回签日）时，`businessDate` 取 AsyncAPI 中为该事件类型声明的那一个，其余日期放在负载中，同样在 AsyncAPI 中声明。
3. **`aggregateVersion` 定义**：
   - 按聚合计数的事件序号，存在聚合上，每发一个事件加一；同一事务内对同一聚合发多个事件时连续编号；
   - 定时器或流程引擎触发的事件（如 `ApprovalTaskBecameOverdue`）取所属任务聚合的序号；
   - L1 **不做**版本连续性检查（乱序时从 OLTP 读当前态），它只用于对账与排查。
4. **跨聚合事件**：信封的 `aggregateType` / `aggregateId` 是主聚合；同一事务中改变的其他聚合列在负载 `relatedAggregates`（类型 + ID）中。例如开票同时改写 WIP（ADR-0016 第 5 条）。
5. **`actorType` 只取 `USER`、`SYSTEM`**。
6. **`documentNumber`** 是新系统的业务编号（ADR-0010），用于从分析下钻到单据；老系统或 A8 的原单号不进信封（见第 10 条）。
7. **`schemaVersion`** 只用于消费方解析在途事件；重建与回填一律从 OLTP 读，不重放旧事件。
8. **可推导约束**：信封与负载中的分析字段（`businessDate`、`documentNumber`、`ruleVersion`）必须同时落在 OLTP 的业务行上，保证 L1 从 OLTP 重建时不丢字段。
9. 过账事件仍按 §3 发布，供其他消费方使用；L1 不依赖它。
10. **信封不带来源标记**（ADR-0039 第 5 条第 1 款，R-1b P5 落地）：原生与迁入的区分、与老系统逐笔对账，由迁入专项的批次与核对表承担，不进事件信封；`aggregateId` 与 `actorId` 在信封中都是 UUID（ADR-0033 第 7 项第 1 条）。
