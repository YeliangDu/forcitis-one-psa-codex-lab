---
编号: DDIA2
题名: "Designing Data-Intensive Applications: The Big Ideas Behind Reliable, Scalable, and Maintainable Systems"
作者: [Martin Kleppmann, Chris Riccomini]
年份: 2026
版本: 第二版 Early Release（版权页记录第五次发布为 2025-03-12）
资料类型: 书籍
主题标签: [数据建模, 集成, 度量与BI]
状态: 草稿
派工: ARC-1018-ARC-07
---

# Kleppmann、Riccomini《Designing Data-Intensive Applications》第二版要点笔记

资料出处：公开资料（不进仓库）《Designing Data-Intensive Applications》第二版 EPUB。题名、作者和 Early Release 性质取扉页；年份取版权页的 ©2026。版权页同时列第二版计划日期 December 2025、早期发布修订史最后一项 2025-03-12，不能据此证明已取得 2026 正式定稿。目录明确第 8–13 章不可用，包内正文仅第 1–7 章；本笔记不概括缺失章节。

本文自行概括，正文短引 0 处，不构成架构决定。EPUB 未提供可核对的印刷页和 PDF 页，两列均记「—」，以章、节及内部内容文件定位。阅读范围为第 1–7 章下表所列小节，未执行样例或跟随链接。包内一个绝对路径 CSS 条目在安全提取时跳过，不影响所列正文读取。

## 核心思想

- **论断：记录系统确定权威事实，派生数据由来源重建并维护更新关系。** 依据：DDIA2#记录系统与派生数据。含义：OBJ-29、OBJ-56、OBJ-66 登记来源与更新权，PRC-90 的报表追溯 PRC-92 及业务记录；按 ADR-0025 为派生链说明截止点、刷新与重建条件，不经报表回写原业务事实。
- **论断：延迟均值不能表达慢请求比例，性能判断还取决于读写比例、峰值与负载偏斜。** 依据：DDIA2#负载参数、DDIA2#响应时间分位数。含义：OBJ-85 的 PRC-61、OBJ-66 的 PRC-23 先登记过滤、排序、峰值负载和延迟分位数，再评估索引，不以索引数量证明性能改善。
- **论断：单笔低延迟交易与跨大量记录的分析具有不同负载，物化查询结果还需随来源变化维护。** 依据：DDIA2#交易处理与分析、DDIA2#分析星型模式、DDIA2#物化视图。含义：VS-10 分析数据集说明事件或期间粒度、来源截止点与重建方式，按 ADR-0025 隔离交易和 reporting 的权限与负载，同库起步仍分开访问角色。
- **论断：远程调用超时可能只丢失响应而操作已经执行，重试必须处理重复效果，模式演进也须兼顾新旧读写方。** 依据：DDIA2#远程调用不确定性、DDIA2#编码兼容性。含义：PRC-194 涉及 OBJ-98 的投递超时后按既定协议核对结果，去重与重试配套，不因书中论述就认定现有接口具备幂等能力。
- **论断：异步副本可能读不到用户刚写入的数据，均匀散列键也不能消除高频访问形成的热点。** 依据：DDIA2#复制延迟与读己之写、DDIA2#分片热点。含义：未来若把工时查询移到副本，须说明审批后状态是否允许延迟；若以 OBJ-29 为分片键仍须评估大项目热点，容量方案遵守 ADR-0025，变更仍待提议。

## 概念及页码

