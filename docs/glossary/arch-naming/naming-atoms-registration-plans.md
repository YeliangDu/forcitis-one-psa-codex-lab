# 元词语登记方案（N8R，仅方案）

日期：2026-10-17。派工卡 N8R。本文件只设计登记位置与检查，**没有新增任何正式词条、提议词条、消息键或命名规则**。初始候选取自本轮真实命名载体的缺口，逐词出处见 [盘点报告](naming-atoms-coverage.md) 与 `/tmp/n8r-atoms.json` 的 records；源字段、完整原文及行号均可复跑。

## 共用结构与职责

- one-glossary 管概念、定义、指称、受控词与同名异义初裁；业务 Admin 确认业务含义，会计用语经财务确认。one-architecture 管命名面的槽位、投影、格式及守护清单；one-quality 承接检查实现。会话名表示建议归属，排期由 one-coordinator 安排。
- 名词及修饰概念进 `glossary/` 并入源，沿 ADR-0053 决定2使用概念＋指称；类别另用 `atom_kind`，不拿元词语分类替代 DDD `kind`。至少有 `concept_ref / atom_kind / definition / bc / scope / designations / relations / status / decision_ref`，概念引用用稳定 id，不从 id 拆名字。
- 只有语法形态的行引用动词或模板，不新建业务概念。snake、camel、kebab 一律从代码角色的英文 ASCII 首选指称机械投影；不手填第二份名字。是否可用按审批状态与使用状态共同检查。
- 以下文件均是**建议新增位置**。正式落地须由 NAMING_STANDARD 逐面指向它们；本卡不修改该入口。规则仍来自 ADR-0053，不以本方案替代已采纳 ADR。

## 1. 事件后缀与体标记：把现有文本规则结构化

**谁管**：one-glossary 管动词词形与词汇化例外；one-architecture 管模板；one-quality 管校验。

**放哪、什么结构**：建议新增 `glossary/verbs.yaml` 和 `glossary/naming-templates.yaml`。动词行有 `verb_ref / forms{base, participle, action_noun} / language / code_role / status / decision_ref`；模板行有 `template_ref / surface / slots / aspect / render / exception_reason`。perfective、pending、progressive 是结构枚举；已、待、中等是语言渲染片段，不冒充 aggregate/entity。`BecameOverdue` 为词汇化事实模板；不登记一个通用 `Event` 后缀。

**初始候选**：已、待、中、已被；Submitted、Approved、Registered、Changed、Released、Confirmed、Rejected、BecameOverdue（实际文件词形见盘点）。这些已有 N4 文本依据，本轮的缺口是机器可读登记，不能解读为又发现了57个新业务概念。Released 必须引用具体受控动词，不能把释放、放行、解除暂停混成一义。

**怎么检查**：检查槽位引用存在、命令/事件的 aspect 合法、所需动词形态齐全、渲染值等于登记指称；事件仍禁 Event/Request/Command 后缀。缺 action_noun 不自动加 ing；未登记的词汇化例外失败并输出登记草稿。为对象中含 Locked、末尾 Changed 的事件加反例，不能将两个词都当主事件后缀。

**与 ADR 的关系**：决定3的槽位正向生成、决定1第5条缺失失败、决定7第1–2条职责；N4 §3–4只是候选细则来源。NM1 已认可中文的部分直接沿用，英文词形由设计方定，不重新问用户英语拼写。

## 2. 界面固定用语：完整消息登记，业务名词用占位符

**谁管**：前端与服务端文字负责会话编消息目录；one-glossary 审业务指称；one-architecture 定共用消息契约；one-quality 接守护。

**放哪、什么结构**：建议新增 `glossary/messages/zh-CN.yaml` 作为消息目录源，前后端各生成适用格式。每条含 `key / pattern / placeholders / use_sites / status / decision_ref`；占位符有类型及可选 `concept_ref + designation_role`。消息用 CLDR/ICU 语法；源语言 zh-CN、当前只交付中文沿已定方向，不新增启用语言。消息键是稳定用途标识，不由概念 id 解析名字。

**初始候选**：保存、保存修改、取消、关闭、加载中、待办加载失败、登录已失效，请重新登录、内容刚刚被其他人修改，请刷新后重试。完整候选还包括带变量的消息，迁入时恢复成类型化占位符，不能把 `${...}` 程序表达式原样变成业务术语。

**怎么检查**：前端 `check-copy-glossary.mjs` 与后端 `OutwardTextGuardTest` 改验消息键存在、无页面写死文案、占位符类型和概念引用存在；已启用语言完整性构建失败。运行缺消息按源语言回退并告警，不显示键名。旧 copy-allow-list 只能辅助迁移，不能证明一条完整消息已登记。日志、测试文字和注释明确排除；服务端给人的标题、错误与通知纳入。

**与 ADR 的关系**：决定6第3–5条、决定1第3/5条。业务名词唯一来源在概念/指称源，整条消息在消息目录；两个层次各查各的。本轮318条是不同完整用语/模板的消息登记缺口，不能据此申请318个新业务概念。

## 3. 限定词：区分修饰语与计划对象

**谁管**：one-glossary 初裁；one-architecture 定允许出现的槽位与作用对象；业务含义由业务 Admin 确认，涉及费率/成本的限定由财务确认。

**放哪、什么结构**：建议 `glossary/modifiers.yaml`，行结构 `concept_ref / atom_kind: qualifier / modifies / value_domain_ref / designations / relations / status / decision_ref`；modifies 指定对象、量值或事实，不是自由文本补语。

