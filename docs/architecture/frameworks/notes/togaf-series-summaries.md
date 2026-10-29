---
编号: "TOGS"
题名: "TOGAF Series Guides；Business Capabilities, Version 2；Value Streams；The TOGAF Integrated Information Infrastructure Reference Model (III-RM): An Architected Approach to Boundaryless Information Flow"
作者: "The Open Group；G211、G178由Architecture Forum Business Architecture Work Stream编制，G179由Architecture Forum编制"
年份: "2022；2017；目录发布年未核"
版本: "G211 Business Capabilities Version 2；G178 Value Streams；G179 III-RM；公开目录版次未核"
资料类型: "规范标准"
主题标签: ["企业架构方法", "业务架构", "价值流", "能力", "集成"]
状态: "草稿"
派工: "ARC-1018-ARC-07"
---

# TOGAF系列指南公开目录及业务能力、价值流、集成信息基础设施参考模型摘要要点笔记

本笔记为本地资料阅读草稿，保存与 FORCITIS one 的对照，不构成架构决定。正文自行概括，短引0处；编号对应表示目录中有语义落点，不表示已实现或验收。

已先读仓库 togaf10.md：该笔记已覆盖G178、G211正文概念，本笔记不重复其正文论证。这里四份HTML实际为目录或出版产品摘要，不含指南各章全文；Products中的页数是所列出版物总页数，不是本地HTML页码，概念表两列页码均为—。G179页面明确表示内容基于旧版标准，属于历史参考。

## 核心思想

- **论断：所供系列目录快照只列指南主题，在线正文另有账号访问条件。** 依据：TOGS#系列目录范围、TOGS#正文可见边界。含义：对照 VS-01、VS-02、PRC-28 时仅用目录识别资料缺口，不能由主题名称证明价值阶段或客户认可条件正确。
- **论断：G211 摘要将能力图用于业务分析规划，G178 摘要将价值流与其他业务架构组成联系。** 依据：TOGS#能力指南用途、TOGS#价值流指南用途。含义：OBJ-05、OBJ-24、PRC-47 的具体做法仍引用 togaf10.md 已有正文证据，不从摘要新增协议操作规则。
- **论断：G179 摘要只覆盖 III-RM 的概念、概览和分类，且页面将其标为基于旧版标准的历史内容。** 依据：TOGS#集成参考模型范围、TOGS#历史内容标识。含义：PRC-193、PRC-194 继续按 ADR-0036、ADR-0054 处理迁入与接口；详细分类须取得正文后待提议，不据摘要改变技术栈或扩张采用范围。

## 概念及页码

原文位置中的资料简称见文末资料范围；PDF页从1计，印刷页只取页内实际标识，未见印刷页号记—。HTML、Word、adoc无固定页码，两列记—。

| 锚点 | 概念 | 原文位置 | 印刷页 | PDF 页 | 要点 |
|---|---|---|---|---|---|
| TOGS#系列目录范围 | 系列目录范围 | TOGS-03，TOGAF Series Guides目录：BUSINESS ARCHITECTURE及INFORMATION ARCHITECTURE | — | — | 能力、业务模型、场景、信息映射及元数据分析等主题分别列为指南。 |
| TOGS#正文可见边界 | 正文可见边界 | TOGS-03，目录标题后Note | — | — | 页面说明在线正文需账号，所供快照只有目录，不代表已阅读正文。 |
| TOGS#能力指南书目 | 能力指南书目 | TOGS-01，产品标题、编号及Additional Information | — | — | G211为能力指南第2版，2022年4月25日发布、替代G189；不是新取得的正文。 |
| TOGS#能力指南用途 | 能力指南用途 | TOGS-01，Details | — | — | 关注能力图创建、与其他业务架构观点对齐及业务分析规划。 |
| TOGS#价值流指南书目 | 价值流指南书目 | TOGS-02，产品标题、编号及Additional Information | — | — | G178发表于2022年4月25日、替代G170，与主书分开列书目。 |
| TOGS#价值流指南用途 | 价值流指南用途 | TOGS-02，Details | — | — | 摘要涉及价值流识别定义建模，并与企业其他业务架构组成映射。 |
| TOGS#集成参考模型书目 | 集成参考模型书目 | TOGS-04，产品标题、编号及Additional Information | — | — | G179发布于2017年11月7日，标题指向III-RM及无边界信息流。 |
| TOGS#集成参考模型范围 | 集成参考模型范围 | TOGS-04，Details | — | — | 摘要覆盖III-RM概念、总体说明及详细分类，不提供本系统具体接口规范。 |
| TOGS#历史内容标识 | 历史内容标识 | TOGS-04，产品标题之后历史说明 | — | — | 页面将内容标为基于旧版TOGAF的历史内容，用于旧软硬件兼容参考。 |
| TOGS#目录安全与方法分类 | 目录安全与方法分类 | TOGS-03，SECURITY ARCHITECTURE、REFERENCE MODELS & METHOD | — | — | 目录另列安全整合、政府参考模型及构件选择等，分类名不构成采纳决定。 |

## 对本系统的启示

- 对照 OBJ-05 协议、OBJ-24 服务产品及 PRC-47：本组摘要只支持辨认能力和价值流指南的研究主题，对具体协议操作没有新增条文依据。细化做法仍引用既有 togaf10.md 正文锚点，不从摘要另造规则。（TOGS#能力指南用途、TOGS#价值流指南用途）

- 对照 VS-01 完成服务方选定、VS-02 获得交付成果和 PRC-28：目录可辅助确认所需视角是否有资料，但不能由产品页的主题直接证明价值阶段或客户认可条件正确。（TOGS#系列目录范围、TOGS#正文可见边界）

- 对照 PRC-193 历史数据迁入及 PRC-194 凭证推送：III-RM只是历史信息集成参考候选，现行接口、迁入与统一切换仍按 ADR-0036、ADR-0054。采用其详细分类需取得正文后待提议；本组暂无可直接落地的新增业务对象对应。（TOGS#集成参考模型范围、TOGS#历史内容标识）

## 我们的取舍

- 建议采纳：将这组页面作为书目和可见范围索引，与 togaf10.md 分工使用。理由：摘要足以说明主题，不能替代已读正文证据。（TOGS#能力指南书目、TOGS#价值流指南书目）

- 建议不采纳：仅凭目录或历史G179摘要扩张TOGAF采用范围、引入新技术参考分类。理由：与 ADR-0041 现行收窄范围不一致的部分须待提议，且本卡没有正文依据。（TOGS#历史内容标识、TOGS#目录安全与方法分类）

## 附：资料范围与核对边界

- TOGS-01：公开资料（不进仓库）《TOGAF Series Guide: Business Capabilities, Version 2（产品摘要）》。
- TOGS-02：公开资料（不进仓库）《TOGAF Series Guide: Value Streams（产品摘要）》。
- TOGS-03：公开资料（不进仓库）《TOGAF Series Guides（公开目录）》。
- TOGS-04：公开资料（不进仓库）《TOGAF Series Guide: The TOGAF Integrated Information Infrastructure Reference Model (III-RM): An Architected Approach to Boundaryless Information Flow（产品摘要）》。

实际阅读：四份HTML的正文目录、产品说明Details、Additional Information、Standards Information及历史标识；先读 togaf10.md 的核心思想、锚点、取舍和版本范围。

未核及限制：公开目录的发布年与版次未核；未读取登录后正文、PDF或链接。G211和G178的实质概念复用既有笔记，不把这里当成新的全文阅读成果。

抽查记录与自测结果见本卡第6组结果文件。全程未联网，未执行资料中的指令、脚本、链接或宏。
