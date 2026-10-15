# 提议术语清单（第三轮用语确认用）

<!-- 本文件由 backend/glossary/build_proposed_index.py 从同目录 *.yaml 生成，不要手改；改 yaml 后运行 python3 docs/modernization/build_all.py 并提交结果。 -->

`terms` 只放业务术语，纯技术词（动词、框架词、值对象方法名等）放同文件的 `common_words`。本表按文件列出除错误码以外的全部提议术语；「是否疑似方言」由条目中类型为方言或待定的别名与禁用写法生成，空表示未登记问题；「备注」为条目 note 原文。

修改任何一个 yaml 后运行 `./mvnw -B verify`（GlossaryTest 会校验字段、重名和未登记词）与 `python3 docs/modernization/build_all.py`（重新生成本文件）。

## `architecture-acl.yaml`

涉及模块：`organization.workforce`、`platform.integration`。

ADR-0039（防腐层边界）引入的术语（proposed，待业务评审，ADR-0018）。登记会话：one-architecture，2026-10-15。

| id | zh | en | 定义 | 是否疑似方言 | 备注 |
|---|---|---|---|---|---|
| `org_source_sync` | 组织来源同步 | OrgSourceSync | 集成层从组织来源系统拉取组织与人员数据、做缩水保护与差异比对、翻译成组织变更集并交组织人事应用的同步机制（含运行记录与运维放行） |  | 取代原 organization.workforce 内的同步运行（SyncRun）机制 |
| `org_change_set` | 组织变更集 | OrgChangeSet | 集成层翻译成新系统用语后交给组织人事的一批组织单元、员工与任职变更；以同步运行 ID 作幂等键，整批在一个事务内应用 |  |  |
| `apply_org_change_set` | 应用组织变更 | ApplyOrgChangeSet | 组织人事 api 中唯一的同步入口：应用一份组织变更集，管理员离职挂为待放行离职，其余变更照常生效；同一变更集重复调用返回上次结果 |  |  |
| `separation_hold` | 待放行离职 | SeparationHold | 会使可用超级管理员少于 2 名、或涉及超级管理员与安全审计员持有人的离职，被单独挂起等待另一名持有人放行的记录（ADR-0027 第 1 项第 7 条、ADR-0028） |  | 权限码 organization.workforce:separation-hold:approve 已用此词（ADR-0028 第 2 条） |
| `federated_identity` | 联合身份 | FederatedIdentity | 由外部身份提供方（企业微信、致远单点、签名票据签发方）认证、集成层校验并按外部身份对照映射到本系统用户的登录方式；本系统只据此决定能否登录并发会话 |  | 细节待「人员入职到账号开通」身份生命周期研究（ADR-0039 第 3 条） |

## `contract-evolution.yaml`

契约演进与部署排空用语（proposed，ADR-0035，待用语确认，ADR-0018）。

无错误码以外的术语。

## cost.timesheet（`cost-timesheet.yaml`）

cost.timesheet 提议术语（proposed，待第三轮用语确认，ADR-0018）。

无错误码以外的术语。

另有错误码 12 个（kind 为 `error_code`）、技术词 23 个（`common_words`），不在表中。

## `data-migration.yaml`

涉及模块：`accounting.period`、`operations.facts`、`platform.integration`、`platform.migration`。

迁入的其余用语（拒收、核对、历史期间、迁入标记等）随 ADR-0036（另行 PR）登记。

通用词另增 `batch`、`count`（批量命令后缀与计数端点，NAMING_STANDARD §2.6 A-3、A-6）。

