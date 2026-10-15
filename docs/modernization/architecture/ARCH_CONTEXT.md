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
| 在途 | #203、#204、#207 已合并；#208 已入队；#210（ADR-0040）已 rebase 到 main（797bda5c），待入队；分支 `claude/one-architecture-step01`（96f385e0，叠在 #210 上，未开 PR）含第 0、1 步草稿与确认页、FRAMEWORKS §8 补充框架、身份研究结论 |
| 排定（one-coordinator） | ①#207 合并后：身份生命周期研究 + C1 设计（取证交 sonnet，ADR-0037 登记，结论在线页）②ADR-0040 补齐顺序：第 0+1 步（愿景/目标/KPI + 能力/价值流）一页确认页 → 第 2 步按能力重推规划 BC（决策页 Q6 与余项随后一起发）→ 指标挂 KPI → 事件风暴与规则卡补「类别」（只补类别，sonnet 批量、我抽查）→ CI 追溯校验（守护单列，opus 复审）③ADR-0039 代码子 PR 由 one-coordinator 并入 P3b / P5；ARCH-D1..D7 交 one-1a-next-3 |
| 身份生命周期研究 | 取证完成，结论 `proposals/IDENTITY_LIFECYCLE.md`；下一步：C1 方案做成手机确认页（兜底方式、在职判断、离职回收）。one-coordinator：新系统不复刻「统一默认口令兜底」，方案写明替代做法；老系统离职后仍能登录只作 C1 离职回收的设计依据，不另开老系统整改（用户「老系统先不管」） |
| 工程层框架 | 已定（intent 2026-10-15-192555，FRAMEWORKS §9，勾选页 https://claude.ai/artifact/27GVPzAjrX6FtDCLZtcaFt ）；规范补引用 one-coordinator 派 sonnet，SBOM/SLSA 交 one-ops（rc.2 后）；step01 分支等第 0、1 步确认后一起开 PR |
| 待用户 | 第 0、1 步确认页 https://claude.ai/artifact/MyMngoyiqfTyYAkVw3Lesp ；第三范式与六边形架构是否加入 FRAMEWORKS（建议都加，等用户回「加」） |
| 用户已定 | ADR-0039（intent 164527）；ADR-0040 方法与知识框架（intent 173149）；补充框架勾选页 https://claude.ai/artifact/GtFkMvqxnWe6wZc1euM9Ec 待选（选完记 intent 再写进 FRAMEWORKS） |
| 接着做（清空后先核实况：`gh pr list`、ListAgents） | PR-C：草稿在 scratchpad/pr-c（aggregates 15、processes 7、decisions 15、data-objects 30），核对后入 `ddd/aggregates.yaml`、`bpmn/`、`dmn/`、`dama/`，出图③④ |
| 之后 | PR-C 聚合 / 流程 / 数据 / 指标与图③④；PR-D CI 守护与 §14 条款 |
| 待用户 | 架构决策页 Q1–Q7（DESIGN §6）＋ ACL 冲突项；等 one-coordinator 通知再发（用语确认单先出） |
| 用户确认 | 新建私有页手机能打开（「可以看到」，2026-10-15），决策页另建新页 |
| 已定 | Q1 改为本仓库推导（intent 163920）；mermaid-cli 用户批准本机安装（锁版本，装在 `~/.modernize/psa/tools/mermaid-cli`，登记 SUPPLY_CHAIN 与 RESOURCES；SVG 入库，在线页只嵌静态 SVG） |
| 协同 | 决策页链接方式问 one-glossary（不覆盖 R-1b-0 门禁页）；Q7 术语 bc 改动由 one-glossary 执行 |
| 后续项 | A1 只做边集合相等；「已声明但不再使用的边」登记后续 |
