# ADR-0053 起草附注：链接核对、细则清单、批准页题目

状态：评审中｜2026-10-17｜负责：one-architecture

配套 `adr/src/ADR-0053.md`（提议）。依据 `NAMING_SYSTEM_RESEARCH.md`；one-glossary 合写素材 `glossary/arch-naming/NAMING_ATOMS_DRAFT.md`、`NAMING_ATOMS_PREP.md`（分支 `claude/one-glossary-bcs-systems` 提交 c544ab2d，下称「one-glossary c544ab2d」，全部是候选、不是裁定）。

## 1. 标准链接核对记录（2026-10-17 联网）

| 标准 | 调研所写 | 核对结果 | 在 ADR 中的用法 |
|---|---|---|---|
| ISO 704 | ISO 704:2009，https://www.iso.org/standard/38109.html | iso.org 拒绝抓取（403）。经瑞典 SIS、奥地利标准协会目录核实：**2009 版已撤销，被 ISO 704:2022 取代**（2022-07-05 批准，ISO 项目号 79077）。现行页应为 https://www.iso.org/standard/79077.html （未能直接打开） | 改引 ISO 704:2022，作「概念与指称分离」的参照；条文号不引 |
| ISO 1087 | ISO 1087:2019，https://www.iso.org/standard/62330.html | iso.org 403；经 BSI、加拿大标准委员会、AFNOR 目录核实为现行版，2025 年复审确认，取代 ISO 1087-1:2000 | 参照（术语「概念」「指称」）；条文号不引 |
| TBX | ISO 30042:2019，https://www.iso.org/standard/62510.html | iso.org 403；经爱沙尼亚 EVS、加拿大标准委员会目录核实 2019 版现行，范围含元模型、数据类别、TBX-Core 方言。`termType`、`administrativeStatus` 的规范定义**未核到原文**（只见厂商文档） | **不作硬依据**；未选用，另做研究；ADR 正文只在备选方案中提及 |
| W3C SKOS | https://www.w3.org/TR/skos-reference/ | 已核：W3C 推荐标准，2009-08-18；第 5 节 prefLabel / altLabel / hiddenLabel，第 8 节 broader / narrower，第 10 节 exactMatch / closeMatch | 硬依据（关系语义与指称使用状态的对应） |
| XBRL 标签角色 | XBRL 2.1，https://specifications.xbrl.org/ | XBRL 2.1 推荐标准（2003-12-31，勘误至 2013-02-20）页可打开，但未读到「标准标签角色」表原文；xbrl.org 指引页 https://www.xbrl.org/guidance/label-roles/ （2019-06-11，评审稿）列出标准、简称、全称、说明、期初、期末、合计等角色；`http://www.xbrl.org/2003/role/terseLabel` 等 URI 见于 SEC 申报文件 | 只作「参照」：ADR 正文写「参照 XBRL 标签角色」，角色枚举由细则自定，不声称与 XBRL 一致 |
| Unicode CLDR | https://cldr.unicode.org/ | 已核：最新正式版 CLDR 48.2（2026-03-17），CLDR 49 beta（2026-09-25） | 硬依据（语言数据、复数规则）；版本属细则 |
| ICU MessageFormat | https://unicode-org.github.io/icu/userguide/format_parse/messages/ | 已核：页面描述 plural、select；MF2 在 ICU4J 为技术预览 | 硬依据（复数、选择语义）；库与版本属细则 |
| MessageFormat 2 | https://unicode.org/reports/tr35/tr35-messageFormat.html | 已核：UTS #35 LDML 第 9 部分 MessageFormat，修订 48.2，自述「稳定文档，可作规范性引用」 | ADR 不定 MF1 还是 MF2，细则按前端库支持选 |
| BCP 47 | 调研未给链接 | 已核：https://www.rfc-editor.org/info/bcp47 = RFC 5646（语言标签）+ RFC 4647（匹配） | 硬依据 |
| PostgreSQL 标识符 | https://www.postgresql.org/docs/current/sql-syntax-lexical.html | 已核：`current` 现指 PG 18（18.6）；PG 17 页 https://www.postgresql.org/docs/17/sql-syntax-lexical.html 原文相同：`NAMEDATALEN` 默认 64，最长 63 字节，超长被截断（迁移不报错，文档未提示告警） | 硬依据；本项目 PG17，细则引 `/docs/17/` |
| DDD、Event Storming | Fowler、eventstorming.com | 未核（非本 ADR 新依据，ADR-0050 已定 DDD 参照） | ADR 不引 |

### 调研第 5 节第 6 条未核实项（2026-10-17 在仓库核）

