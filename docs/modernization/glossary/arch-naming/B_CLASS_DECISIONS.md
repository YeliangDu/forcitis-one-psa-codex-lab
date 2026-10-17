# NAM-1017-GLO-07：B 类定稿及 GLO-06 复审返工

日期：2026-10-17。Codex 起草／实现；GLO-07 返工第 1 轮（扩范围，合并 GLO-06 opus 复审）。本地基准 `9800ecae`，当前独立 worktree；不提交、不推送、不联网、不起容器。

**功能及本机验证结果见下；交付门禁是否通过以末尾 precheck 为准，有 FAIL 不算完成。**

## 机制变更

- 用户裁定确认字符串保持原格式；设计方使用 `设计方定稿，ADR-0053 决定 N 条，NAM-1017-GLO-07`。设计来源必须引用存在的 ADR 决定条款，或 CODING_STANDARD／NAMING_STANDARD 条款；逐字核对内容及段落哈希。
- 用 `meta.promotion_counts` 声明 A/B 数量，逐条核对分类、file/id、原提议完整快照与总数；C 类拒绝进入对照或写回。漏登数量由 `meta.missing_count` 与原始审计清单共同校验。B 源由主数据的 `meta.design_decisions` 明确加载，文件缺失失败。
- B 类采用明确互斥字段 `promote / merge_into / replaced_by / keep_proposed`，必填原因；弃用还要求目标存在、owner、until；合并须保留可检索旧名，不全局禁用通用词。识别业务口径时仅保留提议并标 `escalate_to: C`。
- JSON 按题号核对完整答复（包含 label、choice、submission、unanswered、说明、时间）；intent 对“原话”段落做内容比对与哈希；原话仅泛指“全答”的来源，另逐字核对本批引用的落点原句，不锁无关落点；ADR 只核对指定决定条款；rulings／NM 只核对引用行及关联操作，CAPINFO 核对对象行与提交元数据。整文件 SHA-256 只作审计记录，`--verify` 才作为整文件漂移门禁。
- promotions／missing／terms 的来源键全部要求存在；漏登 registered 的 target、zh、sources 须与操作一致；A 类删除还核对原名、目标名／别名与 BC 迁移依据，复用目标必须有真实裁定支撑。
- 原有自测迁入 `apply_recon_selftest.py`，原 72 个负向用例及正向断言保留；原整文件哈希负向用例改在显式 `verify_hashes=True` 模式执行。新增复审回归及兼容字节快照。`build_terms.py` 未改调用和行为；新词表全部由它生成。

## 105 条结论与删除、基线

**互斥业务统计：转正 97、合并 1、弃用 0、维持提议 4、改归 C 3，共 105。** 机器机制中后两项均为 `keep_proposed`，合计 7 条，不能再把 3 条重复加到 105 上。

- 正式词表 785 → 882（新增 97）；既有词条变更 7 条，逐条见下。
- 从 11 个 proposed YAML 精确删除 98 条（97 转正、1 合并）；全部条目 314 → 216，非错误码 159 → 61。其余条目的值、顶层字段和文件头均保持原文；仅按中4给 `recon_pending__role_deleted` 补 owner／until。README 由 `build_proposed_index.py` 生成。
- term-rules 基线 103 → 102，新增键 0，剩余键和值不变。唯一删除 `proposed/kernel.yaml#system_actor:used_by`：使用方依据 `backend/architecture/system-actors.txt` 已补全。零计数 `event_name`、`command_name` 规则已删除。余项为 metric 75、unique_zh_error_code 10、used_by 17。
- 原 C3、B 类保留 7 条和所有其他未删除提议（包括错误码）完整保留。原 C3 与新增 C3 问题见 [C_CLASS_FOR_N7.md](C_CLASS_FOR_N7.md)。

| 提议文件 | 删除条数 |
|---|---|
| `backend/glossary/proposed/architecture-acl.yaml` | 3 |
| `backend/glossary/proposed/data-classification.yaml` | 2 |
| `backend/glossary/proposed/data-migration.yaml` | 24 |
| `backend/glossary/proposed/enterprise-aggregates.yaml` | 27 |
| `backend/glossary/proposed/fix-b2b-events.yaml` | 1 |
| `backend/glossary/proposed/fix-c.yaml` | 1 |
| `backend/glossary/proposed/frontend-ui.yaml` | 4 |
| `backend/glossary/proposed/kernel.yaml` | 3 |
| `backend/glossary/proposed/organization_workforce.yaml` | 11 |
| `backend/glossary/proposed/platform-identity.yaml` | 9 |
| `backend/glossary/proposed/platform-integration.yaml` | 13 |

## 9 条复审意见逐项处理

| 编号 | 处理结果 |
|---|---|
| 高1 | 已改为内容核对。来源中的无关 JSON 题、NM 操作、rulings 行、intent 落点、ADR 状态记录变动可通过；真正引用内容变动失败。整文件哈希仅显式 --verify 失败。 |
| 高2 | 原 97/97 个来源重新核对通过（30 个文件）；设计来源另 3/3。S033 evidence 补 ADR-0049 决定第 2 条全文及段落哈希；selector 精确定位，历史 batch／question 与 confirmed 字符串保留，以保持旧输出。S047/S049 补完整 CAPINFO 题号、OBJ-122/123 对象行、日期和提交编号。伪造答复／未作答／日期等有负向自测。 |
| 中1 | 来源键、registered target/zh/sources、A 类原名与目标名／别名、BC 迁移及复用目标裁定支撑均校验；漏登第43项与相应 promotion 都补 S032+S033，词条结果不变。 |
| 中2 | 四条事件中文补为已发生事实，旧中文留非首选可检索别名；en/snake、既有确认与其余字段保持不变。见下一节。 |
| 中3 | 采用复审允许的“注明英文待确认”方案：acceptance_evidence 的 note 明示 AcceptanceEvidence 仍为设计候选，用户只裁定宽口径“证据”与定义。未擅自确定新英文，也未引入与治理取证 Evidence 的跨 BC 同名。 |
| 中4 | role_deleted 删除“废除/撤销待用户确认”过期句，明确 B2-B06 已定。recon_pending__role_deleted 补 owner=platform.identity、until=NAM-1017-N7 identity 事件改名合入前（首次部署前）。 |
| 低1 | 按意见保留“第”已从 allow list 删除的现状，未改 copy-glossary.mjs。后续建议：自动 vocabulary 不接收单字块，单字按连接词策略显式管理，避免“第13期”放宽其他文案；应另补“第/期不由该词条自动放行”的负向样例。 |
| 低2 | 60/66 不再作为实现的固定分母：漏登从数据声明并与审计核对，promotions 按 A/B 声明与分类逐项核对。自测中的 60/66 是本批预期断言，保留。 |
| 低3 | 删除 count=0 的 event_name、command_name 两条规则及过期说明；现存规则数与条目数一致。 |

S006 的 F1-05 原答复有一段说明，未把它冒充无说明答复：完整原句逐字核对并登记 `reviewed_note` 与核对原因；没有此记录即失败。CAPINFO 只有已存在交接稿和提交编号，本轮未补造原始用户答复。97/97 是本地来源内容核对通过数，不是宣称本轮见到了所有外部原始提交。

## 既有词条变化清单

