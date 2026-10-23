# OAT 正本（经营会计分类法）

用户 2026-10-14 决定（BI_SCOPE §6.1 Q5）：「复制进本仓库，policy 治理 (Recommended)」。自本次复制起，**本目录是 OAT 的正本**，由 `operations.policy` 治理；原研究仓库中的同名文件不再作为依据。

## 来源

- 来源仓库：`YeliangDu/ddd-theory`，本机 `/Users/yeliang/Projects/YeliangDu/ddd-theory`，复制时 HEAD 为 `1ace8779cbe4c0d837a38cb310742e375ea1063d`。
- **这些文件在来源仓库中尚未提交（未跟踪），没有对应的提交号**，因此以下面的 SHA-256 作为复制时版本的凭据。
- 复制时间：2026-10-14 · 复制会话：psa-r0-metric-baseline。

| 本目录文件 | 来源路径 | 行数 | SHA-256 |
|---|---|---|---|
| `forcitis_operating_accounting_taxonomy.csv` | `cases/forcitis-aec-ops/forcitis_operating_accounting_taxonomy.csv` | 246（245 条原语 + 表头） | `8ecdc44b4ce194981c9a644406010849a9b578bc8060603bf47f51a89755e70b` |
| `standard_concept_mapping.csv` | `cases/forcitis-aec-ops/standard_concept_mapping.csv` | 241 | `be9e3aecb64ae2b2fb0885da3b4078819dd31e061427896787609ed48ff271ab` |
| `forcitis_anti_custom_symbol_audit.csv` | `cases/forcitis-aec-ops/forcitis_anti_custom_symbol_audit.csv` | 246 | `93abaa6b486991d8e42f936214f9155127ee2edc2fbb7fc37f8490e1a653e5ff` |
| `KPI体系设计.md` | `obsidian/FORCITIS-one/80-经营度量/FORCITIS one KPI体系设计.md` | 608 | `6ea38624642c7615e56ca1a81c3cfdfb75d612dbc99999144e858a29b968500e` |

`KPI体系设计.md` 中的相对链接（方法论、公式库、目录 CSV 等）指向来源仓库，本仓库未复制，只作背景参考。

## 内容

- `forcitis_operating_accounting_taxonomy.csv`：245 条经营会计原语，27 列。符号、名称、层级、单位、期间类型（流量 / 存量）、借贷、加总规则、标准引用、事实归属与指标归属都在其中。
- `standard_concept_mapping.csv`：原语到外部标准概念（会计准则、XBRL、管理会计、项目控制）的映射。
- `forcitis_anti_custom_symbol_audit.csv`：反自造符号审计。能映射到标准概念的不得新造私有原语。
- `KPI体系设计.md`：指标定义（MetricDefinition）、口径政策（PolicyDefinition）、事实源契约、指标观测值的字段要求。

## 治理

1. **归属**：OAT 由 `operations.policy` 治理（OAT 245 条原语的指标归属均为「经营口径与勾稽 BC」）。
2. **变更**：新增或修改原语按 ADR-0018 的「提议 → 确认」流程处理：会计类由财务确认，业务类由业务 Admin 确认。口径版本按 SESSION_PLAYBOOK 附 M3「规则调整三类」配置类处理，按生效日期生效、不追溯。每次变更在本 README 末尾追加一行（日期、原语、变更、确认人原话出处）。
3. **用途**：语义层的每个指标都要声明 OAT 原语或登记的经营扩展原语（BI_SCOPE §3.3、ADR-0023）；R1 的守护测试（扩展 G37–G39）读取本目录的 CSV。
4. **不改原样**：本目录文件保持复制时的内容，修改一律经上面的流程，生成的派生物另放。

## 变更记录

| 日期 | 变更 | 出处 |
|---|---|---|
| 2026-10-14 | 自 ddd-theory 复制初版（4 个文件，SHA-256 见上表） | BI_SCOPE §6.1 Q5 用户决定 |
