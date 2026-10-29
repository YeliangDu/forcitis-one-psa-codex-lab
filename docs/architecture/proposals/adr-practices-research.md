# ADR 业界最佳实践研究与本项目优化方案（2026-10-16，只读研究）

状态：历史记录｜2026-10-16｜负责：one-architecture

## 0. 取证说明
一手来源均 2026-10-16 抓取。未核实：Backstage ADR 插件的文件名与前置字段规则（官方 README 被截断）、log4brains 最近提交日期、adr-tools 的 link/generate 子命令（页面未列）、Spotify 文章对已采纳 ADR 取代的做法（文中未述）。GOV.UK 服务手册无 ADR 页（404），改取 GOV.UK ADR 框架与 GDS Way。

## 1. 对照表
| 主题 | 主流做法（出处） | 本项目现状 | 差距 |
|---|---|---|---|
| 模板 | Nygard 四段：标题、背景、决定、后果，「一两页」，一份一个决定（cognitect.com/blog/2011/11/15/documenting-architecture-decisions，2011-11-15）。MADR 4.0.0（2024-09-17）有 bare/minimal/full 三档，必备仅「背景与问题、备选项、决定结果」，其余可选（adr.github.io/madr）。AWS 最低要求背景、决定、后果。Azure：「别把 ADR 写成设计指南，细节链接补充文档」（learn.microsoft.com/azure/well-architected/architect-role/architecture-decision-record，2026-04-10） | 九个固定小节，ADR-0024 起另加四个必备（影响面、迁移、回滚、守护）；ADR-0033 74KB、0022 39KB、0047 35KB，混入现状勘查、用户原话、清单 | 单份严重超重；无轻量档；没有「决定摘要」一眼可读 |
| 编号 | Nygard：顺序且永不复用。MADR：`NNNN-title.md`，可按子目录分类，号只在类别内唯一。log4brains 用日期前缀以避免合并冲突（github.com/thomvaill/log4brains，日期未核实）。GOV.UK 框架未规定编号 | 全局顺序号，ADR-0029 号被占着但文件不在 main，导致断号 | 无占号机制；撤回的号规则未写 |
| 状态 | Nygard：提议/采纳/弃用或取代。MADR 加 rejected。AWS 有 Rejected 且须写拒绝理由防重复讨论（docs.aws.amazon.com/prescriptive-guidance/latest/architectural-decision-records/adr-process.html）。GDS Way：不写 Proposed，用 PR 状态表示；main 上即已采纳（gds-way.digital.cabinet-office.gov.uk/standards/architecture-decisions.html，复核 2026-03-05） | 提议/已采纳/已被取代/已废弃，无「已拒绝」；状态行被塞入用户原话、证据链接、部分取代说明（ADR-0033 状态行数百字） | 状态行承担了元数据的所有职责；缺 rejected |
| 采纳后可否改 | AWS、Azure：不可改，取代并链接。joelparkerhenderson：理想不变，实践中允许加带日期的补注。GDS Way：澄清可原地改，已部分落地的决定另写新 ADR | 正文冻结，只改状态行；历史偏差表登记 | 符合；缺「纯澄清/勘误」的轻量通道，导致小事也开新 ADR |
| 取代与部分取代 | adr-tools `adr new -s 9` 一步新建并改旧状态（github.com/npryce/adr-tools，仅页面所见）。MADR：状态写「superseded by ADR-NNNN」。AWS：新 ADR 采纳后旧状态改 Superseded。部分取代各家未规范（未核实有标准写法） | 「取代」行自由文本，生成器只校验被取代方状态含「已被 ADR-本号 取代」；漏列取代对象不可发现 | 方向有校验，完备性无校验；部分取代是项目自创，且有价值 |
| 分类、标签、范围 | MADR：可选子目录分类。GOV.UK 框架：按影响范围四级升级与审批，必填字段含状态、日期、咨询对象，不含标签（gov.uk/government/publications/architectural-decision-record-framework，2025-11-04）。工具侧 log4brains 支持标签与按状态分组（页面所述不全，未核实） | 只有总表 | 无类别、BC、阶段元数据，找不到「某 BC 现行有效的决定」 |
| 粒度 | Azure：只记影响结构、关键质量属性或难以回退的选择。AWS：结构、非功能、依赖、接口、构建技术。joelparkerhenderson：临时变通、已有标准覆盖、单人小事不写。Spotify：几乎总是写，大改动先 RFC 再 ADR（engineering.atspotify.com/2020/04/when-should-i-write-an-architecture-decision-record，2020-04-14）。Google Cloud：有两个以上方案且无现成依据时写（docs.cloud.google.com/architecture/architecture-decision-records，2024-08-16） | 49 份里混有协作规则（0020）、清单（106 聚合归属）、参数 | 无「不写 ADR」的判据 |
| 评审与批准 | AWS：负责人提交，评审会 10 到 15 分钟读，行动项留在提议状态，采纳时加时间戳与干系人。joelparkerhenderson：一周评论期，每份有主责人，年度复审。GOV.UK：按影响范围分级批准 | 对抗评审（opus）加用户批准，用户原话入状态行 | 无提议期限，无负责人，无复审点 |
| 工具化 | adr-tools：init、new、supersede。log4brains：预览与静态站，CI 构建。ADR Guard、Decision Guardian 等 PR 守护（joelparkerhenderson 所列）。Backstage 解析 MADR 并索引（未核实细节） | build_adrs.py 在生成时校验（状态、小节顺序、必备小节、取代对称） | 校验只在生成时、只对已采纳；无占号、无草稿检查、无链接检查 |
| 反模式 | Nygard 与 Azure 均指向：过长、无理由、隐藏后果、写成设计文档 | 同左；另有「决定、勘查、清单、规则」混写 | 见下 |

