# one-architecture 速查（2026-10-15）

> **接续必读（2026-10-16 下午）**：本表「在途 PR」「当前步」为准；`proposals/HANDOFF_2026-10-16.md` 只作上午的历史。


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
| 在途 PR | #324（ADR-0048 成文、G-DDL1、第③步答复落文）→ #327（第④步回溯，叠在 #324）→ #334（⑥a 流程清单、J 检查清单、对照标准确认页，叠在 #327）；均待 one-coordinator 入队 |
| 已定（2026-10-16） | ADR-0046（12 月与第 13 期同时关账）、ADR-0047（视为同意）已采纳；阶段类型 S1–S6；BC 划分 D-1–D-9、D-8 期满判定在 receivables.wip；完整性 C-1–C-8、INV-62、Q29 宽口径（证据升一级）；D-7 提前拆 accounting.tax；K-1 知识条目（OBJ-110，km.knowledge）；P-1 回签前开票规则；METHOD M-1–M-7（7 步、两份一次性 ADR） |
| 草稿位置 | proposals/：ENTERPRISE_INFO_MAP_DRAFT（一级 21）、ENTERPRISE_AGGREGATES_DRAFT（聚合 103、BC 29、查环脚本 aggregates_dep_check.py）、STAGE_TYPES_DRAFT、METHOD_V4_DRAFT、FRAMEWORKS_V4_DRAFT、OBJECT_COMPLETENESS_REPORT、STEP1_VALUE_STREAMS + value-streams.v2.yaml、STEP2_CAPABILITIES + capabilities.v2.yaml |
| 当前步 | ⑥a 提议稿已出（`proposals/STEP6A_PROCESSES.md`，gaps 18 条），等 one-coordinator 派的独立 opus 审查（不自审）；对照标准确认页 https://claude.ai/artifact/9M1be9qRAfRF4LxVMpV2iN （收件箱 2026-10-16-one-architecture-lock，含 ADR-0047 修订）待用户；J1–J7 清单已交 one-quality 写脚本 → 审查与答复回来后处理 gaps 回溯 → 回溯售前段 → 两份一次性 ADR |
| 规矩 | 要用户定的：确认页（不预选，ADR-0041 决定 6）＋待决定收件箱 https://claude.ai/artifact/RVU6nNNSsygF5sVRWLhQ15 （集合 inbox）；确认页用 contract 0.2.80；纯文档作者自检，结构判定仍 opus；直连 one-glossary、one-data（数据落点与第 11 步）；取证登记 #298 合入后用 phase1a/infra/new_legacy_read.py 片段，在途分支合 main 时丢老表改动重登 |
| 待办 | 新用语交 one-glossary（#320 已登记商票、诉讼等；STEP6A §4 流程用语待交；「核销」处置取值与「已核销」重名）；往来款项处置分类其余取值待用户；「争议」与「争议应收」待用户（one-glossary 收件箱）；WY-1 待用户追认；工时单填报义务待 one-rules |
