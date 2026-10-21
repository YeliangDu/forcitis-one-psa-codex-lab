# W0 收口窗口：平台契约改动批量审核清单

状态：草稿｜2026-10-21｜负责：one-architecture

依据：ADR-0067 决定 3 第 2 条（冻结前 2 周收口窗口，契约改动集中批量审核）、决定 4（兼容新增判据）。冻结点为 W0 结束、W1 开工前。冻结前的新增属 W0 正常建造，登记在此，窗口内一次审完；冻结后改走决定 5。

| 来源 | 内容 | 所属层 | 登记日 | 状态 |
|---|---|---|---|---|
| B-31 行政事务（one-1a-admin，`B31_PLATFORM_CONSTANTS.md` @4635a528） | 流程键 3 个（`platform.approval.api.ProcessKey`） | 公共服务层 | 2026-10-21 | 待窗口审核 |
| 同上 | 权限资源 30 个（`platform.identity.api.Permission`，前缀 `organization.administration:`） | 基础层（identity） | 2026-10-21 | 待窗口审核 |
| 同上 | 投递用途 8 个与主体种类（`platform.integration.api.DeliveryPurpose`、`DeliverySubjectKind`） | 适配层 | 2026-10-21 | 待窗口审核 |
| ADR-0066 决定 4 | 单据类型注册表改造、`ProcessKey` 去掉 `DocumentType` 入参、审计主体种类改造 | 公共服务层、基础层 | 2026-10-21 | 须在窗口内完成 |

名称以 one-glossary 定名为准；「过渡通道」三项的资源名须中性，不含老系统名称（CLAUDE.md 第 10 条）。