| id | zh | en | 定义 | 是否疑似方言 | 备注 |
|---|---|---|---|---|---|
| `migration_batch` | 迁入批次 | MigrationBatch | 一次历史数据迁入运行；登记来源系统、对象类型、范围、状态、行数与来源 ID 摘要；批次挂在来源对照与原编号对照上，业务表不带批次列 | 「导入批次」已禁用；「import_batch」已禁用；「ImportBatch」已禁用 | 用户 2026-10-15 定批次术语唯一写法；platform.numbering 的 import_batch 已在 R-1b P3a（#139）改为 migration_batch_id，同一提交加入 forbidden 并删去 platform-integration.yaml 中的重复条目 |
| `migration_orchestration` | 迁入编排 | MigrationOrchestration | 按依赖顺序调用集成层与各目标 BC 迁入用例、推进迁入批次的独立模块（暂定 platform.migration）；只依赖它们的 api，不被任何模块依赖（ADR-0036 第 1 条） |  | 模块名在实现 PR 定 |
| `migration_reject` | 迁入拒收 | MigrationReject | 迁入时被拒绝的一条来源行，带批次、来源对象、来源 ID 与原因代码；处置经应用内带权限的操作录入，批次提交前须逐条处置（ADR-0036 第 7 条） |  |  |
| `migration_reject_reason__unmappable_value` | 取值无法映射 | UnmappableValue | 迁入拒收原因代码：来源取值在映射表中没有对应的自有取值（ADR-0036 第 7 条） |  |  |
| `migration_reject_reason__required_missing` | 必填缺失 | RequiredMissing | 迁入拒收原因代码：目标必填项在来源中为空（ADR-0036 第 7 条） |  |  |
| `migration_reject_reason__number_conflict` | 编号冲突 | NumberConflict | 迁入拒收原因代码：原编号与已有编号在（单据类型, 编号）上冲突（ADR-0036 第 7 条） |  |  |
| `migration_reject_reason__original_missing` | 原行缺失 | OriginalMissing | 迁入拒收原因代码：冲销行的原行不在本批次也不在目标库中（ADR-0036 第 7 条） |  |  |
| `migration_reject_reason__legal_entity_out_of_scope` | 法人不在范围 | LegalEntityOutOfScope | 迁入拒收原因代码：来源行的法人不在新系统的法人范围内（ADR-0036 第 7 条） |  |  |
| `migration_reject_reason__trial_commit_failed` | 试提交失败 | TrialCommitFailed | 迁入拒收原因代码：试提交中领域校验或数据库约束不通过（ADR-0036 第 7 条） |  |  |
| `migration_reject_disposition__rerun_after_fix` | 修正后重跑 | RerunAfterFix | 迁入拒收处置取值：修正映射或来源后由新的暂存重跑（ADR-0036 第 7 条） |  |  |
| `migration_reject_disposition__excluded` | 确认不迁 | Excluded | 迁入拒收处置取值：确认该行不迁入；权责事实须财务负责人确认，其他由业务管理员确认（ADR-0036 第 7 条） |  |  |
| `migration_tie_out` | 迁入核对 | MigrationTieOut | 一个迁入批次按期间 × 法人 × 对象类型比对来源侧聚合与暂存的行数、金额合计、来源 ID 摘要与按来源单号的行数金额的结果；零差异或经财务签字后批次才能提交（ADR-0036 第 8 条） |  | 不用 Reconciliation：已批准术语「经营勾稽」Reconciliation 指跨口径对账 |
| `period_status__historical` | 历史期间 | Historical | 期间状态的一个取值，终态：FORCITIS one 首个业务期间之前的 (期间, 法人)，永不接受业务过账，只接受迁入过账通道；不逐期关账、不发关账事件（ADR-0036 第 6 条） |  | 会计类用语，待财务确认（ADR-0018；ADR-0036 U-3） |
| `migration_period_reservation` | 迁入占用 | MigrationPeriodReservation | 迁入批次对 (期间, 法人) 的占用登记，由 accounting.period 自持；有未解除占用的期间不得关账，批次转为提交中时加锁确认期间状态（ADR-0036 第 6 条） |  |  |
| `migration_marker` | 迁入标记 | MigrationMarker | 事实行上区分原生与迁入的标记；只在事实行，单据表不带；accounting.ledger 不为迁入标记的事实生成分录（ADR-0036 第 5、6 条） |  | 列名在实现 PR 中按 NAMING_STANDARD 定 |
| `migration_posting_channel` | 迁入过账通道 | MigrationPostingChannel | operations.facts 中只供迁入用例使用的过账入口：期间可为历史期间或开放期间，不发布事件（ADR-0036 第 6 条） |  |  |
| `migration_staging` | 迁入暂存 | MigrationStaging | 来源行转换为目标形状后存放的集成层暂存表（Flyway 按对象类型建固定表，带批次列、来源键、摘要与核对所需来源取值）；试提交与核对都针对它，核对通过后才提交目标库（ADR-0036 第 7 条） |  | 暂存只有一份，不另存来源快照 |
| `trial_commit` | 试提交 | TrialCommit | 用与正式提交同一套代码把整批写入目标库后回滚事务，以跑全部领域校验与数据库约束（ADR-0036 第 7 条） |  |  |
| `migration_batch_status__staging` | 暂存中 | Staging | 迁入批次状态取值：正在抽取、转换与暂存，可试提交（ADR-0036 第 3 条） |  |  |
| `migration_batch_status__awaiting_tie_out` | 待核对 | AwaitingTieOut | 迁入批次状态取值：暂存与试提交完成，等待核对通过或签字（ADR-0036 第 3 条） |  |  |
| `migration_batch_status__committing` | 提交中 | Committing | 迁入批次状态取值：核对已通过，正在分块提交目标库（ADR-0036 第 3 条） |  |  |
| `migration_batch_status__effective` | 已生效 | Effective | 迁入批次状态取值：提交后复核一致；可转为已冲销（ADR-0036 第 3 条） |  |  |
| `migration_batch_status__reversed` | 已冲销 | Reversed | 迁入批次状态取值：提交阶段失败或生效后发现错误，事实行已整批冲销，终态；重跑为新批次（ADR-0036 第 3 条） |  |  |
| `migration_batch_status__abandoned` | 已放弃 | Abandoned | 迁入批次状态取值：提交前放弃，目标库未受影响，终态（ADR-0036 第 3 条） |  |  |
| `source_reference_status` | 对照状态 | SourceReferenceStatus | 来源对照行的状态：当前 CURRENT / 已冲销 REVERSED；只有事实类对照可为已冲销，冲销与关闭旧对照同一事务（ADR-0036 第 3、9 条） |  | 与确认状态 confirmation_status 是两列；自然键只约束已确认且当前的行；取值「已冲销」REVERSED 与迁入批次状态的「已冲销」同词同义，不另登记 |
| `source_reference_status__current` | 当前 | Current | 对照状态取值：该对照仍指向有效的自有行（ADR-0036 第 3 条） |  |  |
| `source_row_digest` | 来源行摘要 | SourceRowDigest | 来源行规范化后的 SHA-256，存于来源对照与暂存行，用于重跑时判断同一来源行是否变化（ADR-0036 第 3、7 条） |  |  |

