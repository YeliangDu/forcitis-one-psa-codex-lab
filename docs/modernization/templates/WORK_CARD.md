# 派工卡模板

用途：负责会话或调度会话派一张编码卡（给子代理、Codex 或工作会话）时，复制下方「模板」填写。规则出处：SESSION_PLAYBOOK TEAM-32、TEAM-33、TEAM-34、TEAM-86、TEAM-52、附 M1、TEAM-20；CODING_STANDARD §7。一张卡只属于一类工作（SESSION_PLAYBOOK 附 M1 第 5 条），卡上没写的不做。

## 字段协议（check_tasks／finish／合入回填共用）

权威解析入口为 `../check_tasks.py` 的 `FIELDS`、`FRAGMENT_STATUSES`、`KINDS`、`LANES`、`SESSION_CODES`、`parse_task`、`validate`；三方须读取同一套常量和解析函数，不各定义终态。本文只约定卡的写法，不表示 finish 或回填已实现；接线分别由 GOV-1022-QUA-14、QUA-17、MRG-02 验收。

- 必填键共 13 个：`任务号`、`任务名`、`主线`、`bc`、`种类`、`会话`、`执行者与模型`、`状态`、`分支`、`提交`、`返工轮次`、`用时`、`旧编号`。新卡不用兼容别名「执行者」；缺键即解析失败。
- 可选键只有 `最后更新` 与 `来源`；后者只允许 `回填`，新卡省略（首次入库晚于 2026-10-17 不可冒充回填）。前置、阻塞原因、并入去向、撤销依据、PR 号、交出事件及周期放正文，不能另加头部键。
- 头部从首行 `---` 到下一行 `---`，每行一个 `键: 值`；不是完整 YAML，不写行尾注释、列表、重复键或多行值。可用一对完整引号包值。空值解析为 `待核`，未知数据明确写 `待核`，不按零计。
- `任务号` 与 `tasks/<任务号>.md` 文件名一致：主线码、有效 MMDD、解析器会话码、两位序号；`主线` 与任务号一致；`任务名` 为 `[主线中文名·bc] 动作 + 对象`，中文主线查 `LANES`；`bc` 查 `glossary/bcs.yaml` 或用 `跨域`；`种类` 查 `KINDS`；`会话` 写 `one-*`（实际在用性另按 TEAM-02 核验）。
- 状态只写 **已派、进行中、阻塞、完成、作废**（TEAM-24）。已派→进行中；遇阻记阻塞及恢复条件，解除后回进行中；全部完成标准有证据才记完成；撤并记作废并在正文给去向与 intent 依据。复审、返工、待验收仍为进行中；「已交」只作正文事件，不新增状态。完成卡被发现未履约时恢复进行中、正文说明原因，不按分支已合入冒充验收。
- `check_tasks` 保持现行兼容读取与告警边界：旧状态映射见 `LEGACY_STATUS`，目前不是五值硬门；QUA-14 先归一后收紧。模板写法的五值要求不宣称该门已上线。
- `分支` 写实际分支或 `待核`；`提交` 写可核提交或 `待核`；`返工轮次` 为非负整数或 `待核`；`用时` 格式为非负分钟、`12m`、`1h30m`、`约 N 分钟` 或 `待核`；不得拿提交间隔冒充执行工时。`最后更新` 为 ISO 日期／时刻或 `待核`，执行或回填时更新，精确时刻优先带时区。
- finish 核 PR／复审及卡完成标准，写交出事实与节点标记，按 TEAM-53 及 QUA-01 共用投递、同事件去重；未验收完整的卡保持进行中，不另发提交号。
- 合入回填按任务号找卡与负责会话；一卡多 PR 不凭其中一个合入即关卡。符合完成标准才填完成，`提交` 填最终合入提交，保留执行者与返工事实；执行用时与派出至合入周期必须分别注明来源，未知值保留待核。适用 bc 状态按 MRG-02 更新；回填以文档合集交付，失败写 GAPS，不能静默关卡。调度 10-23 定：头部「用时」= 交付周期（派工至最终合入），由 MRG-02 合入时自动回填；执行工时另记正文「执行用时」节（可写待核），不新增头部键。

