# 新增流程编号登记（one-architecture 分配；v1 已用到 PRC-135）

| 编号 | 名称 | 组 | owner_bc | 依据 | 分配日 |
|---|---|---|---|---|---|
| PRC-136 | 调岗（任职变更，按人事业务生效日，触发数据范围复核） | people | B-13 | B-13 为人事记录系统（施工方案 U-1、U-12，intent 134434）；P-05 数据范围 | 2026-10-16 |
| PRC-137 | 入职开通企业微信账号并绑定外部身份 | people | platform.identity（J-1：订阅 B-13 入职事件，沿用现有边 platform.identity → workforce / integration，无新边） | Q-03 附注「通过入职后开通企业微信账号，解决。」（intent 134134）；ADR-0039 决定 3 | 2026-10-16 |
| PRC-138 | 离职停用企业微信账号并解绑 | people | platform.identity（同 J-1） | 与 PRC-129（只吊销会话）互补；施工方案 go/no-go M-13 离职吊销 | 2026-10-16 |
| PRC-139 | 用印申请与用印登记 | people | B-31（S-09 已定） | 施工方案第 2 轮 C-3「迁入新系统，走通用审批」（intent 144943） | 2026-10-16 |
| PRC-140 | 合同借阅 | revenue | B-05 | C-4（借阅迁入通用审批） | 2026-10-16 |
| PRC-141 | 销售合同归档 | revenue | B-05 | C-4（归档并入合同模块的档案功能） | 2026-10-16 |
| PRC-142 | IT 需求工单 | people | B-31（S-09 已定） | C-5「都迁入新系统通用审批」 | 2026-10-16 |
| PRC-143 | IT 资产领用、调拨、报修、报废、盘点与采购申请 | people | B-31（S-09 已定）；用户「IT 资产是固定资产」（intent 2026-10-16-152926）：卡片与折旧归 B-24，B-31 只记领用、归还、维修工单，引用 B-24 卡片 ID（B-31 → B-24）；入账与报废的账务步骤由 finance 组在 B-24 流程中补 | C-5；IT 资产采购是否属非项目采购待财务（T-4） | 2026-10-16 |
| PRC-144 | 员工活动类申请（周年贺卡、节日活动、创新项目发布） | people | B-31（S-09 已定） | C-6「迁入新系统通用审批」 | 2026-10-16 |
| PRC-201 | 登记代发结果并补发薪酬 | people | B-15 | ARC-1022-COO-06：薪酬行子机 待支付 → 已实付 / 支付失败，补发后回到待支付（ADR-0075）；承载命令「登记代发结果」「发起薪酬补发」（`claude/one-arch-people-closeout` @c9b4922f，PAYROLL_LINE_PAYMENT.md J-3） | 2026-10-22 |
| PRC-202 | 维护默认账期覆盖规则（财务负责人批准生效） | revenue | B-09 | 用户 KPI-Q04（KPI-20261022-085652-r54）：默认账期 30 日，按协议类型、客户分级覆盖；ARC-1022-ARE-01（`claude/one-arch-revenue-closeout` @9af4685d，DEFAULT_PAYMENT_TERM.md），VS-15 | 2026-10-22 |
| PRC-203 | 按月计提奖金（one-architecture @3541ced0b 确认） | people | B-15 | S-6 裁定（one-architecture @cfc721a34 附三）：按月奖金计提归 B-15 计算并过账；ARC-1022-APE-01 | 2026-10-22 |

## 新增聚合编号（聚合稿已用到 AGG-110；AGG-111、112 不用，避免与 OBJ-111、112 混看）