另有技术词 2 个（`common_words`），不在表中。

## `finance-decisions.yaml`

财务口径决策单（docs/modernization/r0/FINANCE_DECISIONS.md，2026-10-15）中出现、词表未收的会计与经营用语。

会计类提议术语须经财务口径确认（ADR-0018）；「产值收入」是方言，登记在正式用语「权责收入」下。

无错误码以外的术语。

## `fix-b.yaml`

涉及模块：`organization.workforce`、`platform.identity`、`platform.integration`、`platform.numbering`、`platform.web`。

Phase 1a 修复批 B 新增术语（proposed，待业务评审，ADR-0018）。

无错误码以外的术语。

另有错误码 47 个（kind 为 `error_code`）、技术词 49 个（`common_words`），不在表中。

## `fix-b1-identity.yaml`

涉及模块：`organization.workforce`、`platform.audit`、`platform.identity`、`platform.numbering`。

Phase 1a 修复批 B1（身份与授权）新增术语（proposed，待业务评审，ADR-0018）。ADR-0027 的八项与运维命令用到的词；每个新错误码一条 error__ 条目。

无错误码以外的术语。

另有错误码 28 个（kind 为 `error_code`）、技术词 18 个（`common_words`），不在表中。

## organization.attendance（`fix-b2-data.yaml`）

Phase 1a 修复批 B2（数据架构）新增术语（proposed，待业务评审，ADR-0018）。

无错误码以外的术语。

另有错误码 1 个（kind 为 `error_code`），不在表中。

## organization.workforce（`fix-b2b-events.yaml`）

Phase 1a 修复批 B2b（事件业务日期、指标）新增术语（proposed，待业务评审，ADR-0018）。

| id | zh | en | 定义 | 是否疑似方言 | 备注 |
|---|---|---|---|---|---|
| `snapshot_date` | 快照日 | SnapshotDate | 读取组织源快照的那个业务日，即同步运行开始当天（Asia/Shanghai）。它是 OrgSnapshotChanged 信封的 businessDate，也是同一次同步写入的版本行的生效日 |  |  |

## `fix-c.yaml`

涉及模块：`organization.workforce`、`platform.integration`。

前端修复批新增的界面用语（proposed）：状态、列名与提示语中的业务词，守护要求它们作为完整的词登记，不得由单字拼成。

状态、列名与提示语中的业务词。前端用语守护要求单字只作连接词，状态词（已通过、待认领等）必须作为完整的词登记。

| id | zh | en | 定义 | 是否疑似方言 | 备注 |
|---|---|---|---|---|---|
| `ui_released_state` | 已放行 | ReleasedState | 被中止的同步已由管理员放行并生效 |  | 展示用语；同步记录状态 RELEASED |
| `ui_external_system` | 外部系统 | ExternalSystem | 本系统之外、通过防腐层接入的系统，如企业微信、致远、组织来源 |  | 错误提示用语 |

## `fix-time-zone.yaml`

Phase 1a 时间与时区修复新增用语（proposed，待业务评审，ADR-0018）。纯技术词放 common_words。

无错误码以外的术语。

另有技术词 2 个（`common_words`），不在表中。

## `frontend-ui.yaml`

涉及模块：`organization.workforce`、`platform.approval`、`platform.identity`、`platform.integration`。

前端界面用语中尚不在正式词表里的业务术语（proposed，待第三轮用语确认，ADR-0018）。

| id | zh | en | 定义 | 是否疑似方言 | 备注 |
|---|---|---|---|---|---|
| `legacy_system` | 老系统 | LegacySystem | 被本系统替代的原项目管理系统（待办合并期间只读接入） |  | 疑似方言，第三轮用语确认；界面用语，正式叫法待定 |
| `new_system` | 新系统 | NewSystem | FORCITIS one 本身；待办合并期间与老系统并存，界面用来标出待办的来源 |  | 用户 2026-10-14 指定提议；界面标签、筛选项、提示用语 |
| `trigger_mode` | 触发方式 | TriggerMode | 一次同步是定时触发还是手动触发 |  | 用户 2026-10-14 指定提议；同步记录列名 |
| `held` | 被中止 | Held | 同步记录的状态：被缩水保护中止，等待管理员放行；与「被中止的同步」指同一状态 |  | 用户 2026-10-14 指定提议；同步记录状态值，第三轮用语确认时与 workforce__held_run 合并 |
| `release` | 放行 | Release | 管理员核对同步差异后，让被中止的同步生效的动作 |  | 扫描前端界面文案发现；同步记录页按钮与结果用语 |
| `org_sync` | 同步 | OrgSync | 把组织来源的组织单元与员工变化应用到本系统的一次运行 |  | 扫描前端界面文案发现；与「同步记录」「同步差异」配合使用 |
| `portal` | 门户 | Portal | 公司门户，致远单点登录的入口 |  | 扫描前端界面文案发现；登录页与错误提示用语 |