| 词条 | 变化 |
|---|---|
| `ecl_alert` | zh：ECL 预警 → 预期信用损失已预警；原中文追加为 standard-zh/deprecated 别名。 |
| `retention_money_release_due` | zh：质量保证金到期返还 → 质量保证金返还已到期；原中文追加为 standard-zh/deprecated 别名。 |
| `revenue_reduction` | zh：收入冲减 → 收入已冲减；原中文追加为 standard-zh/deprecated 别名。 |
| `rule_execution` | zh：规则执行 → 规则已执行；原中文追加为 standard-zh/deprecated 别名。 |
| `acceptance_evidence` | 只追加 note，明确宽口径及英文待确认；en/snake/confirmed 未改。 |
| `role_deleted` | 只替换 note 中的过期“待用户确认”句；en/snake/confirmed 未改。 |
| `sync_run` | B 类 org_sync 合并目标：追加“同步 / OrgSync”两个可用别名、D4 standard_ref、设计确认及合并说明；不写 forbidden。该词条不属于 A66 或漏登60的保护目标。 |

前六项对应中2／中3／中4。其他原有 778 条词条所有字段不变；新建 97 条逐项在 B 类表与 decisions-recon-b.yaml 完整列出。

## 逐字节兼容证明

比较基准是开工 HEAD `9800ecae` 的原脚本与原 decisions，不是扩展中的半成品。两版实际执行 apply，输入为同一份 recon 前 729 条生成词条；新版本仅使用已核验的来源／分类元数据规范化，操作数据保持旧值（不带 B 源及中2–4修正）。两边输出 785 条，序列化字节完全相同。

- 机制前／后同输入 SHA-256：`10e029ab84a5d8d9f554d8e70a4c2e8b51fc05c2bbb47ebc8de8f477b076276e`。
- A66：66 个对照行、61 个不同非空目标；排除明确允许变化的 `role_deleted` 后，60 个目标从两份实际 terms.yaml 提取的词条字节相同，前后 SHA-256 均为 `0562a30065c14528e2781b86428040fa69f39f9efe2f4f8f03c598f10d1ed3ae`。
- missing60：60 个对照行、58 个不同非空目标；排除明确允许变化的 `acceptance_evidence, role_deleted` 后，56 个目标从两份实际 terms.yaml 提取的词条字节相同，前后 SHA-256 均为 `53e395cdf7694fe76cc830c26ca3cbb78386d3f2cf66940dd8088f2ef800b573`。

该最终输出字节快照已进入自测；比较按词条边界保留行尾换行，不把后续追加 B 词条误算成旧最后一条的变化。本机原始机制输出、比较明细与每个变化字段留在 `/tmp/nam-1017-glo-07-r1/`；关键数量、哈希及变化清单已在本报告留存。

## B 类 105 条完整对照

正式项完整 zh/en/snake/kind/scope/bc/definition 及确认来源均在 [decisions-recon-b.yaml](../decisions-recon-b.yaml)；原提议完整快照与分类逐项核对，不改变原业务定义或业务行为。enum／标记在现有 DDD kind 下用 state 作兼容表示，不据此新增状态机。

