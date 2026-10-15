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
| 在途 PR | #258 ADR-0043/0044/0045（已采纳，排队；ADR-0044 须在 one-rules #249 之后合并）；#222 ADR-0041（排队）→ #224 ADR-0042；#226 intent。排队分支不得再推 |
| 等用户 | 全企业业务对象确认页 https://claude.ai/artifact/Nmexu1PxCx5bqbbZiAbSLA（一级 19 / 次级 61 / 非对象 21，39 题；答复读 db `answers`、`answers-submit`） |
| 答复后 | 回溯统一售前段口径（PRESALES_*，冲突 C-38）→ METHOD 第 4–5 步（不变量 → 聚合 → BC），全部段做到聚合和 BC（用户定） |
| #258 合并后 | 通知 one-1a-par 收紧 F-1 验收（Q1 c、Q3 a、Q4 b 员工 ID、Q5 d）；起草 ADR-0046 期间模型含第 13 期（opus 一次 + 评审一轮，Phase 2 accounting.period 骨架 PR 前定稿；建议见 `proposals/FD46_RECOMMENDATION.md`） |
| 方法第 4 稿 | 依 intent 221201（按对象命名的能力图、法律事实例外、全部段到 BC、子域 = 命名空间）、222319 / 222350（BIZBOK 五项 + G178/G190/G211，冲突以 BIZBOK 为准）；草案 `METHOD_V3_DRAFT.md`、`FRAMEWORKS_V3_ADDITIONS_DRAFT.md` 与 opus 评审意见；随最终一次性 ADR 落地（intent 211634） |
| 其他待办 | #222 合并后 C1 卡补到期端口 ExternalSubjectEmployees 与 SSOT `expiring_ports`；「区域」数据范围维度随信息图组织单元类型题定后排期；身份生命周期 C1 确认页未出 |
| 新规矩 | 选型先做适配研究；交用户前按性质对抗检查；子代理 haiku 取数、sonnet 调研起草复核做页、opus 只做 ADR 起草与首轮评审、安全判定、结构设计；行业通行做法为主、生产数据只作参考；取证子代理只用 one_evidence_ro 与既有通道 |
| 已定（早前） | ADR-0039、ADR-0040（被 #222 ADR-0041 取代）；mermaid-cli 本机锁版本安装；A1 只做边集合相等 |
| 交接记录 | `proposals/HANDOFF_2026-10-15.md`（随 #258 入库） |