**初始候选**：内部、外部、最终、标准、自动、默认，以及代码中的 internal。中文与英文这里只列不同词形，不凭字面宣布互译。“计划”作对象与作计划量的修饰语分别引用；现有 common_words 放行的 actual/current 等不伪报为新缺词，后续结构化时写入迁移对照。

**怎么检查**：限定词只在声明的对象/量值上使用；不得用任意字符串绕过槽位；检查标准成本、实际成本与计费费率引用各自概念。不可用不能误切成可用。新增含义先交 one-glossary，不能仅因英语同形就归并。

**与 ADR 的关系**：决定2第1–3条、决定3及决定7；NAMING_STANDARD §2.4列名限定词＋类别词。是否受控、放哪由设计方定，具体业务口径仍按已有决定，不能借登记改公式。

## 4. 方向词：按关系值域引用，不混同资金与会计方向

**谁管**：one-glossary 管定义；one-architecture 管槽位；外部集成会话核对来源/目标；借贷方向由财务确认。

**放哪、什么结构**：建议并入 `glossary/modifiers.yaml`，`atom_kind: direction`，增加 `relation_kind / inverse_ref / applies_to / value_domain_ref`。来源目标、入站出站、转入转出、借贷方向各自值域；已登记的动词只引用，不再造同义概念。

**初始候选**：outbound、目标、转入、转出。转入/转出在命令里可能是动作；登记前按该处语义引用现有动词或方向概念。当前白名单没有检出借方/贷方真缺词，不能用叙述文字补造缺口。

**怎么检查**：target 必须有对象引用；来源/目标方向成对校验适用关系；收入不是流入方向的同义词；借方/贷方不能直接映射收款/付款。对 ACL 范围与领域范围分别校验。

**与 ADR 的关系**：决定1第4条、决定2第1条、决定3、决定7第1条；会计方向含义不由设计方自行批准。

## 5. 表示词、技术后缀与关系语法：补结构，不另立业务名词

**谁管**：one-architecture 定格式与技术角色，one-glossary 管确有概念意义的关系；one-quality 补检查。

**放哪、什么结构**：建议 `glossary/naming-templates.yaml` 分 `technical_roles / representation_terms / connectors`；技术条目有 `token / surface / allowed_position / type_constraint / rule_ref`，不滥用 DDD kind。语义关系仍引用 concepts，语法连接片段不另设业务概念。

**初始候选**：flag、摘要、类别；关联、归属、用途。前一组与 NAMING_STANDARD §2.2/2.4 逐项核对，flag 不是任意可用列后缀；后一组先区分动作与关系引用。For/As/To/BecauseOf 等已有 N4 文本规则，按模板结构迁入；number 等规范已允许的类别词不再报成新词。

**怎么检查**：Repository、Controller、Request/Response 等只在指定位置；类别词检查类型；约束/索引按投影生成；连接片段只能出现在白名单模板中。And/Or 的字符串不能擅自改变一个业务事务的原子性。

**与 ADR 的关系**：决定1第3条、决定3、决定4、决定8。格式由设计方定，无需用户重新决定数据库类别词；业务关系的定义仍归业务确认。

## 6. 单位、业务分类及级别：按现有定义与实际缺口补，不凭零数造词

**谁管**：one-glossary 管单位/分类定义；one-data 管量值及单位契约；业务 Admin 维护业务分类，财务确认金额、基数、费率定义；one-architecture 核对值域结构。

**放哪、什么结构**：建议 `glossary/units.yaml` 使用 `concept_ref / quantity_kind / symbol / conversion_ref / designations / status`；分类与等级进所属 BC 的概念并入源，带 `value_domain_ref / values / ordering / owner / decision_ref`。已定主数据对象不改成词袋或生命周期枚举。

**初始候选**：单位类 millis（见实际方法声明）；业务分类类服务产品。单位与日期天、工时值对象分开；millis 的准确量纲/英文指称由设计方核对库契约后定。级别类本轮无真缺词，不虚构初始缺词清单；已有职级、客户分级等只做结构迁移核对。团队字段“专业”的具体含义留 NM5-03，它目前只在参数证据中出现，不进入本轮真缺词数。

**怎么检查**：量值绑定明确量纲/单位，币种值域单列；日历日期不是 Duration；分类值属于声明的值域，等级只按显式 ordering 排序，不凭 Level 推定。业务分类取值不得自动当状态机取值。

**与 ADR 的关系**：决定2、3、4、7；NAMING_STANDARD §2.4及§2.6 A-7已有数量、币种和日期规则。服务产品与阶段类型模板作为主数据对象已定（intent 2026-10-15-215632-one-architecture-WBS-从商机报价出生-到项目完.md），不再问对象地位；具体目录值不足时保持未定。

## 落地次序与出口

1. one-glossary 根据本轮逐词出处初裁；NM5 三题由 one-coordinator 汇总转用户，涉及会计的定义再经财务确认。英语词形、结构位置、字段 schema 由设计方完成。
2. one-architecture 在 NAMING_STANDARD 登记每面格式、源、负责会话和守护/阶段；one-glossary 新建并入源，同时按 ADR-0053 决定5保留旧名/禁用指称与稳定概念引用。
3. one-quality 与前后端负责会话按 N6/N8 卡实现读取、正向渲染与反例检查；新旧投影逐项一致才切换。本文所列检查均为方案，尚未实现，不能以本轮静态统计或 precheck 通过替代它们。
