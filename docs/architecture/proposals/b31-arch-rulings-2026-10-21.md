# B-31 契约冻结：one-architecture 裁定

状态：评审中｜2026-10-21｜负责：one-architecture

依据：one-1a-admin 契约冻结 IMP-1021-ADM-01（`claude/one-1a-admin-skeleton` @d2f0b69d，`proposals/closeout/people/b31-rules/B31_CONTRACT.md` §6、`B31_ALIGNMENT_AUDIT.md`）。用户已定的 B31P、B31R 答复（intent 2026-10-20-232338、B31R 提交 `B31R-20261021-083733-ajz`、§11 补充 intent 提交 5efb6555）照办，本裁定只补架构手续，不改判用户答复。

需要新 ADR 的三项（第 2、5、6 项，合为 ADR-0074、ADR-0075 两份）由本会话起草。两份都部分取代已采纳 ADR（ADR-0051、ADR-0058），按 ADR 生命周期第 6 节走正式档，用户批准后生效。起草期间，B-31 可以按本裁定设计，但不得落地受影响的那部分代码（ADR-0066 决定 7「可以按已采纳的 ADR 设计，代码不落地」的同一原则）。

| # | 条目 | 裁定 | 依据 | 执行 |
|---|---|---|---|---|
| 1 | MOD-05：B-31 → `platform.scheduling` 边 | 去掉。B-31 的定时按统一写法自声明 `@Scheduled`＋`@SchedulerLock`，不依赖 scheduling。聚合稿里的这条边属设计文件改动，列入 SSOT 暂停令解除后的待办 | ADR-0066 决定 2 第 3 条（scheduling 改为 `kind: technical`）；`closeout/CONVENTIONS.md`「定时触发」 | one-1a-admin 在契约与骨架里不登这条边 |
| 2 | MOD-03：B-31 不在 ADR-0051 的 BC 清单与审批依赖清单里 | 起草 **ADR-0074**（正式档）：ADR-0051 决定 3 的 BC 清单增 `organization.administration`（依据用户 intent 2026-10-16-152807）；决定 7 第 3 条的含审批聚合增 AGG-113、AGG-114 与合同原件借阅（第 5 项）。`contexts.yaml` 现在就以 `status: planned`、`basis: intent` 登记，本会话随裁定代登。骨架与依赖边可以先建；在 ADR-0074 采纳之前，B-31 的模块不得合入 main | ADR-0051 决定 1 第 1 条（规范性内容要另开 ADR 取代）；`contexts.yaml` 头部「新 BC 先在此登记」 | one-architecture 起草 ADR-0074；one-1a-admin 骨架照做 |
| 3 | MOD-04：B-31 → `accounting.fixedasset` 的时点 | 先行期不建这条边，也不建 fixedasset。IT 资产卡片由 B-31 保存外部卡片号（老系统编号文本，原样进来源对照），作为本 BC 的值。W5 建 fixedasset 时，B-31 按 ADR-0067 决定 4 的兼容新增，加一个可空的资产卡片 ID 列并补连。`context-map.yaml` 这条边只标 planned | ADR-0051 决定 9 第 1 条（BC 不早于其上游可用）；ADR-0054（固定资产在 W5）；ADR-0067 决定 3 第 4 款、决定 4 | one-1a-admin |
| 4 | REF-01：实际用印核验的接口方向 | 按 ADR-0057：B-31 的 api 以（报价版本，法人，拟发文件的文件 ID 与内容哈希）为入参核验，**输出**用印登记 ID；登记 ID 不作入参 | ADR-0057 决定 2 第 1 条；CS-1.10 | one-1a-admin 改契约 |
| 5 | DATA-07、REF-04、APR-07：合同原件借阅 | 照用户 B31R-Q8～Q10：先行期记在 B-31，按 AGG-13 的目标形状建（INV-125 部分唯一索引第一版即建），W3 只移交所有权与界面、不洗数据。跨 BC 移交的手续写进 **ADR-0074**：W3 时由 `contract.agreement` 经领域入口接收（ADR-0058 决定 8 的迁入写法，`cause` 记为移交），B-31 对应的表转为只读，并在移交完成后按 retired-module-schemas 的做法退役；B-31 期间「协议已归档」这一前置用老系统合同编号代位，并保留对照。借阅审批成为第三类人审：设计 §1.1、§3.8、§4.1、§8.4 的「两类人审」改为三类；借阅审批要有自己的流程键、单据类型码和审批人表。REF-04 的依据行改为引用 B31R-Q8 | 用户 B31R-Q8～Q10；ADR-0058 决定 3、决定 8；CS-1.8 | one-architecture 写进 ADR-0074；one-1a-admin 改设计 |
| 6 | DATA-01、DATA-02：一个聚合有多台子状态机；一条命令触发多条转换 | 起草 **ADR-0075**（正式档，ADR-0058 补篇），采 one-data 方案（`data/B31_PHYSICAL_MODEL_RULINGS.md` §4）：子状态机各自记转换，主体取子行（类型码为子实体类型、ID 为子行 ID、序号为子行自己的 `version`），表形状与唯一键不变；同一行在一条命令内变几次，`version` 就增几次、各写一条；没有状态变化时不写转换，序号允许跳号；带状态机的子表行必须各自带 `version`，事务仍守在根上。本会话初稿的「加步序与子实体列」改列为备选未采用。ADR-0075 采纳前，AGG-113、AGG-115 的状态历史代码与 DDL 不落地 | ADR-0058 决定 1；DATA_DESIGN_CHECKLIST §14.1；one-data §4 | one-architecture 起草 ADR-0075；one-data 补设计 |
| 7 | DATA-09：离职事件一次改多个聚合 | 不登例外。消费方逐个聚合各开事务：先查出离职人仍在用的资产卡片，再逐个用例处理，幂等键为（离职事件 ID，卡片）；单个失败只重试该卡片，并告警 | DATA_DESIGN_CHECKLIST §14 第 1 项、G-T1 | one-1a-admin |
| 8 | APR-03：`ApprovalCompleted` 的结果映射 | 用印与 IT 统一按下表映射；状态词的中文名交 one-glossary 定（「已退回」现有词条义为 RETURNED，所以 IT 的「驳回→已退回」改为「已驳回」）。<br>REJECTED → 已驳回（本轮终止，发起人可重提新一轮）<br>RETURNED＋APPROVER → 已退回（退回发起人修改后重提）<br>RETURNED＋INITIATOR → 已撤回<br>RETURNED＋SYSTEM → 不进业务状态：单据留在提交前的状态，记下拒绝原因并告警（启动被拒）<br>CANCELLED → 已取消（终态）<br>聚合稿「已驳回、已撤回为提前终态」的写法，列入暂停令解除后的待办 | `ApprovalRequest.java:63-67、267-275`；ADR-0007 | one-1a-admin 写进契约；one-glossary 定名 |
| 9 | APR-04：来源审批的「已批准载荷及份数」读取接口 | 不纳入先行期。先行期唯一的来源是 Q7 过渡通道（老系统单号＋批准页），这时还没有新系统的报价或合同审批可读。把它从 §7.4 的阻塞前置里移出；等第一个来源 BC（B-03，W2）建成时，与它同批在审批 api 增加，走 ADR-0067 的兼容新增（加带默认实现的新方法） | CS-1.8；ADR-0067 决定 4；用户 B31R-Q7 | one-1a-admin 改设计；审批模块 owner 届时实现 |
| 10 | APR-02：平台枚举新增（流程键、权限码、审计主体种类、投递用途） | **更正（2026-10-21）**：现在是 W0、平台模块尚未冻结（ADR-0067 决定 3 第 1 条：冻结点为 W0 结束、W1 开工前），新增常量属 W0 正常建造，不走决定 5 的冻结通道，也不报用户。B-31 随施工卡新增 `B31_PLATFORM_CONSTANTS.md`（`claude/one-1a-admin-skeleton` @4635a528）所列 3 个流程键、30 个权限资源、8 个投递用途；one-architecture 把它们登记进收口窗口的批量审核清单，在冻结前随其他契约改动一并审（决定 3 第 2 条）。单据类型与审计主体照 ADR-0066 决定 4 走注册表；注册表改造卡须在收口窗口内完成，B-31 若先于它用到，先按现有写法，改造时一并迁。名称（特别是「过渡通道」三项须中性，不含老系统名）以 one-glossary 定名为准 | ADR-0066 决定 4；ADR-0067 决定 3、4、5 | one-1a-admin 照常建；one-architecture 登记批量审核清单 |
| 11 | SCH-02、SCH-05：周年待生成投影；待办与提醒的承载 | 周年：不建投影。由每日作业经 workforce 的 api 查当日满周年的在职员工，按（员工，周年年份）幂等生成 AGG-116；workforce 没有这个查询方法的，按兼容新增补。待办：本批不建通用待办（ADR-0066 决定 9 第 3 条，「提醒与待办」未准入）；季度复核、月度抽检、借阅逾期都做成 B-31 自己的到期列表（聚合上的到期时点列＋查询），提醒走企业微信投递。权限工单到期按 Q5 的「或」只做提醒，自动收回归 identity，不在本批。新的投递用途并入第 10 项的申请 | `CONVENTIONS.md`「定时触发」；ADR-0066 决定 9 第 3 条；用户 B31R-Q5、Q10 | one-1a-admin；integration owner（投递用途） |
| 12 | REF-03：「经通用审批办理」的措辞与用户后来的决定不符 | 用户的 B31 答复（2026-10-17）晚于 ADR-0054、0055、0064，取消了资产与活动的审批，以用户答复为准。ADR-0054 决定 1、ADR-0055 决定 1、ADR-0064 决定 9 各登记一条勘误，注明「资产与员工活动不经审批（用户 B31 答复 2026-10-17）」；B-31 设计 §1.1 标出这处偏差 | 用户 B31 答复；ADR 勘误机制（E-xxx） | one-architecture 登记勘误；one-1a-admin 改设计 |

