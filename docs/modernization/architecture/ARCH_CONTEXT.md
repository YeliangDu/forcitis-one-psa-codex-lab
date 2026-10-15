# one-architecture 速查（2026-10-15）

规矩：结构性选择只推荐、不裁定，用户答复为准；先在 SSOT 登记再写代码；已有 SSOT 只引用不复制；一行一项，过程放链接（SESSION_PLAYBOOK §2.1 第 13 条）。

## 在哪里

| 什么 | 路径 |
|---|---|
| 设计说明（one-coordinator 2026-10-15 审过） | `architecture/DESIGN.md` |
| 生成器与图 | `architecture/build_architecture.py`（`--svg` 本机渲染，`--check` 校验；build_all 调用）→ `architecture/generated/` |
| 在线图页（私有） | https://claude.ai/artifact/2yjJtJvziV85NshARkFRbN （发布 `generated/index.html`） |
| 架构 SSOT | `architecture/{togaf,ddd,bpmn,dmn,dama,olap}/*.yaml` |
| 引用的已有 SSOT | 词表 `glossary/terms.yaml`；依赖图 `backend/architecture/module-dependencies.txt`；数据访问注册表 `backend/architecture/data-access-registry.yaml`；事件目录 `events/*.yaml`；指标草稿 `r0/METRIC_BASELINE_DRAFT.md` |
| 老系统证据（只读） | `~/Projects/FORCITIS/psa/docs/domain/00_战略设计/`（02 子域、04 BC、05 上下文映射、06 发布语言） |
| 理论参照（唯一） | `architecture/FRAMEWORKS.md`（锁版本）；方法 `architecture/METHOD.md`；forcitis-architecture 不是信源（intent 2026-10-15-163920） |
| 防腐层决策页（私有，db） | https://claude.ai/artifact/BLkvx2sswqYiRWbTx2bDMB ；源 `architecture/decision-page/index.html` |
| 依据 | intent `2026-10-15-160111`（施工图、4 张图）、`2026-10-15-151621`（守护三层方案）；ADR-0003、0017 |

## 现状

| 项 | 值 |
|---|---|
| 分支 | `claude/one-architecture-ssot`（基于 main 173987b4） |
| 在途 | PR-A #203（已入队）；PR-B #204（叠在 #203 上）；ACL 方案草稿 `proposals/ACL_BOUNDARY.md`（已交 one-coordinator 审，未提交）；PR-C 聚合 / 流程 / 数据 / 指标 |
| 之后 | PR-C 聚合 / 流程 / 数据 / 指标与图③④；PR-D CI 守护与 §14 条款 |
| 待用户 | 架构决策页 Q1–Q7（DESIGN §6）＋ ACL 冲突项；等 one-coordinator 通知再发（用语确认单先出） |
| 用户确认 | 新建私有页手机能打开（「可以看到」，2026-10-15），决策页另建新页 |
| 已定 | Q1 改为本仓库推导（intent 163920）；mermaid-cli 用户批准本机安装（锁版本，装在 `~/.modernize/psa/tools/mermaid-cli`，登记 SUPPLY_CHAIN 与 RESOURCES；SVG 入库，在线页只嵌静态 SVG） |
| 协同 | 决策页链接方式问 one-glossary（不覆盖 R-1b-0 门禁页）；Q7 术语 bc 改动由 one-glossary 执行 |
| 后续项 | A1 只做边集合相等；「已声明但不再使用的边」登记后续 |