- `DdlNamingTest` **不含** 63 字节检查（`backend/src/test/java/com/forcitis/one/DdlNamingTest.java` 无长度判断；只有 `TestPostgres.java:152` 对测试库名截断到 63 字节）。须补。
- 角色码**有**事实用法：`super_admin`（`platform_identity` 基线 `ck_ticket_issuers_no_administrator`）、`employee`（`config/platform_identity.properties` 默认角色）；无格式规范与守护。
- 审计动作名**有**事实用法：`AuditEntry.action` 为自由字符串，现值如 `organization.workforce.sync-run.released`、`platform.approval.task.denied`（形如 `<子域>.<bc>.<资源>.<动作>`）；无规范与守护。
- 查询参数规范**在** NAMING_STANDARD §2.6 A-4（camelCase、逗号多值、`sort=field,-field`）。
- 前端无 i18n 库（`frontend/` 各 `package.json` 无 intl / messageformat 依赖）。

## 2. 细则清单（将进 NAMING_STANDARD 等可演进文档）

归属：「细则」列为落点；「起草」为负责会话；守护列为现有或待建。

| # | 细则条目 | 落点 | 起草 | 现状与守护 |
|---|---|---|---|---|
| 1 | 概念—指称字段定义（字段名、角色枚举、使用状态、审批状态、来源类型、关系类型） | `glossary/` 结构说明 + NAMING_STANDARD §2.1 | one-glossary | 草案见 one-glossary c544ab2d §4；`term_rules.py` 改写 |
| 2 | 指称角色枚举：ADR 只定「代码名」；其余候选为标准名、简称、全称、说明、列头 | 同上 | one-glossary | 参照 XBRL，非一致声明 |
| 2a | 各已启用语言的必填角色清单（决定 1 第 5 条）；英文界面名能否与代码名不同（批准页第 2 题结论） | 同上 | one-glossary | 无 |
| 2b | 概念 id 写法：沿用可读 slug（同名异义 `<bc>__` 前缀）或改无含义编号；id 不得被投影或解析的检测法 | 同上 | one-glossary + one-architecture | 新守护 |
| 2c | 审批流：英文代码名指称、受控动词表、状态词表由 one-glossary 初裁、业务 Admin 批准，会计用语经财务（ADR 决定 7） | `glossary/` 说明 | one-glossary | — |
| 3 | 投影规则：各命名面取哪个语言与角色、大小写与分隔变换 | NAMING_STANDARD 新节 | one-architecture | 无 |
| 4 | 受控动词表（中文、英文原形、过去分词、snake、对应标准动作、近义并入） | NAMING_STANDARD 新节（数据放 `glossary/`） | one-glossary | 候选 35 个：登记、提交、编制、标记、驳回、退回、生效、确认、发布、停用、启用、关闭、批准、认定、取消、撤回、作废、撤销、释放、占用、锁定、完成、终止、变更、审核、关联、封存、归档、冲销、开具、核销、认领、恢复、暂停、创建、开始（出处 one-glossary c544ab2d §1）；「标准动作」列须与 G43 `standard_actions` 核对 |
| 5 | 状态词表：「已 + 动」「待 + 动」「动 + 中」三类，其余为词汇化状态单独登记；状态集合名归值对象 | 同上 | one-glossary | 候选 18 个（出处 one-glossary c544ab2d §2）；抽样 5 个非状态（申请、增员、减员、公示、计划）改名或移出 |
| 6 | 槽位与渲染模板：命令 动+宾 / `VerbObject`；事件 宾+已+动 / `ObjectVerbed`；状态三类；补语槽 | 同上 | one-glossary | 样例见 one-glossary c544ab2d §3 |
| 7 | 63 字节与缩写规则（约束、索引名超长时的缩写表） | NAMING_STANDARD §2.4 | one-architecture | `DdlNamingTest` 待补 |
| 8 | 触发器、函数、序列、报表视图与 BI 列命名 | NAMING_STANDARD §2.4、§7 | one-architecture | 空白；§7#18 只有 `rpt_` 前缀 |
| 9 | 角色码格式 | NAMING_STANDARD §7 | one-architecture | 空白；词表 30 个 role 概念 |
| 10 | 审计动作名格式（现值形如 `<子域>.<bc>.<资源>.<动作>`） | NAMING_STANDARD §7 | one-architecture | 空白 |
| 11 | 消息键约定、消息格式版本（MF1 / MF2）与库、占位符取指称、回退告警 | NAMING_STANDARD §4a + 前端规范 | one-architecture（前端会话实施） | 空白；`check-copy-glossary.mjs` 与 `OutwardTextGuardTest` 重写 |
| 12 | 源语言、启用的语言清单与交付阶段 | NAMING_STANDARD 多语言节 | 用户定（批准页第 1 题） | 无 |
| 12a | 地区格式（日期、数字、货币、时区按 CLDR 与 BCP 47 取）；打印单据、报表、通知模板的消息键约定 | NAMING_STANDARD 多语言节 + 前端规范 | one-architecture | 无（ADR 决定 6 第 4、5 条） |
| 12b | 业务数据多语言字段：哪些字段可按语言存、表结构放法（按语言标签的子表或 JSON 等候选，须适配研究） | DATA_DESIGN_CHECKLIST §14 数据设计 + NAMING_STANDARD §2.4a | one-architecture | 无（ADR 决定 6 第 6 条） |
| 12c | 新增命名面的格式与归属：数据库角色与账号、事件主题与队列名、缓存键、对象存储与附件路径、导入导出文件名与列头、通知模板编号、存库的枚举取值码 | NAMING_STANDARD §7 | one-architecture | 无，标「待建」 |
| 13 | 日志字段与追踪键 | NAMING_STANDARD §7#20 + GUIDING_PRINCIPLES §11 | one-architecture | G-O1、G-O3 待实现 |
| 14 | 测试名（以 `@DisplayName` 前缀引规则编号，CODING_STANDARD C-8 建议） | CODING_STANDARD + NAMING_STANDARD §5 | one-architecture | G-T4 待实现 |
| 15 | ACL 名称约定（外部系统名统一写法，只在集成层） | NAMING_STANDARD §7#19 + `glossary/acl/` | one-glossary | 只拦新增（ADR-0039 决定 7） |
| 16 | 文档与卡编号（永久编号不含义，展示编号生成） | 流程方法文档（ADR-0052 定稿后），NAMING_STANDARD 指向 | one-architecture | `name_id_check.py` 只出报告 |
| 17 | 分支、提交命名（CODING_STANDARD C-11：实际为 `claude/<会话名>`） | NAMING_STANDARD §7#21 | one-architecture | 文字过时 |
| 18 | 守护清单与盲区（G41–G48 已知盲区、运行时拼出的名字） | NAMING_STANDARD §4、§7 | one-architecture | 已有，随面补 |
| 19 | 词表迁移脚本：抽取（id 沿用）→ state 拆集合与取值 → 反解（名词, 动词）→ 按模板生成并与现名比对 → 校验不变量 → 切换并留兼容视图 | `glossary/` 脚本 | one-glossary | 思路出处 one-glossary c544ab2d PREP 末节、DRAFT §5；受影响读取方清单同出处 |
| 20 | 未登记名词按 bc 清单（ADR 批准后第一批登记） | `glossary/` | one-glossary | 出处 one-glossary c544ab2d DRAFT §6 |
| 21a | 命名面守护类别（原 ADR 稿决定 4 表的「守护类别」列，移此）：领域面——词表构建校验 + 后端词表测试；流程与架构——架构生成器；结构——架构测试 + 架构生成器；数据——DDL 测试；接口——契约测试；授权与运行——后端命名测试（运行时拼出者靠复审、登记盲区）；界面——前端检查 + 服务端外发文字测试、前端 lint 与结构检查；外部——ACL 归类 + 集成层隔离测试；工程——复审与 CI | NAMING_STANDARD §4、§7 | one-architecture | 随面补 |
| 21b | 文档中 `terms.yaml:<行号>` 式引用：新增即失败，存量（ADR-0031、ADR-0036、CROSS_CUTTING_MATRIX、CROSS_CUTTING_DECISIONS、PRESALES_OBJECTS_EVIDENCE）登记待清零；已采纳 ADR 走勘误 | 文档检查脚本 | one-architecture | 新守护 |
| 21c | NAMING_STANDARD §6 老名对照改为由来源类型「老名」的禁用指称投影 | NAMING_STANDARD §6 | one-glossary | — |
| 21 | NAMING_STANDARD §2.1 词源改写（现仍写 `03-通用语言.md`）、§7 改为归属表 | NAMING_STANDARD | one-architecture | ADR 采纳后第一步 |