## kernel（`kernel.yaml`）

`tail_adjustment`（尾调）、`rounding_residual`（舍入差）、`prorate`（按权重拆分）由用户 2026-10-15 XD-07「同意 (Recommended)」确认登记（`docs/modernization/CROSS_CUTTING_DECISIONS.md`）。`reversal_document`、`correction_document`、`document_nature`、`reversal_category`、`reversible_balance`、`derived_reversal`、`original_period`、`change_order_document`、`reverse_action`、`correct_action` 十条是 ADR-0031 第 8 项的提议，待用语确认（U-4）。

| id | zh | en | 定义 | 是否疑似方言 | 备注 |
|---|---|---|---|---|---|
| `system_actor` | 系统操作主体 | SystemActor | 以 SYSTEM 身份发出事件的作业、监听器或运维入口；名字人为声明在 backend/architecture/system-actors.txt，事件信封 actorId 为该名字在固定命名空间下的 UUIDv5，同一名字恒定、不同于任何用户 ID，任何实例都可由目录反查名字（RULING-P2-C5） |  | RULING-P2-C5 已裁定：声明完整的静态目录，改名新增一行、旧行不删 |

另有技术词 38 个（`common_words`），不在表中。

## `list-paging.yaml`

K-3a 列表分页公共契约新增的纯技术词（proposed，NAMING_STANDARD A-3、A-4）。

无错误码以外的术语。

另有技术词 6 个（`common_words`），不在表中。

## organization.attendance（`organization_attendance.yaml`）

organization.attendance 提议术语（proposed，待业务评审，ADR-0018）。

无错误码以外的术语。

另有错误码 8 个（kind 为 `error_code`）、技术词 8 个（`common_words`），不在表中。

## `organization_workforce.yaml`

涉及模块：`organization.workforce`、`platform.integration`。

Phase 1a organization.workforce 新增术语（proposed，待业务评审，ADR-0018）

新词一律 status: proposed；通用技术词放 common_words，业务概念放 terms。

| id | zh | en | 定义 | 是否疑似方言 | 备注 |
|---|---|---|---|---|---|
| `employment_verdict` | 在职判定 | EmploymentVerdict | 组织快照对某员工是否在职的判定：在职 EMPLOYED、离职 SEPARATED、快照可用而未列出 NOT_LISTED、快照不可用或过期 SNAPSHOT_UNAVAILABLE（ADR-0033 第 3 项第 7 条，应急登录在职校验用） |  | R-1b P2-H2 |
| `login_lookup` | 登录查人 | LoginLookup | 按外部登录标识（企业微信 userid 或外部登录名）在组织快照中找员工；随 DA-3 外部身份移到 identity 后删除 |  | R-1b P2-H2，两个按登录标识查人的方法合一以守住 api 方法上限 |
| `workforce__sync_diff` | 同步差异 | SyncDiff | 一次同步运行改动的对象与字段，以及发现的数据例外（供数据管理员处理） |  |  |
| `workforce__shrink_guard` | 缩水保护 | ShrinkGuard | A8 返回的有效部门数或在职人数降幅超过 20 % 时中止同步，等管理员人工放行 |  |  |
| `workforce__held_run` | 被中止的同步 | HeldRun | 被缩水保护中止、等待管理员放行的同步运行 |  |  |
| `workforce__isolated_row` | 被隔离的行 | IsolatedRow | 缺部门或缺岗位的人员行：不写入、告警，不中断整批同步 |  |  |
| `workforce__broken_org_tree` | 断裂的组织树 | BrokenOrgTree | A8 返回的部门缺少上级路径；整次同步中止，什么都不写 |  |  |
| `workforce__login_name` | 登录名 | LoginName | 员工在组织源的登录名（手机号）；没有即离职（RULE-363） |  |  |
| `workforce__staff_employee` | 人员 | StaffEmployee | 一名员工连同其任职，是同步比较与写入的单位 |  |  |
| `workforce__relationship` | 关系 | Relationship | A8 中人员与组织单元之间的关系（部门负责人、区域应收负责人），类型 ID 为配置项 |  |  |
| `separation_observed_at` | 离职观察时间 | SeparationObservedAt | 组织同步第一次把员工判为离职、并写入快照的那一轮同步的提交时刻；只表示新系统这样判定了，不是人事生效日 | 「离职日期」已禁用；「separation_date」已禁用 | 单说离职日期有歧义，须写明预计离职日期或离职观察时间 |

另有错误码 10 个（kind 为 `error_code`）、技术词 86 个（`common_words`），不在表中。

## platform.approval（`platform-approval.yaml`）

platform.approval 提议术语（proposed，待业务评审，ADR-0018）。单词级术语：服务于代码标识、URL、事件键、错误码的命名守护。

