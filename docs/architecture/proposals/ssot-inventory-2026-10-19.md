# 仓库内架构 SSOT 盘点与确认页去向（暂停期只读清单）

状态：历史记录｜2026-10-19｜负责：one-architecture

起因：用户原话「暂停，在仓库只有一套你认可的 SSOT，仓库之外的 SSOT 一律不管。仓库里的不同口径 SSOT 要作废，全部参考你最终认同这套」（one-coordinator 转，intent 2026-10-19-223711）。本文件只盘点、不改设计文件；读者数为按文件名在 `git ls-files '*.py'` 中引用到的脚本数（同名文件如 commands.yaml 会合并计数）。

## ① 各口径 SSOT：声称的节点、形态、读者

节点按 ADR-0059 登记网络 v5（提议，ADR59 页已撤回）。「手写」= 人或会话直接编辑；「生成」= 脚本产出。

| 节点 | 文件 | 形态 | 读者 | 口径与说明 |
|---|---|---|---|---|
| 01 干系人 | `proposals/value-streams.v2.yaml` 的 `stakeholders`、`stakeholder_categories` | 手写 | 14 | 现行；STKH 答复已写回 |
| 01 干系人 | `proposals/STAKEHOLDERS.md` | 表由 panorama_build 生成，正文手写 | 1 | 现行，从上一行生成 |
| 01 干系人 | `proposals/stakeholder-subclasses/subclasses.yaml`、`STAKEHOLDER_SUBCLASSES.md` | 手写（对拍合并） | 0 | 提议，STKH2 未答 |
| 03 价值主张 | `value-streams.v2.yaml` 各价值流 `value` 字段 | 手写 | — | 暂代，无独立登记 |
| 04 取舍决定 | `panorama/tradeoffs.yaml` | 手写 | 1 | 新建，2 条（DECR-01、02） |
| 05 业务能力 | `proposals/capabilities.v2.yaml` | 手写 | 5 | 现行 |
| 05 业务能力 | `togaf/capabilities.yaml` | 手写 | 0 | **旧口径**，已被 v2 取代未删 |
| 05 分类 | `proposals/categories.proposal.yaml` | 手写 | 1 | 提议（价值流与能力的核心 / 支撑分类） |
| 06 价值流与阶段 | `proposals/value-streams.v2.yaml` | 手写 | 14 | 现行（15 条价值流、61 个阶段） |
| 06 价值流 | `togaf/value-streams.yaml` | 手写 | 0 | **旧口径**，已标取代；曾使 49 份笔记错引编号 |
| 06 过渡架构 | `togaf/transitions.yaml`、`togaf/vision.yaml` | 手写 | 2 / 0 | 过渡与愿景，按整合确认包 T 册待重写 |
| 07 业务流程 | `proposals/closeout/process-catalog.yaml` | 生成（build_catalog、sync_catalog_stages 从三组流程骨架同步） | 11 | 现行目录（200 个流程） |
| 07 流程骨架 | `closeout/{finance,people,revenue}/processes.*.yaml` 及 `parts/` | 手写 | 1–8 | 三组各自 SSOT，目录由此汇总 |
| 07 流程 | `bpmn/processes.yaml` | 手写 | 1（rebuild.py） | **旧口径** |
| 07 流程 | `proposals/processes.v1.yaml` | 手写 | 1（processes_check） | **旧口径** |
| 07 派生 | `closeout/process-objects.yaml`、`closeout/object-layer.yaml` | 生成 | 3 / 3 | 由目录与骨架算出，非独立来源 |
| 08 约束 | `proposals/ENTERPRISE_AGGREGATES_DRAFT.md` §1.2（INV） | 手写 | 11 | 现行 |
| 08 状态机 | `closeout/*/state-machines.yaml` | 手写 | 5 | 三组各一份 |
| 08 审批 | `closeout/APPROVAL_RULES.yaml`、`OBJECT_EXEMPTIONS.yaml` | 手写 | 2 / 1 | 现行 |
| 08 决策 | `dmn/decisions.yaml` | 手写 | 0 | **旧口径**；R 册已记「不引入 DMN，判断走 D15 版本化配置」 |
| 09 业务对象 | `proposals/ENTERPRISE_INFO_MAP_DRAFT.md` §1 总表、§2.18 | 手写 | 9 | 现行（126 行，含 OBJ-126 股东） |
| 09 冻结裁定 | `panorama/FREEZE_FINAL.yaml` → `FREEZE_FACTS.*`、`FREEZE_IMPACT.md` | 手写 / 生成 | 1 | 裁定未写回信息图（等 OBJFIN） |
| 09 数据模型 | `closeout/*/data-model.*.yaml` | 手写 | 0 | 三组草稿，无脚本读 |
| 10 聚合与 BC | `ENTERPRISE_AGGREGATES_DRAFT.md` §2.2 聚合、§3.2 BC | 手写 | 11 | 提议口径（聚合 130、BC 31） |
| 10 聚合（已建） | `ddd/aggregates.yaml`、`contexts.yaml`、`context-map.yaml`、`subdomains.yaml` | 手写 | 1–2 | **已建代码口径**，与提议稿并存 |
| 10 命令 | `closeout/*/commands.yaml` | 手写 | 8 | 三组各一份 |
| 10 BC 映射 | `closeout/bc-map.yaml` | 手写 | 1 | 三组 BC 对照 |
| 10 依赖 | `backend/architecture/module-dependencies.txt` | 手写 | 4 | 代码守护的已有 SSOT |
| 10 数据访问 | `backend/architecture/data-access-registry.yaml` | 手写 | 0（Java 守护读） | 各模块所有 |
| 11 事实 / 事件 | `docs/modernization/events/*.yaml`；`closeout/people/events.yaml`、`finance/events.finance.yaml` | 手写 | 2 | 仓库事件目录与组内草稿两套 |
| 13 指标 | `glossary/terms.yaml`（kind=metric）、`r0/METRIC_BASELINE_DRAFT.md` | 手写 | 21（词表整体） | 指标借住词表 |
| 12–14 | `data/PANORAMA_TABLES.yaml` | — | 1 | 不存在（one-data 未交） |
| 责任 | `closeout/RESPONSIBILITY_MATRIX.yaml`、`finance/responsibility.finance.yaml`、`people/raci.yaml`、`revenue/raci.yaml`、`assignments.yaml` | 手写 / 生成 | 1–5 | 两种写法并存 |
| 用语 | `glossary/terms.yaml` | 手写（one-glossary） | 21 | 已有 SSOT |
| 改名 | `closeout/rename/RENAME_{A,B,C,V2}.yaml` | 手写 | 0 | 只作输入 |
| 施工计划 | `proposals/construction-plan.v1.yaml` | 手写 | 0 | 旧 |
| 场景 | `closeout/*/scenarios.*.yaml` | 手写 | 0–1 | 三组草稿 |
| 全景 | `panorama/panorama.yaml`、`GAPS.md` | 生成（panorama_build 读上面全部） | 3 | 汇总视图，不是来源 |