| 序 | 原条目 | 结论 | 依据与原因 |
|---|---|---|---|
| 1 | `backend/glossary/proposed/architecture-acl.yaml#org_change_set` · 组织变更集 | 转正 `org_change_set`：组织变更集 / OrgChangeSet；value_object / domain / organization.workforce | ADR-0053 决定 4；原据：ADR-0039；按已定含义登记技术结构与英文代码名；保留原定义，不新增业务行为或业务边界。 |
| 2 | `backend/glossary/proposed/architecture-acl.yaml#apply_org_change_set` · 应用组织变更 | 转正 `apply_org_change_set`：应用组织变更 / ApplyOrgChangeSet；command / domain / organization.workforce | ADR-0053 决定 3；原据：ADR-0039；按已定含义登记技术结构与英文代码名；保留原定义，不新增业务行为或业务边界。 |
| 3 | `backend/glossary/proposed/architecture-acl.yaml#separation_hold` · 待放行离职 | 转正 `separation_hold`：待放行离职 / SeparationHold；aggregate / domain / organization.workforce | ADR-0053 决定 4；原据：ADR-0028；ADR-0039；按已定含义登记技术结构与英文代码名；保留原定义，不新增业务行为或业务边界。 |
| 4 | `backend/glossary/proposed/data-classification.yaml#personal_information` · 个人信息 | 转正 `personal_information`：个人信息 / PersonalInformation；state / domain / platform.audit | ADR-0053 决定 3；原据：个人信息保护法第四条；ADR-0043；枚举、标记或分类取值按 state 登记；仅定表示种类，保持原值域和判定条件。 |
| 5 | `backend/glossary/proposed/data-classification.yaml#sensitive_personal_information` · 敏感个人信息 | 转正 `sensitive_personal_information`：敏感个人信息 / SensitivePersonalInformation；state / domain / platform.audit | ADR-0053 决定 3；原据：个人信息保护法第二十八条；ADR-0043；枚举、标记或分类取值按 state 登记；仅定表示种类，保持原值域和判定条件。 |
| 6 | `backend/glossary/proposed/data-migration.yaml#migration_batch` · 迁入批次 | 转正 `migration_batch`：迁入批次 / MigrationBatch；aggregate / acl / platform.integration | ADR-0053 决定 4；原据：用户 2026-10-15 批次术语统一；ADR-0036 历史数据迁入；按已定含义登记技术结构与英文代码名；保留原定义，不新增业务行为或业务边界。 |
| 7 | `backend/glossary/proposed/data-migration.yaml#migration_reject` · 迁入拒收 | 转正 `migration_reject`：迁入拒收 / MigrationReject；entity / acl / platform.integration | ADR-0053 决定 4；原据：ADR-0036 历史数据迁入；按已定含义登记技术结构与英文代码名；保留原定义，不新增业务行为或业务边界。 |
| 8 | `backend/glossary/proposed/data-migration.yaml#migration_reject_reason__unmappable_value` · 取值无法映射 | 转正 `migration_reject_reason__unmappable_value`：取值无法映射 / UnmappableValue；state / acl / platform.integration | ADR-0053 决定 3；原据：ADR-0036 历史数据迁入；枚举、标记或分类取值按 state 登记；仅定表示种类，保持原值域和判定条件。 |
| 9 | `backend/glossary/proposed/data-migration.yaml#migration_reject_reason__required_missing` · 必填缺失 | 转正 `migration_reject_reason__required_missing`：必填缺失 / RequiredMissing；state / acl / platform.integration | ADR-0053 决定 3；原据：ADR-0036 历史数据迁入；枚举、标记或分类取值按 state 登记；仅定表示种类，保持原值域和判定条件。 |
| 10 | `backend/glossary/proposed/data-migration.yaml#migration_reject_reason__number_conflict` · 编号冲突 | 转正 `migration_reject_reason__number_conflict`：编号冲突 / NumberConflict；state / acl / platform.integration | ADR-0053 决定 3；原据：ADR-0036 历史数据迁入；枚举、标记或分类取值按 state 登记；仅定表示种类，保持原值域和判定条件。 |
| 11 | `backend/glossary/proposed/data-migration.yaml#migration_reject_reason__original_missing` · 原行缺失 | 转正 `migration_reject_reason__original_missing`：原行缺失 / OriginalMissing；state / acl / platform.integration | ADR-0053 决定 3；原据：ADR-0036 历史数据迁入；枚举、标记或分类取值按 state 登记；仅定表示种类，保持原值域和判定条件。 |
| 12 | `backend/glossary/proposed/data-migration.yaml#migration_reject_reason__legal_entity_out_of_scope` · 法人不在范围 | 转正 `migration_reject_reason__legal_entity_out_of_scope`：法人不在范围 / LegalEntityOutOfScope；state / acl / platform.integration | ADR-0053 决定 3；原据：ADR-0036 历史数据迁入；枚举、标记或分类取值按 state 登记；仅定表示种类，保持原值域和判定条件。 |
| 13 | `backend/glossary/proposed/data-migration.yaml#migration_reject_reason__trial_commit_failed` · 试提交失败 | 转正 `migration_reject_reason__trial_commit_failed`：试提交失败 / TrialCommitFailed；state / acl / platform.integration | ADR-0053 决定 3；原据：ADR-0036 历史数据迁入；枚举、标记或分类取值按 state 登记；仅定表示种类，保持原值域和判定条件。 |
| 14 | `backend/glossary/proposed/data-migration.yaml#migration_reject_disposition__rerun_after_fix` · 修正后重跑 | 转正 `migration_reject_disposition__rerun_after_fix`：修正后重跑 / RerunAfterFix；state / acl / platform.integration | ADR-0053 决定 3；原据：ADR-0036 历史数据迁入；枚举、标记或分类取值按 state 登记；仅定表示种类，保持原值域和判定条件。 |
| 15 | `backend/glossary/proposed/data-migration.yaml#migration_reject_disposition__excluded` · 确认不迁 | 转正 `migration_reject_disposition__excluded`：确认不迁 / Excluded；state / acl / platform.integration | ADR-0053 决定 3；原据：ADR-0036 历史数据迁入；枚举、标记或分类取值按 state 登记；仅定表示种类，保持原值域和判定条件。 |
| 16 | `backend/glossary/proposed/data-migration.yaml#migration_period_reservation` · 迁入占用 | 转正 `migration_period_reservation`：迁入占用 / MigrationPeriodReservation；entity / acl / accounting.period | ADR-0053 决定 4；原据：ADR-0036 历史数据迁入；按已定含义登记技术结构与英文代码名；保留原定义，不新增业务行为或业务边界。 |
| 17 | `backend/glossary/proposed/data-migration.yaml#migration_marker` · 迁入标记 | 转正 `migration_marker`：迁入标记 / MigrationMarker；state / domain / operations.facts | ADR-0053 决定 3；原据：ADR-0036 历史数据迁入；枚举、标记或分类取值按 state 登记；仅定表示种类，保持原值域和判定条件。 |
| 18 | `backend/glossary/proposed/data-migration.yaml#migration_posting_channel` · 迁入过账通道 | 转正 `migration_posting_channel`：迁入过账通道 / MigrationPostingChannel；entity / acl / operations.facts | ADR-0053 决定 4；原据：ADR-0036 历史数据迁入；按已定含义登记技术结构与英文代码名；保留原定义，不新增业务行为或业务边界。 |
| 19 | `backend/glossary/proposed/data-migration.yaml#migration_staging` · 迁入暂存 | 转正 `migration_staging`：迁入暂存 / MigrationStaging；entity / acl / platform.integration | ADR-0053 决定 4；原据：ADR-0036 历史数据迁入；按已定含义登记技术结构与英文代码名；保留原定义，不新增业务行为或业务边界。 |
| 20 | `backend/glossary/proposed/data-migration.yaml#trial_commit` · 试提交 | 转正 `trial_commit`：试提交 / TrialCommit；command / acl / platform.migration | ADR-0053 决定 3；原据：ADR-0036 历史数据迁入；按已定含义登记技术结构与英文代码名；保留原定义，不新增业务行为或业务边界。 |
| 21 | `backend/glossary/proposed/data-migration.yaml#migration_batch_status__staging` · 暂存中 | 转正 `migration_batch_status__staging`：暂存中 / Staging；state / acl / platform.integration | ADR-0053 决定 3；原据：ADR-0036 历史数据迁入；枚举、标记或分类取值按 state 登记；仅定表示种类，保持原值域和判定条件。 |
| 22 | `backend/glossary/proposed/data-migration.yaml#migration_batch_status__awaiting_tie_out` · 待核对 | 转正 `migration_batch_status__awaiting_tie_out`：待核对 / AwaitingTieOut；state / acl / platform.integration | ADR-0053 决定 3；原据：ADR-0036 历史数据迁入；枚举、标记或分类取值按 state 登记；仅定表示种类，保持原值域和判定条件。 |
| 23 | `backend/glossary/proposed/data-migration.yaml#migration_batch_status__committing` · 提交中 | 转正 `migration_batch_status__committing`：提交中 / Committing；state / acl / platform.integration | ADR-0053 决定 3；原据：ADR-0036 历史数据迁入；枚举、标记或分类取值按 state 登记；仅定表示种类，保持原值域和判定条件。 |
| 24 | `backend/glossary/proposed/data-migration.yaml#migration_batch_status__effective` · 已生效 | 转正 `migration_batch_status__effective`：已生效 / Effective；state / domain / platform.integration | ADR-0053 决定 3；原据：ADR-0036 历史数据迁入；枚举、标记或分类取值按 state 登记；仅定表示种类，保持原值域和判定条件。 |
| 25 | `backend/glossary/proposed/data-migration.yaml#migration_batch_status__reversed` · 已冲销 | 转正 `migration_batch_status__reversed`：已冲销 / Reversed；state / acl / platform.integration | ADR-0053 决定 3；原据：ADR-0036 历史数据迁入；枚举、标记或分类取值按 state 登记；仅定表示种类，保持原值域和判定条件。 |
| 26 | `backend/glossary/proposed/data-migration.yaml#migration_batch_status__abandoned` · 已放弃 | 转正 `migration_batch_status__abandoned`：已放弃 / Abandoned；state / acl / platform.integration | ADR-0053 决定 3；原据：ADR-0036 历史数据迁入；枚举、标记或分类取值按 state 登记；仅定表示种类，保持原值域和判定条件。 |
| 27 | `backend/glossary/proposed/data-migration.yaml#source_reference_status` · 对照状态 | 转正 `source_reference_status`：对照状态 / SourceReferenceStatus；state / acl / platform.integration | ADR-0053 决定 3；原据：ADR-0036 历史数据迁入；枚举、标记或分类取值按 state 登记；仅定表示种类，保持原值域和判定条件。 |
| 28 | `backend/glossary/proposed/data-migration.yaml#source_reference_status__current` · 当前 | 转正 `source_reference_status__current`：当前 / Current；state / acl / platform.integration | ADR-0053 决定 3；原据：ADR-0036 历史数据迁入；枚举、标记或分类取值按 state 登记；仅定表示种类，保持原值域和判定条件。 |
| 29 | `backend/glossary/proposed/data-migration.yaml#source_row_digest` · 来源行摘要 | 转正 `source_row_digest`：来源行摘要 / SourceRowDigest；value_object / acl / platform.integration | ADR-0053 决定 4；原据：ADR-0036 历史数据迁入；按已定含义登记技术结构与英文代码名；保留原定义，不新增业务行为或业务边界。 |
| 30 | `backend/glossary/proposed/enterprise-aggregates.yaml#own_participation_record` · 我方参与记录 | 转正 `own_participation_record`：我方参与记录 / OwnParticipationRecord；entity / domain / masterdata.counterparty | ADR-0053 决定 4；原据：ENTERPRISE_AGGREGATES_DRAFT.md AGG-33；按已定含义登记技术结构与英文代码名；保留原定义，不新增业务行为或业务边界。 |
| 31 | `backend/glossary/proposed/enterprise-aggregates.yaml#project_wbs` · 项目 WBS | 转正 `project_wbs`：项目 WBS / ProjectWbs；aggregate / domain / delivery.execution | ADR-0053 决定 4；原据：ENTERPRISE_AGGREGATES_DRAFT.md AGG-20；PMBOK 工作分解结构（WBS）；按已定含义登记技术结构与英文代码名；保留原定义，不新增业务行为或业务边界。 |
| 32 | `backend/glossary/proposed/enterprise-aggregates.yaml#billing_revenue_limit` · 开票与收入额度 | 转正 `billing_revenue_limit`：开票与收入额度 / BillingRevenueLimit；aggregate / domain / receivables.wip | ADR-0053 决定 4；原据：ENTERPRISE_AGGREGATES_DRAFT.md AGG-44；企业会计准则第 14 号——收入（履约义务）；按已定含义登记技术结构与英文代码名；保留原定义，不新增业务行为或业务边界。 |
| 33 | `backend/glossary/proposed/enterprise-aggregates.yaml#acceptance_evidence_type_admission` · 客户认可证据类型开放配置 | 转正 `acceptance_evidence_type_admission`：客户认可证据类型开放配置 / AcceptanceEvidenceTypeAdmission；aggregate / domain / receivables.wip | ADR-0053 决定 4；原据：ENTERPRISE_AGGREGATES_DRAFT.md AGG-92；ADR-0047 第 4 条；按已定含义登记技术结构与英文代码名；保留原定义，不新增业务行为或业务边界。 |
| 34 | `backend/glossary/proposed/enterprise-aggregates.yaml#invoice_posting_reservation` · 发票入账占用 | 转正 `invoice_posting_reservation`：发票入账占用 / InvoicePostingReservation；entity / domain / accounting.tax | ADR-0053 决定 4；原据：ENTERPRISE_AGGREGATES_DRAFT.md AGG-89；按已定含义登记技术结构与英文代码名；保留原定义，不新增业务行为或业务边界。 |
| 35 | `backend/glossary/proposed/enterprise-aggregates.yaml#receivables_collection__cash_application_reservation` · 核销占用 | 转正 `receivables_collection__cash_application_reservation`：核销占用 / CashApplicationReservation；entity / domain / receivables.collection | ADR-0053 决定 4；原据：ENTERPRISE_AGGREGATES_DRAFT.md AGG-49、AGG-50；按已定含义登记技术结构与英文代码名；保留原定义，不新增业务行为或业务边界。 |
| 36 | `backend/glossary/proposed/enterprise-aggregates.yaml#payables_payment__cash_application_reservation` · 核销占用 | 转正 `payables_payment__cash_application_reservation`：核销占用 / CashApplicationReservation；entity / domain / payables.payment | ADR-0053 决定 4；原据：ENTERPRISE_AGGREGATES_DRAFT.md AGG-52、AGG-53；按已定含义登记技术结构与英文代码名；保留原定义，不新增业务行为或业务边界。 |
| 37 | `backend/glossary/proposed/enterprise-aggregates.yaml#supplier_agreement_performance_limit` · 供方协议履约额度 | 转正 `supplier_agreement_performance_limit`：供方协议履约额度 / SupplierAgreementPerformanceLimit；aggregate / domain / procurement.subcontracting | ADR-0053 决定 4；原据：ENTERPRISE_AGGREGATES_DRAFT.md AGG-58；按已定含义登记技术结构与英文代码名；保留原定义，不新增业务行为或业务边界。 |
| 38 | `backend/glossary/proposed/enterprise-aggregates.yaml#subcontract_change_activated` · 分包变更已生效 | 转正 `subcontract_change_activated`：分包变更已生效 / SubcontractChangeActivated；domain_event / domain / procurement.subcontracting | ADR-0053 决定 3；原据：ENTERPRISE_AGGREGATES_DRAFT.md AGG-56；按已定含义登记技术结构与英文代码名；保留原定义，不新增业务行为或业务边界。 |
| 39 | `backend/glossary/proposed/enterprise-aggregates.yaml#labor_cost_allocation_result` · 人工成本分配结果 | 转正 `labor_cost_allocation_result`：人工成本分配结果 / LaborCostAllocationResult；aggregate / domain / cost.costing | ADR-0053 决定 4；原据：ENTERPRISE_AGGREGATES_DRAFT.md AGG-41；企业会计准则第 1 号——存货（成本分配）；按已定含义登记技术结构与英文代码名；保留原定义，不新增业务行为或业务边界。 |
| 40 | `backend/glossary/proposed/enterprise-aggregates.yaml#cash_accrual_account_mapping` · 收付权责科目映射表 | 转正 `cash_accrual_account_mapping`：收付权责科目映射表 / CashAccrualAccountMapping；aggregate / domain / accounting.ledger | ADR-0053 决定 4；原据：ENTERPRISE_AGGREGATES_DRAFT.md AGG-85；ADR-0022 第 2 条；按已定含义登记技术结构与英文代码名；保留原定义，不新增业务行为或业务边界。 |
| 41 | `backend/glossary/proposed/enterprise-aggregates.yaml#control_account_authorization` · 控制科目授权表 | 转正 `control_account_authorization`：控制科目授权表 / ControlAccountAuthorization；aggregate / domain / accounting.ledger | ADR-0053 决定 4；原据：ENTERPRISE_AGGREGATES_DRAFT.md AGG-94；ADR-0022 第 3 条；按已定含义登记技术结构与英文代码名；保留原定义，不新增业务行为或业务边界。 |
| 42 | `backend/glossary/proposed/enterprise-aggregates.yaml#no_objection_declared` · 无异议已声明 | 转正 `no_objection_declared`：无异议已声明 / NoObjectionDeclared；domain_event / domain / delivery.acceptance | ADR-0053 决定 3；原据：ENTERPRISE_AGGREGATES_DRAFT.md AGG-27；按已定含义登记技术结构与英文代码名；保留原定义，不新增业务行为或业务边界。 |
| 43 | `backend/glossary/proposed/enterprise-aggregates.yaml#design_change_claim_status` · 设计变更主张状态 | 改归 C；保留原提议 | ADR-0053 决定 3；原据：ENTERPRISE_AGGREGATES_DRAFT.md AGG-30；改归 C，涉及业务含义，设计方不裁定。设计变更是否只需“待主张／不主张（附原因）”两种状态？已经向客户主张后，应在本处显示什么？ |
| 44 | `backend/glossary/proposed/enterprise-aggregates.yaml#acceptance_date` · 认可日 | 转正 `acceptance_date`：认可日 / AcceptanceDate；value_object / domain / delivery.acceptance | ADR-0053 决定 4；原据：ADR-0047 第 2、3 条；企业会计准则第 14 号——收入；按已定含义登记技术结构与英文代码名；保留原定义，不新增业务行为或业务边界。 |
| 45 | `backend/glossary/proposed/enterprise-aggregates.yaml#review_period` · 审查期 | 转正 `review_period`：审查期 / ReviewPeriod；value_object / domain / delivery.acceptance | ADR-0053 决定 4；原据：ADR-0047 第 5 条；GF-2015-0209 第 8.1 条；按已定含义登记技术结构与英文代码名；保留原定义，不新增业务行为或业务边界。 |
| 46 | `backend/glossary/proposed/enterprise-aggregates.yaml#expiry_record` · 期满记录 | 转正 `expiry_record`：期满记录 / ExpiryRecord；entity / domain / delivery.acceptance | ADR-0053 决定 4；原据：ADR-0047 第 5 条；按已定含义登记技术结构与英文代码名；保留原定义，不新增业务行为或业务边界。 |
| 47 | `backend/glossary/proposed/enterprise-aggregates.yaml#acceptance_evidence_type` · 客户认可证据类型 | 转正 `acceptance_evidence_type`：客户认可证据类型 / AcceptanceEvidenceType；state / domain / delivery.acceptance | ADR-0053 决定 3；原据：ADR-0047 第 2 条；按已定含义登记技术结构与英文代码名；保留原定义，不新增业务行为或业务边界。 |
| 48 | `backend/glossary/proposed/enterprise-aggregates.yaml#counterparty_bank_account_changed` · 往来单位银行账户已变更 | 转正 `counterparty_bank_account_changed`：往来单位银行账户已变更 / CounterpartyBankAccountChanged；domain_event / domain / masterdata.counterparty | ADR-0053 决定 3；原据：ENTERPRISE_AGGREGATES_DRAFT.md AGG-01；按已定含义登记技术结构与英文代码名；保留原定义，不新增业务行为或业务边界。 |
| 49 | `backend/glossary/proposed/enterprise-aggregates.yaml#bid_won` · 投标已中标 | 转正 `bid_won`：投标已中标 / BidWon；domain_event / domain / presales.opportunity | ADR-0053 决定 3；原据：ENTERPRISE_AGGREGATES_DRAFT.md AGG-11；按已定含义登记技术结构与英文代码名；保留原定义，不新增业务行为或业务边界。 |
| 50 | `backend/glossary/proposed/enterprise-aggregates.yaml#bid_lost` · 投标未中标 | 转正 `bid_lost`：投标未中标 / BidLost；domain_event / domain / presales.opportunity | ADR-0053 决定 3；原据：ENTERPRISE_AGGREGATES_DRAFT.md AGG-11；按已定含义登记技术结构与英文代码名；保留原定义，不新增业务行为或业务边界。 |
| 51 | `backend/glossary/proposed/enterprise-aggregates.yaml#opportunity_lost` · 商机已输单 | 转正 `opportunity_lost`：商机已输单 / OpportunityLost；domain_event / domain / presales.opportunity | ADR-0053 决定 3；原据：ENTERPRISE_AGGREGATES_DRAFT.md AGG-10；按已定含义登记技术结构与英文代码名；保留原定义，不新增业务行为或业务边界。 |
| 52 | `backend/glossary/proposed/enterprise-aggregates.yaml#opportunity_abandoned` · 商机已放弃 | 转正 `opportunity_abandoned`：商机已放弃 / OpportunityAbandoned；domain_event / domain / presales.opportunity | ADR-0053 决定 3；原据：ENTERPRISE_AGGREGATES_DRAFT.md AGG-10；按已定含义登记技术结构与英文代码名；保留原定义，不新增业务行为或业务边界。 |
| 53 | `backend/glossary/proposed/enterprise-aggregates.yaml#agreement_terminated` · 协议已终止 | 转正 `agreement_terminated`：协议已终止 / AgreementTerminated；domain_event / domain / contract.agreement | ADR-0053 决定 3；原据：ENTERPRISE_AGGREGATES_DRAFT.md AGG-13；按已定含义登记技术结构与英文代码名；保留原定义，不新增业务行为或业务边界。 |
| 54 | `backend/glossary/proposed/enterprise-aggregates.yaml#agreement_baseline_activated` · 协议新基线已生效 | 转正 `agreement_baseline_activated`：协议新基线已生效 / AgreementBaselineActivated；domain_event / domain / contract.agreement | ADR-0053 决定 3；原据：ENTERPRISE_AGGREGATES_DRAFT.md AGG-13；按已定含义登记技术结构与英文代码名；保留原定义，不新增业务行为或业务边界。 |
| 55 | `backend/glossary/proposed/enterprise-aggregates.yaml#agreement_project_linked` · 协议项目关联已登记 | 转正 `agreement_project_linked`：协议项目关联已登记 / AgreementProjectLinked；domain_event / domain / contract.agreement | ADR-0053 决定 3；原据：ENTERPRISE_AGGREGATES_DRAFT.md AGG-13；按已定含义登记技术结构与英文代码名；保留原定义，不新增业务行为或业务边界。 |
| 56 | `backend/glossary/proposed/enterprise-aggregates.yaml#project_established` · 项目已建立 | 转正 `project_established`：项目已建立 / ProjectEstablished；domain_event / domain / delivery.execution | ADR-0053 决定 3；原据：ENTERPRISE_AGGREGATES_DRAFT.md AGG-19；按已定含义登记技术结构与英文代码名；保留原定义，不新增业务行为或业务边界。 |
| 57 | `backend/glossary/proposed/enterprise-aggregates.yaml#project_closed` · 项目已结项 | 转正 `project_closed`：项目已结项 / ProjectClosed；domain_event / domain / delivery.execution | ADR-0053 决定 3；原据：ENTERPRISE_AGGREGATES_DRAFT.md AGG-19；按已定含义登记技术结构与英文代码名；保留原定义，不新增业务行为或业务边界。 |
| 58 | `backend/glossary/proposed/enterprise-aggregates.yaml#project_terminated` · 项目已终止 | 改归 C；保留原提议 | ADR-0053 决定 3；原据：ENTERPRISE_AGGREGATES_DRAFT.md AGG-19；改归 C，涉及业务含义，设计方不裁定。项目提前终止是否必须已经发生成本？请与“取消”的判定口径一并确定。 |
| 59 | `backend/glossary/proposed/enterprise-aggregates.yaml#project_cancelled` · 项目已取消 | 改归 C；保留原提议 | ADR-0053 决定 3；原据：ENTERPRISE_AGGREGATES_DRAFT.md AGG-19；改归 C，涉及业务含义，设计方不裁定。项目在开工前取消与执行中终止的区分，是否一定以“尚无成本／已有成本”为界？若已发生成本仍取消，应使用哪个状态？ |
| 60 | `backend/glossary/proposed/fix-b2b-events.yaml#snapshot_date` · 快照日 | 转正 `snapshot_date`：快照日 / SnapshotDate；value_object / acl / organization.workforce | ADR-0053 决定 4；原据：proposals/EVENT_ANALYTICS_FIELDS.md；按已定含义登记技术结构与英文代码名；保留原定义，不新增业务行为或业务边界。 |
| 61 | `backend/glossary/proposed/fix-c.yaml#ui_released_state` · 已放行 | 转正 `ui_released_state`：已放行 / ReleasedState；state / acl / organization.workforce | ADR-0053 决定 3；原据：；按已定含义登记技术结构与英文代码名；保留原定义，不新增业务行为或业务边界。 |
| 62 | `backend/glossary/proposed/fix-c.yaml#ui_external_system` · 外部系统 | 维持提议；待改名或登记载体 | ADR-0053 决定 4；原据：；这是系统目录中的技术泛称，不是有身份与生命周期的领域实体；待 GLO-06 技术模板及系统目录接入后迁移，当前保留 UI 提议覆盖，不造 entity。 |
| 63 | `backend/glossary/proposed/frontend-ui.yaml#trigger_mode` · 触发方式 | 转正 `trigger_mode`：触发方式 / TriggerMode；state / acl / organization.workforce | ADR-0053 决定 3；原据：；枚举、标记或分类取值按 state 登记；仅定表示种类，保持原值域和判定条件。 |
| 64 | `backend/glossary/proposed/frontend-ui.yaml#held` · 被中止 | 转正 `held`：被中止 / Held；state / acl / platform.integration | ADR-0053 决定 3；原据：；枚举、标记或分类取值按 state 登记；仅定表示种类，保持原值域和判定条件。 |
| 65 | `backend/glossary/proposed/frontend-ui.yaml#release` · 放行 | 转正 `release`：放行同步记录 / ReleaseSyncRun；command / acl / platform.integration | ADR-0053 决定 3；原据：；无对象的动作片段改为动词＋已定对象；概念 id 不变，原短名仅作可用别名，通用词不禁用。 |
| 66 | `backend/glossary/proposed/frontend-ui.yaml#org_sync` · 同步 | 合并到 `sync_run`；旧名保留可用别名 | ADR-0053 决定 4；原据：；与既有 sync_run 同为一次组织同步运行；沿 ADR-0039 的组织运行归属复用 organization.workforce 的同步记录，不另立集成层聚合。旧中文“同步”和英文 OrgSync 作可用别名，不作全局禁用。 |
| 67 | `backend/glossary/proposed/kernel.yaml#system_actor` · 系统操作主体 | 转正 `system_actor`：系统操作主体 / SystemActor；role / kernel / kernel | ADR-0053 决定 4；原据：ADR-0033 第 7 项第 1 条；phase1a/NEXT_BATCH_PLAN.md 7.2 C5；静态系统操作主体角色；used_by 精确取现有系统主体目录中的已登记 BC，补齐共享内核使用方，不改变账号或授权。 |
| 68 | `backend/glossary/proposed/kernel.yaml#messaging_account` · 即时通讯账号 | 转正 `messaging_account`：即时通讯账号 / MessagingAccount；value_object / kernel / kernel | ADR-0053 决定 4；原据：ADR-0033 第 3 项第 6 条；phase1a/cards/R-1b-P3b.md 第 13 节；按已定含义登记技术结构与英文代码名；保留原定义，不新增业务行为或业务边界。 |
| 69 | `backend/glossary/proposed/kernel.yaml#external_login_name` · 外部登录名 | 转正 `external_login_name`：外部登录名 / ExternalLoginName；value_object / kernel / kernel | ADR-0053 决定 4；原据：ADR-0033 第 3 项第 6 条；phase1a/cards/R-1b-P3b.md 第 13 节；按已定含义登记技术结构与英文代码名；保留原定义，不新增业务行为或业务边界。 |
| 70 | `backend/glossary/proposed/organization_workforce.yaml#employment_verdict` · 在职判定 | 转正 `employment_verdict`：在职判定 / EmploymentVerdict；value_object / domain / organization.workforce | ADR-0053 决定 4；原据：ADR-0033 第 3 项第 7 条；按已定含义登记技术结构与英文代码名；保留原定义，不新增业务行为或业务边界。 |
| 71 | `backend/glossary/proposed/organization_workforce.yaml#login_lookup` · 登录查人 | 维持提议；待改名或登记载体 | ADR-0053 决定 4；原据：ADR-0033 第 4 项；代码仍使用 organization.workforce.api.LoginLookup（OrgDirectory、OrgDirectoryService）；原定义明确随外部身份迁移删除，待 C1 改形合入后再决定退役，不把临时查人接口转为永久领域概念。 |
| 72 | `backend/glossary/proposed/organization_workforce.yaml#workforce__sync_diff` · 同步差异 | 转正 `workforce__sync_diff`：同步差异 / SyncDiff；entity / acl / platform.integration | ADR-0053 决定 4；原据：RULE-373；按已定含义登记技术结构与英文代码名；保留原定义，不新增业务行为或业务边界。 |
| 73 | `backend/glossary/proposed/organization_workforce.yaml#workforce__shrink_guard` · 缩水保护 | 转正 `workforce__shrink_guard`：缩水保护 / ShrinkGuard；business_rule / acl / organization.workforce | ADR-0053 决定 4；原据：RULE-358；RULE-366；按已定含义登记技术结构与英文代码名；保留原定义，不新增业务行为或业务边界。 |
| 74 | `backend/glossary/proposed/organization_workforce.yaml#workforce__held_run` · 被中止的同步 | 转正 `workforce__held_run`：被中止的同步 / HeldRun；value_object / acl / organization.workforce | ADR-0053 决定 4；原据：RULE-358；按已定含义登记技术结构与英文代码名；保留原定义，不新增业务行为或业务边界。 |
| 75 | `backend/glossary/proposed/organization_workforce.yaml#workforce__isolated_row` · 被隔离的行 | 转正 `workforce__isolated_row`：被隔离的行 / IsolatedRow；value_object / acl / organization.workforce | ADR-0053 决定 4；原据：RULE-365；按已定含义登记技术结构与英文代码名；保留原定义，不新增业务行为或业务边界。 |
| 76 | `backend/glossary/proposed/organization_workforce.yaml#workforce__broken_org_tree` · 断裂的组织树 | 转正 `workforce__broken_org_tree`：断裂的组织树 / BrokenOrgTree；value_object / acl / organization.workforce | ADR-0053 决定 4；原据：RULE-354；按已定含义登记技术结构与英文代码名；保留原定义，不新增业务行为或业务边界。 |
| 77 | `backend/glossary/proposed/organization_workforce.yaml#workforce__login_name` · 登录名 | 转正 `workforce__login_name`：登录名 / LoginName；value_object / acl / platform.integration | ADR-0053 决定 4；原据：RULE-363；按已定含义登记技术结构与英文代码名；保留原定义，不新增业务行为或业务边界。 |
| 78 | `backend/glossary/proposed/organization_workforce.yaml#workforce__staff_employee` · 员工任职快照 | 转正 `workforce__staff_employee`：员工任职快照 / StaffEmployee；value_object / acl / organization.workforce | ADR-0053 决定 4；原据：REIMAGINED_ARCHITECTURE.md §3.3；按已定含义登记技术结构与英文代码名；保留原定义，不新增业务行为或业务边界。 |
| 79 | `backend/glossary/proposed/organization_workforce.yaml#workforce__relationship` · 关系 | 转正 `workforce__relationship`：关系 / Relationship；value_object / acl / platform.integration | ADR-0053 决定 4；原据：RULE-356；按已定含义登记技术结构与英文代码名；保留原定义，不新增业务行为或业务边界。 |
| 80 | `backend/glossary/proposed/organization_workforce.yaml#separation_observed_at` · 离职观察时间 | 转正 `separation_observed_at`：离职观察时间 / SeparationObservedAt；value_object / acl / platform.integration | ADR-0053 决定 4；原据：docs/modernization/rules/WORKFORCE_HISTORY_RULES.md；按已定含义登记技术结构与英文代码名；保留原定义，不新增业务行为或业务边界。 原宽泛禁用词改为非首选检索别名，观察时间不等于人事生效日。 |
| 81 | `backend/glossary/proposed/organization_workforce.yaml#attribution_subject` · 法人归属对象 | 转正 `attribution_subject`：法人归属对象 / AttributionSubject；value_object / domain / organization.workforce | ADR-0053 决定 4；原据：ADR-0033 第 7 项第 4 条（DA-5）；按已定含义登记技术结构与英文代码名；保留原定义，不新增业务行为或业务边界。 |
| 82 | `backend/glossary/proposed/platform-identity.yaml#signing_key_code` · 签名密钥编码 | 转正 `signing_key_code`：签名密钥编码 / SigningKeyCode；value_object / acl / platform.identity | ADR-0053 决定 4；原据：ADR-0033 影响面「ticket_issuers.key_id 改名」；按已定含义登记技术结构与英文代码名；保留原定义，不新增业务行为或业务边界。 |
| 83 | `backend/glossary/proposed/platform-identity.yaml#ticket` · 票据 | 转正 `ticket`：票据 / Ticket；entity / acl / platform.identity | ADR-0053 决定 4；原据：REIMAGINED_ARCHITECTURE.md §3.1 §3.2；按已定含义登记技术结构与英文代码名；保留原定义，不新增业务行为或业务边界。 |
| 84 | `backend/glossary/proposed/platform-identity.yaml#ticket_issuer` · 票据签发方 | 转正 `ticket_issuer`：票据签发方 / TicketIssuer；aggregate / acl / platform.identity | ADR-0053 决定 4；原据：REIMAGINED_ARCHITECTURE.md §3.1 §3.2；按已定含义登记技术结构与英文代码名；保留原定义，不新增业务行为或业务边界。 |
| 85 | `backend/glossary/proposed/platform-identity.yaml#external_identity` · 外部身份 | 转正 `external_identity`：外部身份 / ExternalIdentity；entity / acl / platform.identity | ADR-0053 决定 4；原据：adr/ADR-0033.md；按已定含义登记技术结构与英文代码名；保留原定义，不新增业务行为或业务边界。 |
| 86 | `backend/glossary/proposed/platform-identity.yaml#provider_kind` · 身份提供方类别 | 转正 `provider_kind`：身份提供方类别 / ProviderKind；state / acl / platform.identity | ADR-0053 决定 3；原据：adr/ADR-0033.md；按已定含义登记技术结构与英文代码名；保留原定义，不新增业务行为或业务边界。 |
| 87 | `backend/glossary/proposed/platform-identity.yaml#external_subject` · 外部主体标识 | 转正 `external_subject`：外部主体标识 / ExternalSubject；value_object / acl / platform.identity | ADR-0053 决定 4；原据：adr/ADR-0033.md；按已定含义登记技术结构与英文代码名；保留原定义，不新增业务行为或业务边界。 |
| 88 | `backend/glossary/proposed/platform-identity.yaml#single_operator_mode` · 单人运维模式 | 转正 `single_operator_mode`：单人运维模式 / SingleOperatorMode；state / domain / platform.identity | ADR-0053 决定 3；原据：adr/src/ADR-0030.md；枚举、标记或分类取值按 state 登记；仅定表示种类，保持原值域和判定条件。 |
| 89 | `backend/glossary/proposed/platform-identity.yaml#single_operator_relaxation` · 单人运维放宽项 | 转正 `single_operator_relaxation`：单人运维放宽项 / SingleOperatorRelaxation；state / domain / platform.identity | ADR-0053 决定 3；原据：adr/src/ADR-0030.md；枚举、标记或分类取值按 state 登记；仅定表示种类，保持原值域和判定条件。 |
| 90 | `backend/glossary/proposed/platform-identity.yaml#super_admin` · 超级管理员 | 转正 `super_admin`：超级管理员 / SuperAdmin；value_object / domain / platform.identity | ADR-0053 决定 4；原据：adr/src/ADR-0030.md；按已定含义登记技术结构与英文代码名；保留原定义，不新增业务行为或业务边界。 |
| 91 | `backend/glossary/proposed/platform-integration.yaml#outbound_delivery` · 外发投递 | 转正 `outbound_delivery`：外发投递 / OutboundDelivery；aggregate / acl / platform.integration | ADR-0053 决定 4；原据：REIMAGINED_ARCHITECTURE.md §3.7 §3.9；按已定含义登记技术结构与英文代码名；保留原定义，不新增业务行为或业务边界。 |
| 92 | `backend/glossary/proposed/platform-integration.yaml#recipient` · 收件人 | 转正 `recipient`：收件人 / Recipient；role / acl / platform.integration | ADR-0053 决定 4；原据：SECURITY_INVARIANTS.md I-6；按已定含义登记技术结构与英文代码名；保留原定义，不新增业务行为或业务边界。 |
| 93 | `backend/glossary/proposed/platform-integration.yaml#source_reference` · 来源对照 | 转正 `source_reference`：来源对照 / SourceReference；aggregate / acl / platform.integration | ADR-0053 决定 4；原据：adr/ADR-0033.md；按已定含义登记技术结构与英文代码名；保留原定义，不新增业务行为或业务边界。 |
| 94 | `backend/glossary/proposed/platform-integration.yaml#source_system` · 来源系统 | 转正 `source_system`：来源系统 / SourceSystem；state / acl / platform.integration | ADR-0053 决定 3；原据：adr/ADR-0033.md；按已定含义登记技术结构与英文代码名；保留原定义，不新增业务行为或业务边界。 |
| 95 | `backend/glossary/proposed/platform-integration.yaml#source_object_kind` · 来源对象类别 | 转正 `source_object_kind`：来源对象类别 / SourceObjectKind；state / acl / platform.integration | ADR-0053 决定 3；原据：adr/ADR-0033.md；按已定含义登记技术结构与英文代码名；保留原定义，不新增业务行为或业务边界。 |
| 96 | `backend/glossary/proposed/platform-integration.yaml#rejected_state` · 已拒绝 | 转正 `rejected_state`：已拒绝 / RejectedState；state / acl / platform.integration | ADR-0053 决定 3；原据：adr/ADR-0033.md；按已定含义登记技术结构与英文代码名；保留原定义，不新增业务行为或业务边界。 |
| 97 | `backend/glossary/proposed/platform-integration.yaml#registration` · 登记 | 维持提议；待改名或登记载体 | ADR-0053 决定 3；原据：adr/ADR-0033.md；待改名：SourceReference.isRegistrationAllowed()/requireRegistrationAllowed() 仍使用 registration，实际 GlossaryTest G37 已复现；先保留原提议覆盖，platform.integration 完成 N7 改名后再转正 RegisterSourceReference。 |
| 98 | `backend/glossary/proposed/platform-integration.yaml#supersede` · 取代 | 转正 `supersede`：取代来源对照 / SupersedeSourceReference；command / acl / platform.integration | ADR-0053 决定 3；原据：adr/ADR-0033.md；无对象的动作片段改为动词＋已定对象；概念 id 不变，原短名仅作可用别名，通用词不禁用。 |
| 99 | `backend/glossary/proposed/platform-integration.yaml#evidence` · 取证 | 维持提议；待改名或登记载体 | ADR-0053 决定 4；原据：adr/ADR-0037.md；这是只读取证治理活动，未定义可作为值对象的值域；待治理动作目录接入后登记，不将动作名伪作领域 value_object。 |
| 100 | `backend/glossary/proposed/platform-integration.yaml#read_only_verification` · 只读核实 | 转正 `read_only_verification`：核实只读连接 / VerifyReadOnlyConnection；command / acl / platform.integration | ADR-0053 决定 3；原据：adr/ADR-0037.md；phase1a/infra/PREDEPLOY_CHECKLIST.md C-14；无对象的动作片段改为动词＋已定对象；概念 id 不变，原短名仅作可用别名，通用词不禁用。 |
| 101 | `backend/glossary/proposed/platform-integration.yaml#read_only_verification_result` · 只读核实结果 | 转正 `read_only_verification_result`：只读核实结果 / ReadOnlyVerificationResult；state / acl / platform.integration | ADR-0053 决定 3；原据：adr/ADR-0037.md；按已定含义登记技术结构与英文代码名；保留原定义，不新增业务行为或业务边界。 |
| 102 | `backend/glossary/proposed/platform-integration.yaml#read_only_verification_result__passed` · 核实通过 | 转正 `read_only_verification_result__passed`：核实通过 / Passed；state / acl / platform.integration | ADR-0053 决定 3；原据：adr/ADR-0037.md；枚举、标记或分类取值按 state 登记；仅定表示种类，保持原值域和判定条件。 |
| 103 | `backend/glossary/proposed/platform-integration.yaml#read_only_verification_result__violated` · 确认违规 | 转正 `read_only_verification_result__violated`：确认违规 / Violated；state / acl / platform.integration | ADR-0053 决定 3；原据：adr/ADR-0037.md；枚举、标记或分类取值按 state 登记；仅定表示种类，保持原值域和判定条件。 |
| 104 | `backend/glossary/proposed/platform-integration.yaml#read_only_verification_result__undetermined` · 判定不了 | 转正 `read_only_verification_result__undetermined`：判定不了 / Undetermined；state / acl / platform.integration | ADR-0053 决定 3；原据：adr/ADR-0037.md；枚举、标记或分类取值按 state 登记；仅定表示种类，保持原值域和判定条件。 |
| 105 | `backend/glossary/proposed/platform-integration.yaml#overridden_grant` · 被拒绝覆盖的授权 | 转正 `overridden_grant`：被拒绝覆盖的授权 / OverriddenGrant；value_object / acl / platform.integration | ADR-0053 决定 4；原据：adr/ADR-0037.md；SQL Server 权限层级中 DENY 优先于 GRANT（Microsoft 文档 Permissions Hierarchy）；按已定含义登记技术结构与英文代码名；保留原定义，不新增业务行为或业务边界。 |