| 编号 | 名称 | BC | 组 | 依据 | 分配日 |
|---|---|---|---|---|---|
| AGG-113 | 用印申请 | B-31 | people | S-09（intent 152807）；PRC-139 | 2026-10-16 |
| AGG-114 | IT 需求工单 | B-31 | people | S-09；PRC-142 | 2026-10-16 |
| AGG-115 | IT 资产使用记录（领用 / 调拨 / 归还 / 维修工单） | B-31 | people | S-09；PRC-143；卡片、折旧、报废账务在 B-24（AGG-91），按卡片 ID 引用 | 2026-10-16 |
| AGG-116 | 员工活动申请 | B-31 | people | S-09；PRC-144 | 2026-10-16 |
| AGG-117 | 法人归属（已建 `organization_workforce.legal_entity_attributions`） | B-13 | people | 已建表补登聚合号（Q-09 法人三口径） | 2026-10-16 |
| AGG-118 | 假期类型与额度规则（配置型，版本化） | B-27 | people | 聚合稿漏登，补号 | 2026-10-16 |

> 2026-10-16 起新号以 `process-catalog.yaml` 为准，本表只作历史记录。PRC-145 员工法人三口径不一致预警（事件策略，B-15，S-20）已直接登记于目录。
| AGG-119 | 薪酬规则版本（配置型，版本化：个税税率表、社保公积金比例与基数上下限等） | B-15 | people | one-arch-people 铺开（2026-10-16） | 2026-10-16 |
| AGG-120 | 专项附加扣除声明（从 AGG-93 拆出） | B-15 | people | S-32 | 2026-10-16 |
| AGG-121 | IT 资产维修单（从 AGG-115 拆出） | B-31 | people | S-32 | 2026-10-16 |
| AGG-122 | 售前投入归属登记 | B-07 | finance | S-31 SQ-18 | 2026-10-16 |
| AGG-123 | 成本核算参数版本 | B-07 | finance | S-31 SQ-18 | 2026-10-16 |
| AGG-124 | 资产类别与折旧政策版本 | B-24 | finance | S-31 SQ-19 | 2026-10-16 |
| AGG-125 | 期初余额 | B-20 | finance | S-31 SQ-21 | 2026-10-16 |
| AGG-126 | 勾稽差异案 | B-17 | finance | S-31 SQ-22 | 2026-10-16 |
| AGG-127 | 口径转换规则版本 | B-20 | finance | S-31 SQ-24 | 2026-10-16 |
| AGG-128 | 期末结转模板版本 | B-20 | finance | S-31 SQ-24 | 2026-10-16 |
| AGG-129 | 现金流量项目表版本 | B-20 | finance | S-31 SQ-24 | 2026-10-16 |
| AGG-130 | 费用事前申请 | B-12 | finance | S-31 SQ-26 | 2026-10-16 |
| AGG-131 | 差旅订单月账单 | B-12 | finance | S-31 SQ-27 | 2026-10-16 |
| AGG-132 | 出差申请 | B-12 | finance | S-31 SQ-28 | 2026-10-16 |
| AGG-133 | 出差补贴标准版本 | B-12 | finance | S-31 SQ-28 | 2026-10-16 |
| AGG-135 | 分管范围版本（`oversight_scope_versions`） | B-13 | people | 用户 LEB-06（LEB-20261022-082103-ioy）：人力维护、总经理批准，D15 版本化配置；one-arch-people DATA_MODEL 1.5（@c9b4922f） | 2026-10-22 |
| AGG-136 | 默认账期覆盖规则版本（`receivable_term_rule_versions`） | B-09 | revenue | 用户 KPI-Q04；作为一版业务规则（OBJ-124）维护（ARC-1022-ARE-01） | 2026-10-22 |
| AGG-137 | 标准成本费率卡版本 | B-07 | finance | 用户 FCQ 答复（intent 2026-10-22-103824）：奖金计提率挂在费率卡版本上；与 AGG-123 成本核算参数版本分立（费率卡有自己的版本与财务批准，报价、预算也引用）；one-arch-finance BONUS_OVERTIME_COSTING.md S-1 | 2026-10-22 |
| AGG-138 | 奖金计提运行（`bonus_accrual_runs`；技术聚合·运行单据，同 AGG-41；one-architecture @3541ced0b 确认） | B-15 | people | S-6；PRC-203；ARC-1022-APE-01 | 2026-10-22 |