无错误码以外的术语。

另有错误码 28 个（kind 为 `error_code`）、技术词 111 个（`common_words`），不在表中。

## platform.identity（`platform-identity.yaml`）

platform.identity 新增术语（proposed，待业务评审，ADR-0018）。登录、会话、票据、角色与数据范围用到的词。

| id | zh | en | 定义 | 是否疑似方言 | 备注 |
|---|---|---|---|---|---|
| `signing_key_code` | 签名密钥编码 | SigningKeyCode | 票据签发方签名密钥的编码（票据头部的 kid），一方一把；原列名 key_id 按 ADR-0033 影响面改为以 _code 结尾（R-1b P2） |  |  |
| `ticket` | 票据 | Ticket | 一次性登录凭据：致远 OA 票据，或外部系统的签名票据 |  |  |
| `ticket_issuer` | 票据签发方 | TicketIssuer | 被允许签发签名票据的外部系统（KM、组织门户、YD 门户、BI），每方一把密钥，默认关闭 |  |  |
| `sso` | 单点登录 | Sso | 由致远 OA 发起的单点登录 |  |  |
| `external_identity` | 外部身份 | ExternalIdentity | 用户账号在某个身份提供方（企业微信、致远登录）中的标识与该账号的绑定，带绑定区间；登录只经此绑定解析用户 |  | ADR-0033（提议）登记，待用语确认 |
| `provider_kind` | 身份提供方类别 | ProviderKind | 外部身份所属的身份提供方类别，如企业微信用户 ID、致远登录名 |  | ADR-0033（提议）登记，待用语确认 |
| `external_subject` | 外部主体标识 | ExternalSubject | 身份提供方给出的用户标识文本（企业微信 userid、致远登录名），只存于外部身份绑定 |  | ADR-0033（提议）登记，待用语确认 |

另有错误码 3 个（kind 为 `error_code`）、技术词 107 个（`common_words`），不在表中。

## platform.integration（`platform-integration.yaml`）

platform.integration 提议术语（proposed，待业务评审，ADR-0018）。

| id | zh | en | 定义 | 是否疑似方言 | 备注 |
|---|---|---|---|---|---|
| `seeyon` | 致远 | Seeyon | 致远；组织主数据与单点登录验票的外部系统，由集成模块的防腐层读取 |  |  |
| `outbound_delivery` | 外发投递 | OutboundDelivery | 一条待发往外部系统（企业微信）的消息及其发送结果；事务提交后由投递作业发送，失败退避重试，次数用尽进死信 |  |  |
| `recipient` | 收件人 | Recipient | 消息的接收人，用企业微信用户 ID 精确指定；不允许按部门、标签或全员投递 |  |  |
| `source_reference` | 来源对照 | SourceReference | 外部系统或老系统中一个对象的标识与 FORCITIS one 自有 ID 的对应；只在集成层，领域只用自有 ID；同一来源系统、同一类别下来源 ID 与自有 ID 在任一时点各自唯一 |  | ADR-0033 登记；权限码 platform.integration:source-reference:update（授予组织数据管理员）、platform.integration:source-reference:approve（核算组织编码四眼确认，授予业务管理员），授予方式均为 INDIVIDUAL |
| `source_system` | 来源系统 | SourceSystem | 来源对照所属的外部系统，取值为组织源（ORG_SOURCE）、老系统（LEGACY_SYSTEM）、核算系统（ACCOUNTING_SYSTEM），只在集成层与迁入登记中出现 |  | 数据库列叫 source_system，Java 枚举叫 ExternalSource（platform.integration.api），因为 kernel.event.SourceSystem（ONE / MIGRATION）表示事件出自新系统还是历史迁入，是另一回事，不改名 |
| `source_object_kind` | 来源对象类别 | SourceObjectKind | 来源对照所指的自有对象类别（员工、组织单元、岗位、法人主体等），决定自有 ID 指向哪类对象 |  | ADR-0033（提议）登记，待用语确认 |
| `org_source` | 组织源 | OrgSource | 组织与人员的来源系统，来源系统取值 ORG_SOURCE；其员工、组织单元、岗位的标识登记在来源对照里 |  |  |
| `accounting_system` | 核算系统 | AccountingSystem | 接收凭证的核算系统，来源系统取值 ACCOUNTING_SYSTEM；法人在其中的数字编码（核算组织编码）登记在来源对照里 |  |  |
| `rejected_state` | 已拒绝 | RejectedState | 来源对照的确认状态之一：登记的对照被另一个人拒绝，不占自然键约束，不用于出站 |  | 展示用语；对应确认状态 REJECTED。与审批的「驳回」（Reject，platform.approval，审批人对任务给出不同意的结论）不是一回事：这里是另一个人不接受一条登记的来源对照，中文写「已拒绝」，英文命令写 Reject 时不得显示成「驳回」。确认状态的写法：编号规则版本为 PENDING_CONFIRMATION / CONFIRMED，来源对照为 PENDING / CONFIRMED / REJECTED，中文都是待确认 / 已确认 / 已拒绝（ADR-0033 第 2 项第 1 条） |
| `registration` | 登记 | Registration | 由人把一条来源对照录入系统，先处于待确认，等另一个人确认或拒绝；现在只有核算组织编码可以人工登记 |  |  |
| `supersede` | 取代 | Supersede | 已确认的后继对照从生效日起取代生效中的对照：旧对照在后继的生效日结束，两行的来源标识与自有 ID 都不改写 |  |  |
| `evidence` | 取证 | Evidence | 只读核对老系统与 A8 生产数据，用于复刻老行为、迁入对账与新旧等价核对；用专用取证账号，只出计数或聚合，或落 ECS 数据盘，每次登记一行 |  | ADR-0037 登记（数据库登录名 one_evidence_ro），待用语确认 |
| `read_only_verification` | 只读核实 | ReadOnlyVerification | 运维命令：对一条只读外部连接的登录跑与启动校验同一套判定，逐项（ADR-0037 第 4 项 (a)–(g)）输出核实通过、确认违规或判定不了，判定不了时只带 SQL 错误号与 SQLState，按结果给退出码；不输出数据、消息原文与凭据，不监听端口、不迁移、不起作业，口令只从环境读 |  | PAR-2 验收测试登记（档案名 read-only-verification），待用语确认 |
| `read_only_verification_result` | 只读核实结果 | ReadOnlyVerificationResult | 只读核实对每一项与对整条连接给出的结论，取核实通过、确认违规、判定不了之一；整条连接有任一项确认违规即为确认违规，否则有判定不了即为判定不了 |  | PAR-2 验收测试登记，待用语确认 |
| `read_only_verification_result__passed` | 核实通过 | Passed | 只读核实结果的取值：该项（或全部各项）与 RDS 平台基线一致 |  | 不写「通过」：已是审批结论 approve 的中文名 |
| `read_only_verification_result__violated` | 确认违规 | Violated | 只读核实结果的取值：数据库目录确实显示该项超出或缺少基线（与「判定不了」相对） |  | PAR-2 验收测试登记，待用语确认 |
| `read_only_verification_result__undetermined` | 判定不了 | Undetermined | 只读核实结果的取值：查询出错、超时、连不上或返回为空，该项无法判定；启动时同样拒绝启动（ADR-0037 第 4 项第 2 条） |  | PAR-2 验收测试登记，待用语确认 |

