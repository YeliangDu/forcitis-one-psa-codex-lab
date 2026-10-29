# 评审模板 2：表结构（DDL、迁移、注册表、事件载荷、读模型）

通用做法、输出格式、七条通用盲区 B1–B7 见 [README.md](README.md)。**条目本身不在这里重复**：数据设计的全部检查条目、顺序、签发与交付，以 [`docs/data/data-design-gate.md`](../../data/data-design-gate.md)（one-data 维护）为准。本模板只规定评审员读什么、对着门禁的哪几处判、怎样与 one-data 的审查分工。

## 出题人填（只填这一栏）

- 产出：表结构 PR 号 / 分支，提交范围，对应卡的 Issue 号，one-data 的审查记录路径（`docs/data/reviews/<卡号>.md`）。
- 不填：「预期 diff 与实际一致」之类结论。

## 必读材料（自己读）

1. `DATA_DESIGN_GATE.md`：§1.2 顺序、§1.3 签发行与有效性判法、§1.4 表结构 PR 复核的五件事、§3 全部检查条目、附录 A（卡「数据设计」一节的材料）、附录 B（审查记录格式）。
2. 派工卡的「数据设计」节和「预期表结构 diff」（卡上写了的；旧 WORK_CARD §7 模板已删）。one-data 的审查记录含结论，**先独立判完 T1–T8 再读**，不一致的单列。
3. 实际 diff：`backend/src/main/resources/db/migration/**`、`backend/architecture/data-access-registry.yaml`、`data-classification.txt`、其他 `backend/architecture/*.txt` 清单的变化。
4. DATA_DESIGN_CHECKLIST §14、§14.1（D1–D19）、METHODS M3「迁移基线」（迁移不可变）；`NAMING_STANDARD.md` 的 DDL 部分。
5. `docs/data/data-scorecard.md`（逐表打分是否落表）。

## 必查项（引用门禁，评审员逐项判过 / 不过）

| # | 查什么 | 依据 |
|---|---|---|
| T1 | 实际表结构 diff 等于卡上的预期 diff：逐表逐列逐约束对照，多出或少了都不过 | 门禁 §1.4 第 1 条 |
| T2 | 注册表条目与卡一致，没有新增 `gaps` | 门禁 §1.4 第 2 条 |
| T3 | 守护全绿，且没有新增例外清单条目；新增必须在卡上有出处 | 门禁 §1.4 第 3 条 |
| T4 | 逐表打分结论已写入 `DATA_SCORECARD.md` 新增行 | 门禁 §1.4 第 4 条 |
| T5 | 签发行有效：`git diff <签发所依据提交号>..HEAD -- <卡文件>` 中「数据设计」一节无改动，其引用的规则卡与 ADR 无改动 | 门禁 §1.3 有效性判法 |
| T6 | 门禁 §3.1–§3.6 的每一条目，评审员**独立**判通过 / 不通过（不照抄 one-data 的审查记录，再与它对照；两者不一致的单列） | 门禁 §3、附录 B |
| T7 | 迁移不可变与版本单调，按 METHODS M3「迁移基线」与 `migrations_immutable.py`：基线开放标记（`backend/architecture/migration-baseline-open`）存在期间允许改基线文件，但须有卡依据；标记不存在时已合入迁移不得改、改名、删；新迁移版本号大于现有最大 | METHODS M3「迁移基线」；`scripts/ci/migrations_immutable.py` |
| T8 | 主键 UUIDv7、命名、列类型、时间列使用注入时钟而非数据库时钟 | CLAUDE.md 第 3 条；NAMING_STANDARD；DATA_DESIGN_CHECKLIST §14.1 |

安全与结构的深度复审按门禁 §1.4 第 5 条由 opus 做，即本评审；one-data 不重复，评审员也不替 one-data 签发。

## 本类特有的盲区（主动找）

- **B7 登记遗漏**：新表 / 新列是否进了 `data-access-registry.yaml`、`data-classification.txt`；跨 BC 读取是否进了读取列登记。
- **B6**：状态列的取值集与状态机、事件载荷、读模型是否一致。
- **B1**：新表名、列名、枚举值的用语与 `terms.yaml` 一致（CLAUDE.md 第 2 条）。
- **B3**：卡新增测试容器镜像时，核 `test-images.txt` 与镜像架构。
- **B4**：卡里引用 ADR-0033 / 0036 / 0038 / 0043 等的取值，对着 ADR 原文逐字核。
- **B2**：PostgreSQL 版本相关的语法与特性（如 `uuidv7()`、分区、排序规则 `builtin C.UTF-8`），对 `backend/architecture/test-images.txt` 里的版本。
- **B5**：「没有索引 / 没有约束」的结论，评审员在迁移文件里自己搜。
- 迁移里的数据回填：对已有数据是否可重复执行、锁多久、是否在大表上全表更新。

## 输出

按 README 的「通用输出格式」；逐条判时沿用门禁附录 B 的「通过 / 不通过 + 证据」写法，不另造格式。