复制下面模板时填写占位值；头部内不保留尖括号说明。正文须明确目标、完成标准、前置、允许改动范围与不得做。

## 模板

```markdown
---
任务号: <主线码-MMDD-会话码-两位序号，如 GOV-1023-COO-06>
任务名: <[主线中文名·bc] 动作 + 对象>
主线: <NAM|CON|ARC|RUL|DAT|IMP|CI|OPS|GOV>
bc: <glossary/bcs.yaml 的 id 或 跨域>
种类: <设计|编码|测试|取证|复审|确认页|文档>
会话: <one-会话名>
执行者与模型: <执行者、模型与强度>
状态: 已派
分支: 待核
提交: 待核
返工轮次: 0
用时: 待核
旧编号: 待核
最后更新: <ISO 日期或带时区时刻>
---
# 派工卡 <任务号>：<一句话标题>

## 目标

<要改变的具体行为>

## 完成标准

<逐项可核验条件及核验命令>

## 1. 卡号、前置与类别
- 前置：<已有任务号或无；未满足时保持阻塞并说明原因>
- 旧编号或复审编号：<如 INT-H3、R-4、F-7>
- 类别：缺陷修复 | 功能补做（应交付未做） | 结构性重构（含改名）   ← 只选一个（SESSION_PLAYBOOK 附 M1 第 5 条）
- 批次与整合分支：<批次名；缺陷卡一张或几张一个小 PR，直接合入 main（SESSION_PLAYBOOK 附 M1 第 6 条）>

## 2. 依据
- 规格条款：<contract.md 的 D 编号 / 规格文件:节>
- 规则卡：<RULE-nnn，及 RULE_REVIEWS.json 中的结论>（派工前运行 `python3 docs/modernization/rules/build_rule_index.py --dispatch <本卡.md>`，列出的“老行为未评审”“新规则未评审”先评审再派）
- ADR：<ADR-nnnn 第 n 条>
- 用户原话：<「……」（intent 片段文件名）>
- 复审意见：<复审报告:编号，附复现步骤>

## 3. 范围与卡上文件
- 允许改动的文件 / 目录（越界先停下报告，不先做后报）：
  - <路径>
- 归属（SESSION_PLAYBOOK 附 M1 第 1 条）：本卡独占的表、权限码、事件、迁移文件、共享构件：<…>
- 不碰：<验收测试目录、他卡文件、共享构件（需改时停下报告）>

## 4. 验收测试（由测试代理先写，SESSION_PLAYBOOK 附 M1 第 4 条、TEAM-69）
- 测试代理：`code-modernization:test-engineer`（模型 <sonnet/opus>），先于实现提交，提交说明带 `Acceptance-Test-Author: test-engineer`
- 路径：后端 `backend/src/test/java/com/forcitis/one/acceptance/<子域>/<bc>/…`、`backend/src/test/resources/acceptance/…`；前端 `frontend/**/__acceptance__/…`
- 测试名（`@DisplayName` 以编号开头）：
  - `<编号> <Given/When/Then 一句话>`
- 实现方认为验收测试有误：停下报告，由复审裁定（`Acceptance-Ruling: RULING-n`），不自行修改

## 5. 执行者与模型
- 执行者：<子代理 / Codex / 工作会话名>
- 模型与强度：<sonnet | opus | Codex：codex exec -m <模型> -c model_reasoning_effort=<强度> -s workspace-write -c sandbox_workspace_write.network_access=false --ephemeral -o <结果文件> < /dev/null>
- 工作区：独立 worktree，分支 <claude/…>，基线 <main 提交号>
- 复审：按 TEAM-67（代码首轮 opus；一般改后作者交 Codex 复核并贴 PR；安全及生产改动修后 opus 终审；纯文档作者自检），填写本卡适用类型

## 6. 禁止事项（必填「不得做」清单：权限边界、不入仓生成物；并写明与 CI、其他分支、在途片段的衔接，资源与时限，自测须覆盖并发、空输入、续行、重复跑并附破坏验证——SESSION_PLAYBOOK TEAM-20）
- 不改卡外文件；不顺手重构、改名或统一风格（缺陷卡）
- 不放宽、不删除断言；不改验收测试目录
- 不联网、不起容器、不建账号、不调用对外接口；需要时按 TEAM-91 请求授权，本卡明确禁止时不得执行
- 不打印、不写入密钥；测试密钥运行时生成
- <本卡特有：例如不改业务语义、不改事件形状>

## 7. 预期表结构 diff（涉及 DDL 时必填，SESSION_PLAYBOOK 附 M3「规则调整三类」）
- 方法：两个空库分别执行改动前、后的基线，`pg_dump --schema-only` 对比
- 预期差异只能是：
  - <schema.table：列 / 约束 / 默认值 / 索引的变化>
- 不得出现的差异：<…>

## 8. 交付物与自查
- 交付物：<代码、测试、守护、文档、OpenAPI、例外清单的增删>
- 自查：`scripts/precheck.sh <基线>` 全部 PASS 或有理由的 SKIP，输出贴进报告与 PR「precheck.sh 输出」
- 受影响模块测试：<precheck 给出的命令>；全量交 CI，卡另有要求时列明（TEAM-88）；本卡禁止起容器时不得运行会起容器的检查
- 报告分开写「已验证」与「未运行」

## 9. 复审要求（SESSION_PLAYBOOK 附 M1 第 2、7 条）
- Blocker / High：合并前修完，复核模型与责任按 TEAM-67
- Medium / Low：执行者补测试、CI 通过即关闭，负责会话抽查；不阻塞合并的登记进下一批
- 复审清单：DATA_DESIGN_CHECKLIST §14、§14.1；CODING_STANDARD 相关节：<…>

## 10. 报告去向
- 常规状态在 TEAM-53 四节点上报；阻塞、安全事件、用户决定与需动作的请求按该条送达 <负责会话>，需调度处理的报 one-coordinator
- PR／卡／交接记录：分支、改动文件、验证结果（已验证 / 未运行）、precheck 输出、未完成项、需用户决定事项；提交号写 PR 描述或分支，不单独发，文档合集按 TEAM-53 自扫认领；Codex 卡另记返工轮次与耗时（TEAM-52）
```