另有错误码 7 个（kind 为 `error_code`）、技术词 25 个（`common_words`），不在表中。

## platform.numbering（`platform-numbering.yaml`）

platform.numbering 提议术语（proposed，待业务评审，ADR-0018）。服务于原编号、确认状态、单据类型代码的命名守护。

无错误码以外的术语。

另有错误码 4 个（kind 为 `error_code`）、技术词 3 个（`common_words`），不在表中。

## platform.web（`platform-web.yaml`）

无错误码以外的术语。

另有错误码 5 个（kind 为 `error_code`）、技术词 12 个（`common_words`），不在表中。

## `round3-pending-renames.yaml`

涉及模块：`accounting.voucher`、`cost.timesheet`、`organization.workforce`、`platform.approval`、`platform.identity`、`platform.integration`、`platform.numbering`。

第三轮用语确认的待改名登记（one-glossary 生成，round3/writeback.py）：用户已定新名，旧名仍在代码或界面中使用。

负责会话按 glossary/round3/RENAME_CARDS.md 改名并合入后，one-glossary 删除对应条目，方言禁用随之生效。

| id | zh | en | 定义 | 是否疑似方言 | 备注 |
|---|---|---|---|---|---|
| `round3_pending__act` | 办理 | Act | 第三轮用语确认（R3-AP-04）已定为：办理 / Handle（handle）。代码与界面改名合入前保留旧写法。 |  | 代码仍用 Act（claude/one-1a-par-f1-acceptance） |
| `round3_pending__assigned` | 已分配 | Assigned | 第三轮用语确认（R3-AP-05）已定为：任务已分配 / TaskAssigned（task_assigned）。代码与界面改名合入前保留旧写法。 |  | 代码仍用 Assigned（claude/one-1a-next-3-r1b-p2） |
| `round3_pending__completed` | 已完成 | Completed | 第三轮用语确认（R3-AP-07）已定为：审批已完成 / ApprovalCompleted（approval_completed）。代码与界面改名合入前保留旧写法。 |  | 代码仍用 Completed（main） |
| `round3_pending__escalated` | 已升级 | Escalated | 第三轮用语确认（R3-AP-10）已定为：已上推 / ReroutedToSuperior（rerouted_to_superior）。代码与界面改名合入前保留旧写法。 |  | 代码仍用 Escalated（claude/one-1a-next-3-r1b-p2） |
| `round3_pending__fallback` | 兜底 | Fallback | 第三轮用语确认（R3-AP-11）已定为：转流程管理员 / AdministratorFallback（administrator_fallback）。代码与界面改名合入前保留旧写法。 |  | 代码仍用 Fallback（claude/one-1a-next-3-r1b-p2） |
| `round3_pending__form` | 表单 | Form | 第三轮用语确认（R3-AP-12）已定为：审批表单字段 / ApprovalField（approval_field）。代码与界面改名合入前保留旧写法。 |  | 代码仍用 Form（claude/one-1a-next-3-r1b-p2、claude/one-1a-par-f1-acceptance、claude/one-1a-par-k3a） |
| `round3_pending__round` | 轮次 | Round | 第三轮用语确认（R3-AP-15）已定为：审批轮次 / ApprovalRound（approval_round）。代码与界面改名合入前保留旧写法。 |  | 代码仍用 Round（claude/one-1a-next-3-r1b-p2、claude/one-1a-par-k3a） |
| `round3_pending__row` | 行 | Row | 第三轮用语确认（R3-AP-16）已定为：行级任务 / LineTask（line_task）。代码与界面改名合入前保留旧写法。 |  | 界面文字仍用「行」；代码仍用 Row（claude/one-1a-next-3-r1b-p2、claude/one-1a-par-f1-acceptance、claude/one-1a-par-k3a） |
| `round3_pending__deadline` | 审批时限 | Deadline | 第三轮用语确认（R3-AP-21）已定为：审批时限 / ApprovalTimeLimit（approval_time_limit）。代码与界面改名合入前保留旧写法。 |  | 代码仍用 Deadline（claude/one-1a-par-k3a） |
| `round3_pending__effect` | 效力 | Effect | 第三轮用语确认（R3-AP-22）已定为：办理效力 / TaskEffect（task_effect）。代码与界面改名合入前保留旧写法。 |  | 代码仍用 Effect（claude/one-1a-par-f1-acceptance、claude/one-1a-par-k3a） |
| `round3_pending__release_of_task` | 释放 | ReleaseOfTask | 第三轮用语确认（R3-AP-25）已定为：取消认领 / Unclaim（unclaim）。代码与界面改名合入前保留旧写法。 |  | 界面文字仍用「释放」 |
| `round3_pending__ui_business_summary` | 业务摘要 | BusinessSummary | 第三轮用语确认（R3-AP-41）已定为：审批摘要 / ApprovalSummary（approval_summary）。代码与界面改名合入前保留旧写法。 |  | 界面文字仍用「业务摘要」 |
| `round3_pending__ui_proxy_handling` | 代理 | ProxyHandling | 第三轮用语确认（R3-AP-42）已定为：代理办理 / ProxyHandling（ui_proxy_handling）。代码与界面改名合入前保留旧写法。 |  | 界面文字仍用「代理」 |
| `round3_pending__task_participant` | 参与人 | TaskParticipant | 第三轮用语确认（R3-AP-53）已定为：任务参与人 / TaskParticipant（task_participant）。代码与界面改名合入前保留旧写法。 |  | 界面文字仍用「参与人」 |
| `round3_pending__decision` | 决定 | Decision | 第三轮用语确认（R3-AP-57）已定为：审批决定 / TaskDecision（task_decision）。代码与界面改名合入前保留旧写法。 |  | 代码仍用 Decision（main） |
| `round3_pending__resulted` | 已出结果 | Resulted | 第三轮用语确认（R3-AP-60）已定为：任务已办理 / TaskHandled（task_handled）。代码与界面改名合入前保留旧写法。 |  | 代码仍用 Resulted（main） |
| `round3_pending__skipped` | 已跳过 | Skipped | 第三轮用语确认（R3-AP-65）已定为：通知已跳过 / NotificationSkipped（notification_skipped）。代码与界面改名合入前保留旧写法。 |  | 代码仍用 Skipped（main） |
| `round3_pending__project_manager_review` | 项目经理审批 | ProjectManagerReview | 第三轮用语确认（R3-AP-68）已定为：项目经理审批 / ProjectManagerApproval（project_manager_approval）。代码与界面改名合入前保留旧写法。 |  | 代码仍用 ProjectManagerReview（main） |
| `round3_pending__regional_head_review` | 区域负责人审批 | RegionalHeadReview | 第三轮用语确认（R3-AP-69）已定为：区域负责人审批 / RegionalHeadApproval（regional_head_approval）。代码与界面改名合入前保留旧写法。 |  | 代码仍用 RegionalHeadReview（main） |
| `round3_pending__supervisor_review` | 一级汇报人审批 | SupervisorReview | 第三轮用语确认（R3-AP-70）已定为：直属上级审批 / SupervisorApproval（supervisor_approval）。代码与界面改名合入前保留旧写法。 |  | 代码仍用 SupervisorReview（main） |
| `round3_pending__cutoff` | 撤回截止点 | Cutoff | 第三轮用语确认（R3-AP-74）已定为：撤回截止点 / WithdrawalCutoff（withdrawal_cutoff）。代码与界面改名合入前保留旧写法。 |  | 代码仍用 Cutoff（main） |
| `round3_pending__administrator_mode` | 管理模式 | AdministratorMode | 第三轮用语确认（R3-ID-04）已定为：管理员模式 / AdministratorMode（administrator_mode）。代码与界面改名合入前保留旧写法。 |  | 界面文字仍用「管理模式」 |
| `round3_pending__authorization` | 授权 | Authorization | 第三轮用语确认（R3-ID-10）已定为：授予权限 / GrantPermission（grant_permission）。代码与界面改名合入前保留旧写法。 |  | 界面文字仍用「授权」；代码仍用 Authorization（claude/one-1a-next-3-r1b-p2、claude/one-1a-par-f1-acceptance） |
| `round3_pending__account_disabled` | 停用 | AccountDisabled | 第三轮用语确认（R3-ID-11）已定为：账号停用 / AccountDisabled（account_disabled）。代码与界面改名合入前保留旧写法。 |  | 界面文字仍用「停用」 |
| `round3_pending__permission_adjustment_kind__sensitive_role_holder` | 敏感角色持有人 | SensitiveRoleHolder | 第三轮用语确认（R3-ID-22）已定为：敏感角色复核 / SensitiveRoleReview（permission_adjustment_kind__sensitive_role_review）。代码与界面改名合入前保留旧写法。 |  | 代码仍用 SensitiveRoleHolder（claude/one-1a-par-f1-acceptance） |
| `round3_pending__size` | 大小 | Size | 第三轮用语确认（R3-IN-07）已定为：字节数 / ByteCount（byte_count）。代码与界面改名合入前保留旧写法。 |  | 代码仍用 Size（claude/one-1a-next-3-r1b-p2、claude/one-1a-par-f1-acceptance、claude/one-1a-par-k3a） |
| `round3_pending__keep` | 保留 | Keep | 第三轮用语确认（R3-IN-08）已定为：数据保留期限 / DataRetentionPeriod（data_retention_period）。代码与界面改名合入前保留旧写法。 |  | 代码仍用 Keep（claude/one-1a-par-f1-acceptance） |
| `round3_pending__worm` | 一次写入多次读取 | Worm | 第三轮用语确认（R3-IN-36）已定为：一次写入多次读取 / WriteOnceReadMany（write_once_read_many）。代码与界面改名合入前保留旧写法。 |  | 代码仍用 Worm（main） |
| `round3_pending__voucher_numbering_rule` | 凭证号规则 | VoucherNumberingRule | 第三轮用语确认（R3-NU-08）已定为：记账凭证编号规则 / JournalVoucherNumberingRule（journal_voucher_numbering_rule）。代码与界面改名合入前保留旧写法。 |  | 代码仍用 VoucherNumberingRule（main） |
| `round3_pending__rule_version` | 规则版本 | RuleVersion | 第三轮用语确认（R3-NU-09）已定为：编号规则版本 / NumberingRuleVersion（numbering_rule_version）。代码与界面改名合入前保留旧写法。 |  | 代码仍用 RuleVersion（main） |
| `round3_pending__overhead_hours` | OH 工时 | OverheadHours | 第三轮用语确认（R3-TS-04）已定为：间接工时 / IndirectHours（indirect_hours）。代码与界面改名合入前保留旧写法。 |  | 代码仍用 OverheadHours（main） |
| `round3_pending__workforce__email` | 企业邮箱 | Email | 第三轮用语确认（R3-WF-06）已定为：企业邮箱 / EmailAddress（workforce__email_address）。代码与界面改名合入前保留旧写法。 |  | 代码仍用 Email（claude/one-1a-par-f1-acceptance） |
| `round3_pending__department` | 部门 | Department | 第三轮用语确认（R3-AP-09）已定为：并入 org_unit。代码与界面改名合入前保留旧写法。 |  | 代码仍用 Department（claude/one-1a-next-3-r1b-p2、claude/one-1a-par-f1-acceptance） |
| `round3_pending__ui_task_handler` | 办理人 | TaskHandler | 第三轮用语确认（R3-AP-32）已定为：并入 actor。代码与界面改名合入前保留旧写法。 |  | 界面文字仍用「办理人」 |
| `round3_pending__running` | 进行中 | Running | 第三轮用语确认（R3-AP-61）已定为：并入 ui_approval_running。代码与界面改名合入前保留旧写法。 |  | 代码仍用 Running（main） |
| `round3_pending__ts` | 工时单据类型代码 | Ts | 第三轮用语确认（R3-NU-06）已定为：并入 document_type_code。代码与界面改名合入前保留旧写法。 |  | 代码仍用 Ts（claude/one-design-sync-staging） |
| `round3_pending__qt` | 报价单据类型代码 | Qt | 第三轮用语确认（R3-NU-17）已定为：并入 document_type_code。代码与界面改名合入前保留旧写法。 |  | 代码仍用 Qt（main） |
| `round3_pending__jv` | 记账凭证单据类型代码 | Jv | 第三轮用语确认（R3-NU-18）已定为：并入 document_type_code。代码与界面改名合入前保留旧写法。 |  | 代码仍用 Jv（main） |
| `round3_pending__journal_voucher` | 会计凭证 | JournalVoucher | 第三轮用语确认（R3-AP-26）已定为：记账凭证 / JournalVoucher（journal_voucher）。代码与界面改名合入前保留旧写法。 |  | 界面文字仍用「会计凭证」 |
