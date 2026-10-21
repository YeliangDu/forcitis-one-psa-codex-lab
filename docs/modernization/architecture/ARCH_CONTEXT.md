# one-architecture 速查（2026-10-15）

> **接续必读（2026-10-21）**：先读 `proposals/HANDOFF_2026-10-21.md`，再读本文件「现状」。


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
| 在途 PR | 无；文档不开 PR，提交推送后报分支 + 提交号，one-merge 按调度批准收合集 |
| 当前步 | 2026-10-17 13:59：①ADR-0052/0054/0055/0056 已采纳（b4dc1f6a），ADR-0057 报价发出编排模块已批并采纳（bcda802f，部分取代 ADR-0051 三处）；transitions.yaml 与 DDD SSOT 随整合确认包 T、D 册写入（intent 2026-10-17-112814）；②CAPINFO、TRN、B31、ADR57 确认页已答并写回；③命名接续 N9→N7，模块与新命令名随 N7；④SSOT 整合确认包方案 `proposals/ARCH_SSOT_CONSOLIDATION_PLAN.md`（8 册，R 册已记「不引入 DMN、判断走 D15 版本化配置」），N7 完成后开工；⑤在途：B31 参数清单页 ARC-1017-ARC-06（Codex）、A8 现行做法取证 ARC-1017-ARC-07（one-data 经 one-ops 通道，等参数清单）；⑥检查脚本 `--extra` 已落地，云端试点验收通过（`proposals/closeout/pilot/ACCEPTANCE.md`），铺开待调度定 |
| 已定（2026-10-16 晚） | 结构裁定 S-29–S-44；VS-02.7 改名收尾；新增 VS-04.6 履约、VS-08.0 事前申请；归段判据写进 stage_align_check；权责表 586 条冲突 0；跨组接缝清零 |
| 已定（2026-10-16） | ADR-0046（12 月与第 13 期同时关账）、ADR-0047（视为同意）已采纳；阶段类型 S1–S6；BC 划分 D-1–D-9、D-8 期满判定在 receivables.wip；完整性 C-1–C-8、INV-62、Q29 宽口径（证据升一级）；D-7 提前拆 accounting.tax；K-1 知识条目（OBJ-110，km.knowledge）；P-1 回签前开票规则；METHOD M-1–M-7（7 步、两份一次性 ADR） |
| 草稿位置 | proposals/：ENTERPRISE_INFO_MAP_DRAFT（一级 21）、ENTERPRISE_AGGREGATES_DRAFT（聚合 103、BC 29、查环脚本 aggregates_dep_check.py）、STAGE_TYPES_DRAFT、METHOD_V4_DRAFT、FRAMEWORKS_V4_DRAFT、OBJECT_COMPLETENESS_REPORT、STEP1_VALUE_STREAMS + value-streams.v2.yaml、STEP2_CAPABILITIES + capabilities.v2.yaml |
| 规矩（白天） | 要用户定的：确认页：框架、标准、选型类不预选（ADR-0041 决定 6），业务口径页推荐项预选（用户 #362 裁定 B；rules/decide/README.md）＋待决定收件箱 https://claude.ai/artifact/RVU6nNNSsygF5sVRWLhQ15 （集合 inbox）；确认页用 contract 0.2.80；纯文档作者自检，结构判定仍 opus；直连 one-glossary、one-data（数据落点与第 11 步）；取证登记 #298 合入后用 phase1a/infra/new_legacy_read.py 片段，在途分支合 main 时丢老表改动重登 |
| 规矩 | 确认页先交 one-coordinator 独立审题再进收件箱（status pending）；结构性选择只推荐；目录阶段以骨架为准经 sync_catalog_stages 同步；依赖列括注不用全角分号（查环脚本） |
| 待办 | 见「当前步」⑤；任务登记 `docs/modernization/tasks/*-ARC-*.md` |