Y-statement（Zimmermann，SATURN 2012，一句话：在…情境下，面对…，我们决定…而非…，以达成…，接受…）适合低风险或可回退决定（medium.com/@docsoc/y-statements-10eb07b5a177，二手，日期未核实）。

## 2. 对调度七条建议的结论
1. 草稿格式校验与模板：**符合**。补充：校验要对提议稿同样生效，且须明示生成器认的标题与「取代」行写法；提供 bare 档。
2. 提议稿进 adr/src 标「提议」，intent 与状态自动核对：**符合**。理由：这是业界共识（Nygard、AWS 的 Proposed）；GDS 用 PR 状态表示是可选替代，本项目有生成器，放 src 更直接。自动核对的口径：intent 中出现「批准某 ADR」而状态仍是提议，报警。
3. new_adr.py 占号：**修正**。顺序号保持，但占号应是「落到 main 的最小文件」（空壳提议稿加 PR），而不是分支手查；采纳前撤回的号保留并标「已撤回」，不复用（Nygard 永不复用；ADR-0029 现状正是缺此规则）。
4. 取代引用可校验：**符合并加强**。新增「取代清单完备性」检查：新 ADR 须声明「检索过的关键词或旧 ADR 号」，评审清单加一项逐条核对被改条款；工具只能校验格式，漏取代本质靠评审，别指望自动化。
5. ADR 只写决定，清单进 SSOT：**符合**。Azure 明确反对写成设计指南；ADR 引用清单的版本或提交号即可。同时规定「清单变动不需新 ADR，除非改变归属规则」。
6. 提议 7 天复审期限：**修正**。单人决策者加 AI 会话，7 天过期不应自动作废；改为超期标「待决」进调度台账，每周一次复审，决定为采纳、拒绝或搁置（搁置须写原因与重启条件）。joelparkerhenderson 的一周评论期是评论窗口，不是失效时限。
7. 编号不改，加类别与范围元数据：**符合**。不要按类别拆目录或另设前缀（MADR 子目录会令号失去全局唯一，且现有 49 份引用全为 ADR-NNNN）。协作规则写进 METHODS.md：**符合**，ADR-0020 不追改，由新规则注明其性质。

**调度未想到的**
- 状态行过载：用户原话、证据、部分取代说明都挤在状态行，机器只能做子串匹配。拆为「状态」「批准依据」「部分取代说明」三个字段，生成器仍渲染原样。
- 缺「已拒绝」状态（AWS：写理由防重复讨论）；ADR-0029 这类长期无结论的应有出口。
- 缺「撤回」与「澄清」通道：纯笔误或链接失效不必开新 ADR，可用历史偏差表登记（现已有），把它规则化为「勘误」类别。
- 缺决策者字段（MADR decision-makers）；本项目恒为用户，可固定省略，但「评审人（opus 评审编号）」值得结构化。
- 大 ADR 拆分（Azure：分阶段决定拆多份）：ADR-0033 74KB 是 10 个以上决定合订，取代时只能「第 N 项第 M 条」，正是漏取代的温床。新 ADR 一份一个决定，超过约 20KB 须拆。

## 3. 推荐方案（按优先级）
| # | 做什么 | 谁做 | 要用户批 |
|---|---|---|---|
| P1 | 提议稿落 adr/src，生成器对提议稿也校验格式；`new_adr.py` 以「空壳 PR 进 main」占号；撤回的号标「已撤回」不复用 | one-quality 工具；one-architecture 写规则 | 是（改 ADR 规则本身） |
| P2 | 头部拆字段：状态、批准依据（原话与 intent）、取代（保持现写法）；旧稿不动，新稿起适用；加「已拒绝」状态与拒绝理由必填 | one-architecture 规则；one-quality 生成器 | 是 |
| P3 | 元数据：类别（技术栈、数据、边界、流程等枚举）、范围（BC、阶段）；生成按类别与 BC 分组的索引，另生成「现行有效」视图（剔除已取代） | one-quality | 是（含枚举值） |
| P4 | 评审清单：被改条款全文检索、取代完备性、一份一个决定、不放清单与参数；写入 `_readme_lifecycle.md` | one-architecture | 是 |
| P5 | 提议超期（默认 7 天）进调度台账周复审；intent 与状态自动核对脚本 | 调度流程；one-quality 脚本 | 期限与口径需批 |
| P6 | 协作规则不入 ADR，归 METHODS.md；ADR 粒度判据写入规则（见下） | one-architecture | 是 |
| P7 | 提供 bare 档（Y-statement 加影响面）用于可回退小决定；其余仍用全档 | one-architecture | 是 |

**粒度判据建议**：写 ADR 当且仅当决定影响结构、质量属性、依赖、对外接口或数据形状，且回退有成本；流程与协作规则进 PLAYBOOK，清单与参数进 SSOT 配置并受守护测试保护，设计细节进设计文档与 RFC 式提案（architecture/proposals），ADR 只引用。

## 4. 不建议做的事
- 不按类别拆目录、不改成日期或 slug 编号：现有引用全依赖 ADR-NNNN，收益小于迁移成本；单人加多会话，合并冲突靠占号即可解决，无需日期前缀。
- 不引入 log4brains、Backstage 等站点工具：只有一个仓库、一个读者，build_adrs.py 加索引足够。
- 不做多级升级审批（GOV.UK 四级）：决策者只有用户一人。
- 不对已采纳 ADR 追补新字段或回头拆分：冻结规则优先，只对新稿适用。
- 不追求「取代漏列」全自动检测：语义问题，交评审清单。
- 不做 7 天自动作废：会让真实未决事项悄悄消失。