## 已验证

- `build_terms.py` 两次退出 0，`cmp` 逐字节一致；terms.yaml SHA-256：`858549d14df012e671630aaa7b2836ee312ac6c85da71219a349f1a5ea8d2bb9`。882 条（880 approved、2 deprecated），102 条存量问题，基线外新问题 0。
- `apply_recon.py --self-test` 退出 0：原 72 个负向用例、新增 66 个负向用例、四种结论正向样例、引用内容注入及无关变动、最终输出字节快照均通过。原 `sample_term`、`self_test` 两个函数 AST 与 HEAD 完全一致，未放宽或删除原断言；原哈希负向用例在显式全文件校验模式继续执行。
- `apply_recon.py --verify` 退出 0：原 97/97 来源的内容与整文件审计哈希、设计来源另 3/3 全部通过。针对泛指提交的 intent，具体落点原句变动也会失败，无关落点修改可通过。没有把段落哈希扩大为整份 intent 的锁。
- `apply_nm.py --self-test`：原有 59 个负向用例通过；`build_proposed_index.py --self-test`、`term_rules.py --self-test` 通过；README 使用生成器重建。B 类结论行自身的 confirmed／standard_ref 也逐项校验，覆盖伪造确认、缺失出处及错误字段类型。
- `JAVA_HOME=/opt/homebrew/opt/openjdk@25/libexec/openjdk.jdk/Contents/Home MAVEN_ARGS=-o ./mvnw -o test-compile surefire:test -Dtest=GlossaryTest`：5 项通过，0 failure/error/skip。最初发现的 registration / G37 失败通过维持原提议解决，未加基线、未改断言。
- precheck 联跑 GlossaryTest 5、ErrorCodeGlossaryTest 2、NamingSurfaceTest 11、DocumentTypeCodeTest 3，合计 21 项全部通过（包含上述 5 项，不重复相加）；逐份读取 surefire XML，0 failure/error/skip。
- `git diff --check` 通过；gitleaks 8.30.1 全历史 2936 个提交扫描通过。全历史扫描不覆盖未提交文件，本卡 21 个交付文件另经 gitleaks dir --redact 定向扫描，1.18 MB，无发现。
- 结构复核：仅删除 98 个准确 id；其余 proposed 值与所有顶层字段不变（中4允许的 owner/until 除外）；基线新增键 0；正式词条除明确列出的 7 项外，原 778 条内容不变；未改验收测试、Java、前端、代码基线、依赖或锁文件。