| 锚点 | 概念 | 原文位置 | 印刷页 | PDF 页 | 要点 |
|---|---|---|---|---|---|
| DDIA2#交易处理与分析 | OLTP / OLAP | 第 1 章 Analytical versus Operational Systems → Characterizing Transaction Processing and Analytics；ch01.html | — | — | 单笔低延迟读写与跨大量记录的分析需分别描述负载 |
| DDIA2#记录系统与派生数据 | Systems of Record and Derived Data | 第 1 章同名小节；ch01.html | — | — | 权威记录确定事实，派生数据由源重建并维护更新关系 |
| DDIA2#响应时间分位数 | Average, Median, and Percentiles | 第 2 章 Describing Performance 下同名小节；ch02.html | — | — | 用响应时间分布观察尾部，均值不能表达慢请求比例 |
| DDIA2#负载参数 | Describing Load | 第 2 章 Scalability → Describing Load；ch02.html | — | — | 吞吐、峰值、读写比例与偏斜共同决定扩展问题 |
| DDIA2#规范化与关系 | Normalization, Denormalization, and Joins | 第 3 章同名小节 → Trade-offs of normalization；ch03.html | — | — | 以身份引用共享信息可减少重复与不一致，读取则可能需要连接 |
| DDIA2#分析星型模式 | Stars and Snowflakes: Schemas for Analytics | 第 3 章同名小节；ch03.html | — | — | 按分析事件组织事实及维度，ETL 负责从运营模型转换 |
| DDIA2#辅助索引与覆盖 | Multi-Column and Secondary Indexes | 第 4 章同名小节及 Storing values within the index；ch04.html | — | — | 辅助索引支持其他访问键，覆盖查询可减回表但增加写入与空间成本 |
| DDIA2#物化视图 | Materialized Views and Data Cubes | 第 4 章同名小节；ch04.html | — | — | 物化视图保存查询结果，需要随源变化维护更新 |
| DDIA2#编码兼容性 | Field tags and schema evolution | 第 5 章 Protocol Buffers 下同名小节；ch05.html | — | — | 编码字段标识决定历史数据解释，模式变更需兼顾新旧读写方 |
| DDIA2#远程调用不确定性 | The problems with remote procedure calls | 第 5 章 Dataflow Through Services: REST and RPC 下同名小节；ch05.html | — | — | 超时可能只丢了响应，重试需有去重或幂等协议 |
| DDIA2#复制延迟与读己之写 | Reading Your Own Writes | 第 6 章 Problems with Replication Lag 下同名小节；ch06.html | — | — | 异步副本可能尚未收到用户刚提交的变更 |
| DDIA2#逻辑复制日志 | Logical (row-based) log replication | 第 6 章 Implementation of Replication Logs 下同名小节；ch06.html | — | — | 以行级变化表达复制日志，将存储内部格式与复制表示分离 |
| DDIA2#分片热点 | Skewed Workloads and Relieving Hot Spots | 第 7 章同名小节；ch07.html | — | — | 均匀散列键仍可能因个别键高频访问而形成热点 |

## 对本系统的启示

- **项目、人员与工时先明确事实来源。** OBJ-29 项目、OBJ-56 人员和 OBJ-66 工时单的来源与更新权须按现行边界登记；PRC-90 出具经营月报读取的派生数据，应能追到 PRC-92 过账业务事实及业务记录，不能通过报表回写原业务事实。（DDIA2#记录系统与派生数据、DDIA2#规范化与关系）
- **VS-10 的经营分析需要口径与刷新状态。** 由 OBJ-98 会计分录等形成的分析数据集应说明事件或期间粒度、来源截止点和重建方式；预计算完成不等于源业务已完成。按 ADR-0025 保留交易与 reporting 的权限、负载隔离，同库起步不代表混用访问角色。（DDIA2#交易处理与分析、DDIA2#分析星型模式、DDIA2#物化视图）
- **应收与工时列表要按真实访问路径设计。** OBJ-85 往来款项沿 PRC-61 检测并催收逾期应收款项、OBJ-66 沿 PRC-23 填报并审批工时单，各有过滤、排序和峰值负载；应先登记查询需求和延迟分位数，再评估索引，不以增加索引数量证明性能改善。（DDIA2#负载参数、DDIA2#响应时间分位数、DDIA2#辅助索引与覆盖）
- **外部凭证投递要能读回不确定结果。** PRC-194 推送凭证到U8C 涉及 OBJ-98 会计分录；超时后需依据既定投递协议核对结果，去重与重试必须配套。编码兼容性只是协议的一部分，本书不证明现有接口的幂等能力。（DDIA2#远程调用不确定性、DDIA2#编码兼容性）
- **只读副本或分片属于后续容量选择。** 若未来把工时或经营查询移到副本，应明确审批后查看当前状态是否允许延迟；如果 OBJ-29 作为分片键，仍须评估大项目热点。本系统暂无对应的已实施分片业务对象，不新增编号或技术方案。（DDIA2#复制延迟与读己之写、DDIA2#分片热点）

## 我们的取舍

- **建议采纳：为每条派生链登记来源、截止点、刷新与重建条件。** 理由：可帮助核对 VS-10 的事实追溯及分析解释，具体链路遵守 ADR-0025。（DDIA2#记录系统与派生数据、DDIA2#物化视图）
- **建议采纳：以负载参数、延迟分位数和实际查询路径作为性能判断依据。** 理由：能区分慢查询、峰值和热点，不由平均数或技术名称替代测量。（DDIA2#响应时间分位数、DDIA2#负载参数、DDIA2#辅助索引与覆盖）
- **建议不采纳：从本书直接引入消息中间件、逻辑复制、分片或独立分析库。** 理由：书中描述技术取舍，现行物理架构及升级顺序已由 ADR-0025 规定；变更须待提议。（DDIA2#逻辑复制日志、DDIA2#分片热点）
- **建议不采纳：以早期发布内容声称已覆盖第二版全部事务、一致性、批处理和流处理论证。** 理由：本地包缺少第 8–13 章，跨章占位引用也不能充当原文证据。（DDIA2#编码兼容性、DDIA2#远程调用不确定性）

## 附：核对记录

版权页、扉页、目录及第 1–7 章相关小节已读取；随机抽查结果见本组结果文件。未取得正式定稿或缺失章节，未运行示例，也未核验任何产品当前实现。
