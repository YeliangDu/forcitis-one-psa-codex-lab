# FORCITIS one 现代化 ADR

本目录记录 FORCITIS one 现代化中的架构决策。`docs/rules/naming-standard.md`、`docs/architecture/guiding-principles.md` 等只引用这里的决定，不另立结论。参照文档：forcitis-one `docs/adr/0001-tech-stack.md`（只参照文本，不参照代码）。

名称：ADR 正文中的「PSA」指老系统；已采纳 ADR 正文冻结不改（`docs/rules/playbooks/design.md` 第 9 步、`docs/adr/readme-lifecycle.md`），新文档与新 ADR 一律写 FORCITIS one / 老系统（用户 2026-10-14）。

**ADR 写什么、不写什么**（用户 2026-10-16 采纳研究方案 P6、P7，intent `2026-10-16-131918-one-coordinator-ADR-管理优化-采纳-集中解决.md`）

- 写 ADR，当且仅当一个选择同时满足：① 影响结构（模块与 BC 边界、依赖方向）、质量属性（安全、可追溯、性能、一致性）、外部依赖、对外接口或数据形状之一；② 回退有成本（要改数据、迁移、接口或多个模块）；③ 有两个以上可行方案，且没有现成的上位规定（已采纳 ADR、规范、准则）可直接推出。
- 不写 ADR，改放这里：
  | 内容 | 去处 |
  |---|---|
  | 会话分工、协作流程、报告与派工约定 | `docs/rules/`（`rules.md`、`roles/`、`playbooks/`） |
  | 编码与命名约定 | `docs/rules/coding-standard.md`、`docs/rules/naming-standard.md` |
  | 用语 | `docs/glossary/terms.yaml` |
  | 清单、快照、参数（BC 与聚合登记、阶段表、权限码、阈值） | 经用户认可的进各自的 SSOT 文件（如 `docs/architecture/ssot/domain/contexts.yaml`、`docs/architecture/ssot/transitions/transitions.yaml`），受守护测试保护；未经用户认可的留在提议稿（提议中的 ADR 或 `architecture/proposals/`） |
  | 业务口径与政策参数 | 配置类（`docs/rules/playbooks/design.md` 第 9 步「规则调整分三类」，`docs/ops/config-changelog.md`） |
  | 领域规则结论 | `docs/business/rule-reviews.json` |
  | 现状勘查、取证明细、设计细节、方案比较长文 | `architecture/proposals/` 或证据文件，ADR 只引用路径 |
- ADR 只写**决定与判据**。需要列举时分开两类（ADR-0051 决定 1 第 1 条）：
  - **规范性内容**（如 BC 的模块标识、命名空间、BC 的合并与新增、禁止边、归属规则与判据）写在 ADR；它的任何改变——包括 BC 的增、删、合并——都要新 ADR。
  - **参考性快照**（如聚合清单、计数、分层快照）在 ADR 中标「参考快照，以 <SSOT 路径或提议稿路径> 为准」，随阶段复核更新，不需新 ADR。
  - 清单经用户认可后进 SSOT（用户 2026-10-16：「我认可的规划，要进SSOT」，intent `2026-10-16-124649`），ADR 引用其路径与提交号；未经用户认可的部分留在提议稿，不进 SSOT。
- ADR-0020 中的协作规则（会话、协议、调度）以 `docs/rules/`（`rules.md`、`roles/`、`playbooks/`）为准，今后只改这些文件，不再为其开 ADR；ADR 正文不动（见「勘误与历史偏差」第 3 节性质登记）。

**怎么找**：下方总表按编号排列，列出类别与 BC；查现行规则看生成页 `docs/adr/current.md`（按 BC 列现行 ADR，标出整份被取代与部分被取代的范围；`python3 -I scripts/guards/build_adr_current.py` 重新生成，勿手改）。旧格式 ADR 的类别、BC、阶段与被部分取代的范围取自旁表 `docs/adr/catalog.yaml`（不改 ADR 正文）。。

架构正本 2026-10-23 起统一在 `architecture/ssot/`（GOV-1023-ARC-01）。ADR 正文中的 `ddd/`、`togaf/` 旧路径不改，按 `docs/architecture/ssot/paths.md` 对照读。
