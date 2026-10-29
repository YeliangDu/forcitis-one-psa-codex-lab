# N3 待定字段的用语管家定稿（2026-10-17，one-glossary，回 one-architecture）

依据：ADR-0053 决定 1.5（未启用的语言与角色不要求登记）、决定 2.2（本 ADR 只定一个角色「代码名」，其余角色枚举属细则）、AN-04（只交付中文）、AN-05（英文界面叫法与内部英文名分开登记）、CS-1.8（只实现当前阶段）。这些属设计方可自定的结构问题，不需用户判断；业务用语的取值仍以用户答复为准。

| # | 问题 | 定稿 | 理由 |
|---|---|---|---|
| 1 | 其余角色（terse、verbose、column_header、documentation）；界面用途是否与 canonical 分开 | 角色枚举定为 canonical、code_name 两个现用角色，terse、verbose、column_header、documentation、ui_label 列为「预留，未启用」：枚举里有，登记缺失不失败。界面文字现阶段就取 zh-CN canonical，不分开；出现同一概念在界面须用不同于 canonical 的叫法时，再启用 ui_label | 只交付中文；CS-1.8 |
| 2 | 缩写是角色还是属性 | 属性：指称上加布尔 `abbreviation`（缩写形式），不占角色。角色表示用途，缩写表示形式，两个维度分开 | 同一缩写可同时用于代码名和界面 |
| 3 | alias/forbidden 到 usage_status + source_type | **改定稿，对齐已合入的工具**（#461，`convert_entry`）：source_type 原样保留旧词表的类型值（dialect、old-code、legacy-code、legacy-table、standard-zh、zh-internal、undetermined、kpi-symbol 等；forbidden 清单项记 forbidden；首选名记 registered），不做重命名映射，保证往返可还原；usage_status：旧别名 deprecated 为真，或类型属 dialect、forbidden、old-code、legacy-code、legacy-table → forbidden，其余 → permitted；审批状态：旧类型 undetermined → pending_confirmation（待确认，禁止使用），其余沿用概念状态（approved/proposed）；standard-zh 且 deprecated 即「permitted 但不再首选」由 deprecated 属性表达。语义分类（旧名、符号等）由细则按 source_type 查表给出，不进数据 | 往返无损；分类可随细则演进而不重写数据 |
| 4 | concept_ref 与 id | 不另立 concept_ref：概念 id 即引用键，任何引用处写 id；id 永不变、不含含义（ADR-0053 决定 1.1、1.2）。bc 作概念时仍以 bcs.yaml 的 id 为 id | 少一层间接 |
| 5 | verbs.yaml、naming-templates.yaml、messages/zh-CN.yaml 结构 | verbs.yaml：每项 id（V 编号，永不变）、zh、en（原形）、en_past_participle、snake、standard_action、merged_from（并入的近义词）、status。naming-templates.yaml：槽位 {object, verb, aspect, complement} 与每种目标（命令、事件、状态）的中文与英文渲染式、样例。messages/zh-CN.yaml：消息键 → {text（含占位符 `{noun:<概念 id>}` 与 `{verb:<V 编号>}`）、status、note}；不收业务名词字面串。结构的正式草案我随 NAM-1017-GLO-06（事件写法与消息目录补登记结构）出 | 沿用 N4 §4、REGISTRATION_PLANS |
| 6 | 消息状态取值 | proposed（提议）/ approved（已批准）/ deprecated（已弃用）/ retired（已退役），与概念生命周期一致；缺译回退按 ADR-0053 决定 6.4（运行时回退须告警） | 一套取值 |
| 7 | en 还是 en-US | `en`。locale 不区分拼写变体；英式、美式拼写（如 Recognise 与 Recognize）是另一件待用户裁定的事（N4 Q13），裁定前新代码词形按 N4 候选 | 英文只作代码名与英文界面叫法两种角色，不是多方言译文 |

下一步：N3 照写；上表第 5 项的 verbs.yaml、naming-templates.yaml、messages 草案由 GLO-06 产出后再交你核对。