### 需定（不进 ADR，交 one-glossary 与用户）

1. **复合动作的补语槽怎么建模**：如「标记付款条件项可结算」`MarkPaymentTermSettleable`。候选：优先拆成两个命令，拆不开才开补语槽；补语取值限已登记的形容词性词条或状态取值。抽样约 30% 的 C 档靠此（one-glossary c544ab2d §3）。
2. **会计「确认入账」是否另立动词**：已转为批准页第 3 题（须财务确认，ADR-0018 决定 4）。
3. 概念 id 写法（细则 2b）：ADR 已定 id 不承载含义、不得被投影或解析。保留可读 slug 便于评审与引用，但改英文名后 id 与名字不一致；改无含义编号则一次改动所有引用。起草倾向保留 slug。
4. 已有形式不一的命令 46、事件 95：已定统一改名（ADR 迁移方案第 3 条，载荷按 ADR-0035）。
5. kind 为 state 的词条拆「状态集合名」（改值对象）与「状态取值」：调研第 5 节第 5 条。

## 3. 批准页题目草稿

题型按 ADR-0041 决定 6：标准与选型题不预选推荐项，只写推荐与理由；业务口径与工作方式题可预选推荐。下列三题均只影响细则。

### 已定（调度审题 2026-10-17 收回，由 one-architecture 定，不上页）