依赖主链：`value-streams.v2.yaml`（06、01）+ `capabilities.v2.yaml`（05）+ `ENTERPRISE_INFO_MAP_DRAFT.md`（09）+ 三组 `processes*.yaml` / `commands.yaml` / `state-machines.yaml`（07、10、08）+ `ENTERPRISE_AGGREGATES_DRAFT.md`（08、10）→ 生成 `process-catalog.yaml`、`object-layer.yaml`、`process-objects.yaml` → 各检查器（anchoring、stage_align、object_check、seam、converge、name_id）与 `panorama_build.py` → `panorama.yaml`、`GAPS.md`、`STAKEHOLDERS.md` 表、`FREEZE_FACTS`。`ddd/*.yaml`、`togaf/transitions.yaml`、`module-dependencies.txt` 另经 `build_architecture.py` 出 4 张图。

同一节点的多口径：05（togaf vs v2）、06（togaf vs v2）、07（bpmn/processes、processes.v1 vs 目录 + 三组骨架）、08（dmn vs 约束表 + 状态机）、10（ddd 已建 vs 聚合稿提议）、11（events/ vs 组内 events）、责任（RACI 两种写法）。

## ② 确认页去向（本会话出的页）

| 页 | 状态 | 提交编号 | 答复落到的文件 | intent |
|---|---|---|---|---|
| ADR57 | 已答（批准） | ADR57-20261017-131944-76w | answers/ADR57.json；adr/src/ADR-0057、ADR-0051 | 2026-10-17-132129 |
| ADR58 | 已答（批准） | ADR58-20261017-225508-gjl | answers/ADR58.json；adr/src/ADR-0058 | 2026-10-17-225559 |
| TRN | 已答 | 见 answers/TRN.json；TRN-09、13 会话代答 | adr/src/ADR-0054、0055、0056 | 2026-10-17-121025、130124 |
| CAPINFO | 已答 | CAPINFO-20261017-111010-cfy | answers/CAPINFO.json；value-streams.v2、process-catalog、object-layer、信息图口径行、glossary/decisions-nm.yaml | 2026-10-17-111138 |
| B31 | 已答 | B31-20261017-122917-ctg | answers/B31.json；people/processes、scenarios、parts/b31.*、process-catalog、ARCH_VS14 | 2026-10-17-123053 |
| ANC | 已答 | ANC-20261017-181237-som | answers/ANC.json；process-catalog（PRC-190/191 移出） | 2026-10-17-181332 |
| PAN | 已答（会话中答，页未提交） | — | value-streams.v2（VS-15）、PROCESS_ANCHORING_RESEARCH | 2026-10-17-174814 |
| OBJD | 已答 | OBJD-20261017-201658-837 | answers/OBJD.json；信息图 §2.18、value-streams.v2、object-layer、anchoring_check | 2026-10-17-201810 |
| STKH | 已答 | STKH-20261019-202526-si5 | answers/STKH.json；value-streams.v2 干系人、STAKEHOLDERS.md、信息图 OBJ-02 / §2.2 / OBJ-126、panorama/tradeoffs.yaml、OBJ126_SHAREHOLDER_IMPACT.md | 2026-10-19-202820（调度记） |
| ANM | 已答（调度标记） | 见 answers/ANM.json | — | 2026-10-17-084935 |
| B31P | 待答（pending） | — | — | — |
| OBJFIN | 撤回（暂停） | 未答 | 页源 sheets/OBJFIN.json（14 题） | 2026-10-19-173014、174023、182522、202820 |
| STKH2 | 撤回（暂停） | 未答 | 页源 sheets/STKH2.json | 2026-10-19-222904 |
| ADV1、ADV1R | 撤回（暂停） | 未答（answers-submit 为空） | 页源 sheets/ADV1、ADV1R.json | — |
| ADR59 | 撤回（暂停） | 未答 | 页源 sheets/ADR59.json；adr/src/ADR-0059（提议，含决定 8–10） | — |
| NM8A、NM8B、OBDS | 撤回（10-17） | 未答 | 页源保留 | NM8：2026-10-17-181615、225751；OBDS：2026-10-17-203145 |

注：调度派工里写 ADV1 已答，核对两页 answers-submit 均为空、收件箱为撤回，按未答列。2026-10-16 的 30 余项收件箱条目（approval、construction、q04 等）为更早一轮，已全部 decided，不在本表展开。