## 未运行与不确定项

- 未执行会触发容器的 Maven test 生命周期：pom.xml 的 process-test-classes 绑定 OpenApiExportTest，会启动 PostgreSQL 并改写卡外 OpenAPI；改用同一 GlossaryTest 的离线 test-compile + surefire:test，保留测试与断言。
- 前端离线缓存缺 filenamify@2.1.0，pnpm install --frozen-lockfile --offline 失败；不联网补包。check:copy / test:scripts 尚不能报告通过。
- 原 C3 与 B 改归 C3 待业务裁定；acceptance_evidence 的英文指称仍待确认；四条技术／改名提议的退出条件见数据，不假定完成。
- 未运行容器、生产或外网集成、业务端到端验收、完整前端 verify、卡外 build_all 生成；未指定独立验收测试，不声称业务验收完成。

## precheck 原始输出

执行默认 `origin/main` 基线，没有用 HEAD 缩小范围。该范围包含 GLO-06 既有前端 allow list 改动，所以前端 verify 也计 FAIL；两个 FAIL 均为同一个离线缺包条件，不能当作 SKIP。未达到 `0 FAIL`，**本卡门禁未完成，不可按已就绪交付**。

命令：

```sh
PATH=/tmp/nam-1017-glo-07-r1/bin:/Users/yeliang/.pyenv/versions/3.12.13/bin:/Users/yeliang/.nvm/versions/node/v24.18.0/bin:/opt/homebrew/bin:/usr/bin:/bin:/usr/sbin:/sbin \
JAVA_HOME=/opt/homebrew/opt/openjdk@25/libexec/openjdk.jdk/Contents/Home \
MAVEN_ARGS=-o COREPACK_ENABLE_NETWORK=0 npm_config_offline=true CI=true scripts/precheck.sh
```