- **词表切换**（原第 2 题）：一次切换为「概念 + 指称列表」，新旧投影逐项比对，保留兼容视图到读取方迁完；写进 ADR 迁移方案第 2 条。原选项「只加语言字段、不拆概念与指称」与 ADR 决定 2 冲突，已删去。
- **存量迁移**（原第 4 题）：先补名词（未登记对象 60、聚合 78），立受控动词表与状态词表，按组合规则生成命令、事件、状态并统一改名（含已登记但形式不一的命令 46、事件 95），生成不了的逐条人工登记；ADR 批准后执行，写进迁移方案第 3 条。

### 第 1 题：多语言做到哪一步（业务口径题，可预选；只影响细则）

| 选项 | 代价 | 收益 |
|---|---|---|
| A 只立结构：语言标签、代码名与界面名分开、消息键、占位符取词表；只交付 `zh-CN`，英文界面不排期 | 消息键与检查重写一次（中）；页面写文字多一步取键 | 以后加语言只加指称与消息，不改代码结构；无翻译与审校成本 |
| B 立结构，并在某阶段交付英文界面 | A 的代价 + 全部界面与消息翻译、业务审校、每次改文字双语维护 | 有英文用户时即用 |
| C 不立多语言结构，只中文（**与用户「按长期正确完整采用」相悖**，保留由用户定；与 ADR 决定 6 不符，选此项须另开 ADR） | 现在零代价；以后加语言要改全部页面与词表结构，越晚越贵 | 少一层间接 |

推荐 **A**（预选）：现有证据是「没有多语言需求」（`CROSS_CUTTING_MATRIX.md:173`），但结构现在最便宜，翻译交付等真实需求出现再定。

### 第 2 题：英文界面名能否与代码名不同（选型题，不预选；结论写入细则，对应 ADR 决定 6 第 3 条）

| 选项 | 代价 | 收益 |
|---|---|---|
| A 可以不同：英文代码名与英文界面名分开登记（默认同词，需要时界面名另取） | 英文界面上线时要逐条确认界面名 | 代码名可按准则与行业惯用（如 `ContractAsset`），界面名可按读者习惯；改界面名不动代码 |
| B 不可不同：英文代码名兼作英文界面名（一条两用） | 将来界面英文受代码名约束，或改界面名时连带改代码 | 少登记一条 |

推荐 A：现有词表里英文本就是代码名而非译文，B 会把两种角色混在一起。源语言（现行用语标准以中文准则原文为准，ADR-0018 决定 2）不在本题，登记在细则第 12 条。

### 第 3 题：会计「确认入账」是否单立动词（业务口径题，须财务确认，可预选）

背景：「确认」有 confirm（核对无误）、acknowledge（收到）、recognise（确认收入，《企业会计准则第 14 号》与 IFRS 15 用语）三义；受控动词表一个动词只对应一个英文原形（one-glossary c544ab2d §1）。

| 选项 | 代价 | 收益 |
|---|---|---|
| A 单立「确认入账」对应 recognise（`Recognise` / `Recognised`），「确认」只作 confirm | 须财务确认用语；口头「确认收入」在命令与事件中改为「确认入账」（界面显示名可另定） | 会计确认与一般核对在代码、事件、权限码中分开，不再一词多义 |
| B 只用「确认」一个动词，按对象区分含义（确认收入 = recognise） | 动词表一词两义，组合规则须按对象特判 | 沿用口头说法 |
| C 「确认收入」类整体作例外逐条登记，不进动词表 | 每个会计确认命令与事件都人工登记 | 不动动词表 |

推荐 **A**（预选，待财务确认）：准则把收入确认作专门概念，与一般核对分开最清楚。
