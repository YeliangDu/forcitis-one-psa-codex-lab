# NAM-1021-GLO-10 文件服务（platform.file）46 条提议术语评审（one-glossary，2026-10-21）

对象：`backend/glossary/proposed/platform-file.yaml` 46 条（领域词 26、错误码 20）与 `platform-file-areas.yaml` 3 条。依据：ADR-0060、0061、0070 已采纳，词随 ADR 批准；本评审只查重、同名异义、命名规则。结论：26 条领域词 24 条通过、2 处要改；错误码 20 条通过；技术词我直接定，没有需要用户定的新业务词。

## 要改的（交 one-platform-file）

| 项 | 问题 | 建议 |
|---|---|---|
| `pi` 含个人信息、`spi` 含敏感个人信息 | 概念已有：`personal_information` 个人信息、`sensitive_personal_information` 敏感个人信息（platform.audit，提议）；缩写 Pi / Spi 不是全称，也违反命名细则缩写须登记的要求 | 删这两条，个人信息标记的取值直接引用那两个词条；枚举常量 PI、SPI 改 PERSONAL_INFO、SENSITIVE_PERSONAL_INFO，错误码常量 SPI_NOT_ACCEPTED 同改（SENSITIVE_PERSONAL_INFO_NOT_ACCEPTED）；依据：个人信息保护法第 28 条的「敏感个人信息」 |
| `contains_macros` 含宏 | 布尔属性应以 is_ / has_ 开头（NAMING_STANDARD §2.3 布尔列） | en 改 HasMacros，snake has_macros；中文名「含宏」不变 |

## 通过的，附说明

- 内容哈希（ContentHash）：platform.approval 的 `ContentHash`（待审内容指纹）已在 GL 批改名为「内容摘要」ContentDigest（rules-batch-1021），两处不再同名；approval 会话下次改代码时改类名。
- 文件主体、上传主体：「主体」在词表里另有法人主体、客户方主体等，这里是（类型 + ID）的值对象，限定词「文件」「上传」够区分，通过。
- 封存登记（避开 seal）、保存类别、起算单据、约定保存年限：词来自已采纳 ADR-0060、0061，保存期限与数据保留期限的区分已写进定义，通过。
- 版本与派生：中文名是两个部分的合称，描述性，保留；如用户觉得别扭再改。
- 媒体类型：RFC 6838 与国标均称媒体类型，通过。
- 已隔离、未关联已过期、已处置、已迁入：文件状态与结果取值，通过；已迁入限于迁入结果，不与批次状态的「已生效」混。
- 临时下载链接与「外部一次性链接访问」不是一回事（前者是预签名下载地址，后者是给合作方的访问），互相在定义里提一句即可。
- 错误码 20 条：中文名全局唯一，查过无重名；通用错误未重复定义。
- 本会话新增 3 条存放位置词（临时区、检测暂存目录、隔离前缀，见 NAM-1021-GLO-04）与本表一致。

## 状态

评审结论不阻塞准入第 6 条。改名两处由 one-platform-file 在下次改契约时做；做完告诉我，我把两个词条转正。全部通过的词条在用户认可业务词后随文件服务上线一批转正式（本批没有新业务词待认可）。