## 示例：R-4 去数据库时钟默认值

以下只示正文片段；正式卡须套用上方任务头，并补「目标」「完成标准」。

```markdown
# 派工卡 R-4：去掉数据库时钟默认值，时刻由注入 Clock 显式写入

## 1. 目标、完成标准与类别
- 卡号：R-4（并入 INT-L23）
- 类别：结构性重构
- 批次与整合分支：Phase 1a 下一批 W1，排在 R-2 合并之后；单独 PR

## 2. 依据
- 规格条款：NAMING_STANDARD §2.4a 第 3 条「数据库只允许技术默认值……业务值与时间一律由应用显式写入」；G25
- 规则：CODING_STANDARD CS-2.26、CS-3.8；`TimeSourceGuardTest` 已禁止 SQL 字符串读数据库时钟
- 计划：`phase1a/NEXT_BATCH_PLAN.md` §2.2 R-4 行与第 21 行（102 处、31 个文件：`DEFAULT` 99 处，DML `now()` 3 处：numbering `V20261014135200:11`、`V20261014135300:3`、`V20261015000300:78`）
- 用户原话：「开放期内直接改基线 (Recommended)」（迁移基线写法，2026-10-15）；Codex 派工：「用最合适的模型，你试试」
- 复审意见：修复批时区项范围外发现（INT-L23）

## 3. 范围与卡上文件
- 允许改动：
  - `backend/src/main/resources/db/migration/**`（开放基线中的时钟 `DEFAULT` 与 3 处 `now()`）
  - 写这些列的 `backend/src/main/java/**/infrastructure/persistence/Jdbc*Repository.java` 与其 application 调用方（只为传入 `Clock` 的时刻）
  - `backend/src/test/java/com/forcitis/one/TimeSourceGuardTest.java`（扩展为扫描迁移文件）
  - `backend/architecture/sql-database-clock-exceptions.txt`（只允许保持为空）
- 归属：本卡独占上述迁移文件中的默认值子句；不改列名、类型、约束
- 不碰：验收测试目录；框架自建表（Spring Session、Modulith 事件登记、ShedLock `usingDbTime`）与 Flowable `act_*`（FLW-1 另案）；种子迁移是否保留时钟写法先报告

## 4. 验收测试（测试代理先写）
- 测试代理：`code-modernization:test-engineer`（sonnet），先提交，trailer `Acceptance-Test-Author: test-engineer`
- 路径：`backend/src/test/java/com/forcitis/one/acceptance/platform/numbering/`、`…/acceptance/organization/workforce/` 等受影响模块子包
- 测试名：
  - `R-4 固定 Clock 下新建记录的 created_at 等于 Clock 时刻，而不是数据库时刻`
  - `R-4 编号取号更新的时刻来自注入 Clock`（覆盖 3 处 `now()`）
  - `R-4 迁移文件中不出现时钟类默认值与 now()`（守护负向样例：注入一条 `DEFAULT now()` 被拦下）

## 5. 执行者与模型
- 执行者：Codex（试点）
- 模型与强度：`codex exec -m gpt-6.1-sol -c model_reasoning_effort=high -s workspace-write -c sandbox_workspace_write.network_access=false --ephemeral -o <结果文件> < /dev/null`
- 工作区：独立 worktree，分支 `claude/one-1a-next-r4`，基线为 R-2 合并后的 main
- 复审：opus，high；PR 正文注明「Codex 实现（gpt-6.1-sol，high）」

## 6. 禁止事项
- 不改表结构以外的业务语义；不改列名、类型（R-2 已完成）
- 不往 `sql-database-clock-exceptions.txt` 加行
- 不改验收测试；不放宽 `TimeSourceGuardTest` 已有断言
- 不联网、不起容器（Testcontainers 本机临时测试例外按 TEAM-91，当前卡的禁容器限制优先）

## 7. 预期表结构 diff
- 两个空库分别执行改动前、后的基线，`pg_dump --schema-only` 对比
- 预期差异只能是：99 处列的 `DEFAULT now()` / `DEFAULT current_timestamp` 被删除（逐列列在 PR 中）
- 不得出现：列、类型、可空性、约束、索引的任何变化；`version` 等技术默认值的变化

## 8. 交付物与自查
- 交付物：基线迁移去时钟默认值；各 INSERT / UPDATE 显式传入 `Clock` 时刻；`TimeSourceGuardTest` 扫描迁移文件并带负向样例
- 自查：`scripts/precheck.sh origin/main` 输出（迁移不可变、数据库脚本一致、gitleaks 须 PASS）
- 受影响模块测试：precheck 给出的 `./mvnw -q test -Dtest=…`；交付前 `scripts/ci/backend_verify.sh`
- 报告分开写「已验证」与「未运行」

## 9. 复审要求
- opus 核对：表结构 diff 只含声明的默认值删除；每处 INSERT 都传入时刻（无遗漏写 `null`）；守护对迁移生效且例外清单为空
- Blocker / High 合并前修完并按 TEAM-67 复核；Medium / Low 登记进下一批

## 10. 报告去向
- 节点报告与需动作的请求按 TEAM-53 送负责会话；返工轮次与耗时写卡及 PR（TEAM-52），不单独报提交号
```
