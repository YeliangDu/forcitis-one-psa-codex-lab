# ADR-0058 补写：数据侧输入（交 one-architecture 起草）

- 日期：2026-10-22；作者：one-data。ADR 的起草与修订归 one-architecture（本会话只给数据侧事实与建议条文，不改 `docs/adr/ADR-0058.md`）。
- 已不用补的：子实体自有状态机的记法与序号口径已在 ADR-0075（决定 1、3、4 及 `aggregates.yaml` 登记），列名勘误见 E-009。以下只列 ADR-0058 正文与现行落地仍有出入、且 ADR-0075 没覆盖的几处。

| # | ADR-0058 现行字面 | 现行落地与依据 | 建议的补写 |
|---|---|---|---|
| 1 | 决定 1 `cause` 枚举（业务命令 / 冲销 / 更正 / 迁入 / 系统作业） | 模板列名 `cause_kind`（枚举类列带 `_kind`，`DdlNamingTest`）；取值集合以各 schema 的 CHECK 为准，迁入渠道取 `MIGRATION`（`STATE_TRANSITIONS_DESIGN.md` §2 对照表、`B31_PHYSICAL_MODEL_RULINGS.md` §5） | 决定 1 的字段名改读 `cause_kind`，取值英文代码与中文名对照由 one-glossary 登记（我只提 `BUSINESS_COMMAND` / `REVERSAL` / `CORRECTION` / `MIGRATION` / `SYSTEM_JOB` 作候选） |
| 2 | 决定 8「迁入时写一条初始转换，`from_state` 为空」 | 迁入的历史状态可能不在现行状态集合内：状态列 CHECK 取「现行 ∪ 仅历史状态」，另加 `origin_kind`（`NATIVE`/`MIGRATION`），仅历史状态只允许迁入行、为终态、触发器拒绝流出与原生进入（`B31_PHYSICAL_MODEL_RULINGS.md` §5） | 决定 8 补一款：迁入行的初始转换 `to_status` 可为仅历史状态；`state_transitions` 的状态并集 CHECK 随之包含；各表清单由迁入规格逐表列出 |
| 3 | 决定 1 `from_state`、`to_state`、`sequence_no`、`occurred_on`、`aggregate_type` | 模板已按命名守护改为 `from_status`、`to_status`、`transition_sequence_number`、`occurred_on_date`、`aggregate_type_code`（`STATE_TRANSITIONS_DESIGN.md`「列名更正」） | 若 E-009 勘误尚未覆盖全部五个列名，一并改读；`sequence_number` 与词条「流水号」冲突，故用 `transition_sequence_number`，不设豁免 |
| 4 | 决定 3 / 5：各 BC 同形表，表形状统一 | 按聚合成对限定合法流转的 CHECK 须逐个 (from, to) 成对写并用 `coalesce(…, false)` 包住（NULL 放行会误接受 `∅ → 终态`）；这是模板之外各 schema 自填的部分（`STATE_TRANSITIONS_DESIGN.md`「`status_of_aggregate` 的写法」） | 不必入 ADR；在决定 5 末尾加一句指向设计稿，守护由实现侧 IT 覆盖 |

范围外：#2 中仅历史状态的清单、在途（审批中）单据的迁入处理（ADR-0036 U-4）不在本补写内。