另：SCH-03 的定时入口按先例，放在 `infrastructure/scheduling` 的薄类，再调用 application 用例，设计 §2.1 与 §5.1 统一按此写。MOD-01、MOD-02 已由文件服务准入判断（`ADMISSION_PLATFORM_FILE_2026-10-21.md`）与 `contexts.yaml` 补登 platform.signing 处理；organization.administration 随第 2 项代登。

## 补充：one-glossary 转来的四项（NAM-1021-GLO-11，`claude/one-glossary-gl-10` @b6f5dc81）

| 条目 | 裁定 |
|---|---|
| M17 / Q11 合同原件借阅的 bc | 照用户 B31R-Q8：先行期 bc 为 `organization.administration`，W3 移交 `contract.agreement`；移交手续另立 ADR（ADR-0074 拆出的借阅篇，与 one-data 对齐中）。词表登记用词「合同原件借阅」，bc 写 `organization.administration`，并注明 W3 移交。 |
| M12 / Q5 撤回与退回 | 同本文第 8 项：B-31 保留「已撤回」，对应平台 RETURNED＋INITIATOR；平台 RETURNED＋APPROVER 对应「已退回」；REJECTED 对应「已驳回」；CANCELLED 对应「已取消」。 |
| M3 软件许可 | B-31 本批只做设备，软件许可不在本批（CS-1.8）；设计 §1 范围与 §8 写明，词表定义保留。 |
| M19 设计 §8.2 与 `bcs.yaml` 说明 | §8.2 第 1 条按现词表改写（由 one-1a-admin）。`bcs.yaml` 第 396 行说明与现行决定不符，改为：「用印申请与登记、IT 需求工单、IT 资产领用调拨归还与维修、员工活动申请的业务记录；先行期含合同原件借阅（W3 移交 contract.agreement）；审批只用于用印与受控 IT 类别，走 platform.approval；资产卡片、折旧、报废与盘点账务归 accounting.fixedasset，采购由采购所属 BC 处理」。第 173 行与第 396 行重复登记，由 one-glossary 合并。 |