临时 bin/pnpm 仅给原 pnpm 命令强制添加 `--offline`，不修改仓库检查脚本；本次因缺 node_modules，precheck 未调用 pnpm 执行检查。离线安装实际执行 `pnpm --offline install --frozen-lockfile --store-dir ../.pnpm-store`，报 `ERR_PNPM_NO_OFFLINE_TARBALL`，缺 `filenamify@2.1.0`。没有尝试去掉 offline。

```text
precheck: base origin/main (fb070284), HEAD 9800ecae, 42 changed files (38 uncommitted)

SKIP  generated docs (build_all --check)     docs/modernization or backend/glossary/proposed has uncommitted changes: commit first, then run again (请先提交再运行)
PASS  legacy system name                     check_legacy_name: 无违规
SKIP  migrations immutable                   no migration, marker or Flyway setting changed
SKIP  system actors append-only              system-actors.txt and its script did not change
SKIP  acceptance guard                       no acceptance test changed
SKIP  database scripts aligned               no database script or migration changed
PASS  docs read by builds are registered     ok
SKIP  assertion drift (warning only)         no test file changed
FAIL  glossary: frontend check:copy, test:scripts 前端依赖未安装：先在 frontend/ 运行 pnpm install --frozen-lockfile --offline（只用本机缓存、不下载）；缓存缺包而失败时，再问用户是否联网安装
PASS  glossary: backend term tests             size (term round3_pending__size) -> com.forcitis.one.platform.integration.api.ArchivedOb
SKIP  guards: backend guard tests            no guard test or architecture list changed
PASS  release scripts self-test              deploy_gate.py self-test passed
PASS  shellcheck scripts/release             ok
PASS  gitleaks (full history)                2936 commits scanned; no leaks
      note: uncommitted files are not in history; the pre-commit hook scans them on commit
SKIP  backend tests                          not run by default; no module code changed (shared inputs, resources, build or architecture lists)
      run: (cd backend && ./mvnw -q test-compile surefire:test -Dtest='ArchitectureRulesTest,ModularityTest,ModuleDependencyTest,NamingSurfaceTest,GlossaryTest,DdlNamingTest,LegacyShapeTest,TimeSourceGuardTest,TestIsolationGuardTest' -Dsurefire.failIfNoSpecifiedTests=false)
      before delivery run everything: scripts/ci/backend_verify.sh (Testcontainers: ask the user before starting containers)
FAIL  frontend verify                        frontend changed: 前端依赖未安装：先在 frontend/ 运行 pnpm install --frozen-lockfile --offline（只用本机缓存、不下载）；缓存缺包而失败时，再问用户是否联网安装
SKIP  SQL Server integration tests           relevant_change.py: nothing the *MssqlIT tests exercise changed

SUMMARY: 6 PASS, 2 FAIL, 9 SKIP (base origin/main) — not ready
```

本轮本机基准快照建立至本报告更新约 19 分钟（不含开工阅读）。临时验证与完整日志：`/tmp/nam-1017-glo-07-r1/`。工作区保持未提交，未推送；交负责会话与 opus 复审前仍须解决上述前端验证缺口。
