# one-data 速查

只记现行状态，一行一项（SESSION_PLAYBOOK TEAM-07、08）。**2026-10-21 11:58+02:00 清上下文交接，现行状态见文末「2026-10-21 交接」一节，以它为准**；下面 2026-10-17 的暂停交接（one-coordinator：用户额度紧，只保留命名主线；ADR-0058 设计修订、开发卡、DDL 扫描全部暂停）。

| 项 | 值 |
|---|---|
| 状态 | 在岗；独立会话（已从调度解绑）；工作规则：签发记录要先于被签发 PR 合入，文档类产出不单独入队、报 one-coordinator 打包，合并过的分支不再推、开新分支新 PR，每个会话同时打开 PR ≤ 3 |
| 门禁 | `data/DATA_DESIGN_GATE.md` 已生效（用户「生效，阈值按 10%」）；新涉数据的卡先签发、表结构 PR 先合并（只含约束、注释、登记且无业务代码的修复卡：签发 → 验收（红）→ DDL（绿）同一 PR 即可，TA-A 裁定 1）；§4a 分级参照表；附录 C 自测表 |
| 签发记录 | `data/reviews/<卡号>.md`，已签：R-1b P5、TS-UNIQ、TB-01/02、TB-03、PR-1、PR-3、PR-5（identity）、PR-6、PR-7（按 AP-14 更正后重签）、PR-8、PR-8b、TA-A（A1/A2/A3）、SINGLE-OP；PR #400 的 SINGLE-OP 记录待合（先于 #398） |
| 已建表审查 | `data/BASELINE_TABLE_REVIEW.md`（统一分级：阻断 0、高 20、中 91、低 70）、`review-parts/`、`repair-cards/`；保留期与匿名化 `data/RETENTION_PROPOSAL.md`（用户已定按建议）；一次投产更正见 BASELINE_TABLE_REVIEW §9 |
| 错题本 | `quality/defect-bank/one-data.md`（DATA-001…017）；工作守则：「缺失 / 不存在」类结论自己 grep 原文复核后才对外发 |
| 岗位体系重梳（Q-02） | ① 取证完成（`data/positions/EVIDENCE.md`，含补充聚合）；③ 映射候选稿 `data/positions/JOB_MAPPING.md`（57 个在用岗位，候选待人工核）已合；**待**：J-1/J-2 用户答复后出职级对照；职业通道 4 类（技术 / 职能 / 管理 / 营销）与方案 B 三通道对不上、历史工时引用 73 个无人岗位（约 43% 工时行）已告 one-architecture |
| 一次投产 | 生产不再同步 A8，全量历史一次性迁入；sync_* 取消方向、已建表等过渡 ADR 退役，不删不新增；**迁入映射与对账方案（U-4 全量历史明细、U-10 投产前两个已结期间重算比对）由 one-data 负责，等施工方案定稿再向 one-coordinator 要排期** |
| 未完成 | ① 填报义务实现的数据设计：等 one-rules 规则卡（预审前置 P1–P11 已发；请假 = OBJ-68、标准工时 = AGG-108，one-architecture 已答）；② 触发器命名条款提议 `data/TRIGGER_NAMING_PROPOSAL.md`（已写，待 one-coordinator 排改全库小卡与 NAMING_STANDARD 条款、one-glossary 查重）；③ DG-0 注册表字段扩展（business_key、数据类别、DECLARED / OBSERVED、查询路径、分区键、projections、failed_policy 等）与基线 DDL 扫描脚本（#322）由 one-quality；④ E3：迁入单的 `round_number` / `submitted_date` 口径回看（TA-A 决定点 6）；⑤ one-1a-next-5 后续：运维证明员工 ID 须存在小卡（M-4）、DATA-REPAIR-workforce 其余条目 |
| 接 one-architecture | OBJ-105–109 等新增对象，为 METHOD 第 11 步准备（未开始，等一次投产过渡 ADR 后再排） |

## 2026-10-17 暂停时的状态与恢复清单

| 项 | 值 |
|---|---|
| 暂停原因 | one-coordinator 通知：用户额度紧，只保留命名主线，ADR-0058 设计修订、开发卡、DDL 扫描暂停；恢复前不要主动推进下列项 |
| 分支 | `claude/one-data-handoff-1`，已全部推送；本地与远端一致。分支落后 main 约 1000 提交，`sync_with_base_branch` 被拦（需用户在应用里 Help → Troubleshooting → Review Pinned Git Origins 确认 origin），同步前扫描用 main 的导出 |
| 已签发 / 已复核 | F-1 全部（`reviews/F-1.md` 第四轮）；ADR-0034-IMPL PR-1/2/3 的 DDL（`reviews/ADR-0034-IMPL.md` 两次补签，B 组除外）；#476 表结构复核通过（`reviews/ADR-0034-IMPL-PR1.md`，待 CI 与合并，合并后我补 `DATA_SCORECARD.md` 两行） |
| ADR-0058 状态转换 | 已采纳。设计第 2 版 `data/STATE_TRANSITIONS_DESIGN.md` @65c78cfe 已按 opus 复核（`~/.modernize/psa/handoff/reviews/STATE_TRANSITIONS_DESIGN-review-1.md`）改完 B1–B3、S1–S9；**待**：sonnet 复核 → 我签发 → 派开发卡（7 张：kernel、模板与守护公共卡、6 个 schema）。待 one-architecture 定三处：`cause_kind` 命名、S5 DDL 层强制法人、两张无 `version` 列表的转换序号来源。保留期（设计第 7 节）待用户认可前不进表结构 PR。两张例外：`sync_runs`、`outbound_deliveries` |
| 可度量原料扫描 | `scripts/ci/measurable_ddl_scan.py`（M1–M5，只报告，带自测）@51be702e 起；首报 M3 14、M4 7 个候选、M1/M2/M5 为 0（`data/MEASURABLE_DDL_SCAN_2026-10-17.txt`）。**待**：注册表加 `data_category`（五选一）后重扫 M4；写进 NAMING_STANDARD / §14；接 `build_all --check`（须先同步 main）。M3 已按 ADR-0058 表形状改 |
| B31P 取证（ARC-1017-ARC-07） | 已出：`data/evidence/` 下本地盘点与查询包、第四五批查询草稿，汇总稿 `B31P_EVIDENCE_SUMMARY_DRAFT.md` @20566fa2（已交 one-architecture）。one-ops 的 `b31p_r4_result.md`（分支 `claude/one-ops-r56`）含一处个人姓名，已批准改写该分支历史清除；以后核对取证结果只用 `grep -c` / 行号（错题本 DATA-018） |
| 对象 ↔ 表 | `data/TABLE_OBJECT_LIST.md`（已有 + 已提议 5 张）与 `TABLE_OBJECT_LIST_PROPOSED.md`（三组 362 表项）已交 one-architecture；等《业务对象界定标准》经用户认可后它按标准复核，结论会回我 |
| C-6 日历导出缺陷 | `data/CALENDAR_EXPORT_DEFECT.md` @c1f73f06：条件在 SQL:46 与 `c6_relay.py:162` 两处，需 one-ops 修并重导出；回放报告不受影响 |
| 其他待办 | 填报义务实现的数据设计（等 one-rules 规则卡）；触发器命名条款提议 `TRIGGER_NAMING_PROPOSAL.md`；迁入映射与对账方案（等施工方案）；错题本 DATA-001…007、011–013、018 待落实（负责 one-data，2026-10-24 前；暂停期间顺延） |
| 工作规则 | 文档类提交不开 PR，推本会话分支，报分支+提交号；消息一到三行、只在对方要做事时发；Codex 先交（取证汇总、设计初稿），生产取证和签发本人做；任务号 `ARC-1017-DAT-nn`，登记片段在 `tasks/` |

## 2026-10-21 交接（用户清上下文，设计恢复后的状态）

| 项 | 值 |
|---|---|
| 分支 | `claude/one-data-handoff-1` 已全部推送（最新 d00e7625 起）。仍落后 main 约千个提交，`sync_with_base_branch` 被拦（用户在应用 Help → Troubleshooting → Review Pinned Git Origins 确认 origin 后才能同步）。文档由 one-merge 收进合集（只取 docs/）；**`scripts/ci/measurable_ddl_scan.py` 不随文档合集，须另开代码 PR（opus 复审），排在 W2 回填之后** |
| 已签发 / 已复核 | F-1 全部（第四轮）；ADR-0034-IMPL 的 PR-1/2/3 DDL（B 组除外）；#489 列改名；**#493（ADR-0068/0069）四轮**：`personnel_commands`、`separation_holds`、`org_structure_commands`、停薪留职、F-1 两侧列、删三列、来源对照封存、审计对象类别登记表（`reviews/ADR-0068.md`）；**IMP-1021-NX5-01 单据类型登记表**（`reviews/IMP-1021-NX5-01.md`，需补触发器与 §4b 回收，其分支 `claude/nx5-doctype-registry@a5d4b26b` 仅在本地、未推远端，推后核实际 diff）；#476 表结构复核通过，**合并后补 `DATA_SCORECARD.md` 两行** |
| W2 设计 | `data/W2_EMPLOYEE_POSITION_VERSIONING_DESIGN.md` 第 4 版，用户答复已回填，A 线 PR-B 的数据设计依据（coordinator 核对通过）；`DATA-REPAIR-workforce-REJUDGE-2026-10-21.md` 第 3 版。挂起：组织单元 `region_kind`/`is_delivery_unit` 无命令维护、法人归属期间维护不在命令种类、`DISSOLVE` 与 `DISABLED` 映射——均待 one-architecture（#493 第四轮三问）；**`org_units` 根表收窄设计：A4 答复后 30 天内由我出** |
| 迁入数据量 | `data/MIGRATION_VOLUME_ESTIMATE.md` 第 2 版：用户裁定 200 GB+自动扩容、范围取 S2 但 A8 的 02/05 按五块表单类型过滤（A8 迁入约 0.8–1.3 GB）、老系统在制与分摊全量迁、U8C 凭证迁、S3 否；迁入合计约 12–14 GB（目标 12–21 GB）；运行期三年增长 27–74 GB，含迁入 39–95 GB ≤120 判定线，含 30% 空闲 51–124 GB 单列为参考。NAS+WORM 估价在 one-ops。待办：五块过滤口径可再跑一次按模板 ID 过滤的分组查询复核（不急） |
| ADR-0058 状态转换 | 已采纳；设计第 2 版 `STATE_TRANSITIONS_DESIGN.md` 已按 opus 复核改（B1–B3、S1–S9），**待 sonnet 复核后签发**，开发卡暂停。W2 后员工/岗位/组织单元状态进版本表，转换表只剩法人等 |
| 可度量原料 | 规范草稿 `MEASURABLE_RAW_MATERIAL_SPEC.md`、扫描脚本（M1–M5，M3 按 ADR-0058，M4 认 `data_category`）、`table_inventory.csv`/`TABLE_OBJECT_LIST*.md` 已交 one-architecture；总线矩阵暂停（SSOT 重建线），恢复后它找我核时间/币种/单位/状态转换/版本；写进 NAMING_STANDARD/§14、接 `build_all --check` 待恢复且先同步 main |
| B31P 取证 | 汇总稿 `B31P_EVIDENCE_SUMMARY_DRAFT.md` 已交；one-ops 的 `b31p_r4_result.md` 含一个姓名，已批准改写该分支历史清除；核对取证结果只用 `grep -c`/行号（错题本 DATA-018） |
| 待回填 | 用户收件箱里 W2 相关两题已答，已回填；N-03 `CORRECTION` 命令种类 one-architecture 已并入 #493 |
| 其他待办 | 触发器命名条款 `TRIGGER_NAMING_PROPOSAL.md`；填报义务实现的数据设计（等 one-rules）；迁入映射与对账方案；SD-1 带入迁入核对设计（负责人 one-data，触发点迁入核对卡签发）；错题本 DATA-001…007、011–013、018 待落实（2026-10-24 前，已逾期顺延）；`ARC-1017-DAT-01/02` 任务登记已按 check_tasks 格式修 |
| 规则提醒 | 文档类提交不开 PR，推本会话分支、报分支+提交号；代码 PR ≤3 且一单元一个；消息一到三行、只在对方要做事时发（连续发 10 条左右会被限流，到用户发言才解）；Codex 先交（取证汇总、设计初稿），生产取证、签发、守护/钩子/并发/解析类代码自己做；任务号 `<主线码>-<MMDD>-DAT-<nn>`，登记片段在 `tasks/`；会话长时先看 `get_usage` |

### 2026-10-21 交接补充（one-coordinator 新派两项，已完成初稿）

- ② 工作日历 DDL 审已完成：`data/reviews/WORK-CALENDAR-DDL-2026-10-21.md`（结论：需修改；改法为 `work_calendar_versions` / `work_calendar_version_days` 只追加版本表，含四眼、来源、不追溯触发器；待 one-1a-par 出表结构 PR 后复核实际 diff 再签）。
- ① 迁入质量检查要求已完成初稿：`data/MIGRATION_QUALITY_CHECKS.md`（按数据类规则、差异清单含负责人与期限、不通过不入库七条门槛、缺口 Q-1…Q-10）；待 one-architecture/one-coordinator 并入 ADR-0036 与 §14.2 M11；需用户定抽样比例、阈值、期限默认值。

## 2026-10-22 交接（用户清上下文；以本节为准，覆盖上文 10-21 的待办）

**已签（设计层或表结构，均有评论或记录）**：日历 #547（c476dba5e，WITHDRAWN/事务时间/首版）；文件服务 #521（9421b798）与审计允许表 #527（9bb49348）；编号版本来源 #524（f87f85f6）；审计对象类别 #599 先签 68fc3543（条件见下）；B-31 卡 A #597（f1aa1375）；卡 A 草案与 `status_of_aggregate` 复签（B31-CARD-A-DDL 第三轮）。记录在 `data/reviews/`。

**在等别人（我这边不用动，来消息再处理）**：
- #599 @88827c3a：one-1a-next-5 须加 `AuditSubjectKindRegistryIT` 用例（`ck_audit_entries_subject_id` 类别集合 = 登记表 `has_object=false`）与表注释一句，改完我再签。opus M1（生成列+复合外键）已裁不采纳。
- 函数固定 search_path：设计 `data/FUNCTION_SEARCH_PATH_PINNING.md` 已报调度，实现派 one-1a-next-5（基线开放期直接改各基线 `CREATE FUNCTION` 加 `SET search_path = pg_catalog, pg_temp`，27 个函数清单见附录 A；#597 同批）；迁移 diff 出来我复核。
- one-platform-file 单元 7（新会话 `local_87a73fc1…`，按名字发）：`platform_file.preservation_category_versions` 加 WITHDRAWN 与版本头 `version` 列，`state_transitions` 的 `status_of_aggregate` 改成对写并 `coalesce`；表结构先送我签。
- 日历 PR-B/C（四眼流程、使用方记版本）、B-31 其余卡（7.4 用印、7.5 IT 工单、7.6 资产使用、7.7 维修、卡 C 员工活动）：DDL 草案按 `B31_PHYSICAL_MODEL_RULINGS.md` §2–§14 验收，逐卡送我签。
- 迁入阻塞项 B-1a/b/c（人事五家法人归属表、财务项目/合同→法人对照表、人事在职入职日期表，首次全量演练前一周）：用户已定，调度跟踪；老系统无来源（DAT-1021-ARC-01 三步取证）。
- 扫描脚本 #504/#515 已入队或合入；`measurable_ddl_scan.py` M6 认 `decision_ref`/`decision_ref_key`，认 `decision_audit_entry_id` 的补丁与 M1 同时认 `currency`/`currency_code` 的补丁尚未做（小 PR，opus 复审后由调度排）；M7（函数 search_path）随实现。

**本会话新产出的文档（分支 claude/one-data-handoff-1，提交 5aa0ca6d1 后；已报 one-merge 收合集）**：`B31_PHYSICAL_MODEL_RULINGS.md`（§2–§14）、`B31_HISTORY_SEAL_MIGRATION_REQUIREMENTS.md`、`EMPLOYMENT_START_DATE_OPINION.md`、`FUNCTION_SEARCH_PATH_PINNING.md`、`MIGRATION_QUALITY_CHECKS.md`（含第 7 节 B-1）、`STATE_TRANSITIONS_DESIGN.md`（列名更正、子状态机记法、status_of_aggregate 写法）、`reviews/` 各记录、`evidence/` 取证 SQL。

**待办（我自己的）**：①W2 员工版本表 DDL 草案的 `employment_start_date` 一列与索引在签 W2 PR-B 时一起看；②DATA_SCORECARD 的 role_versions/account_role_grants 两行；③errors-bank DATA-001…007、011–013、018 仍待落实；④开放项：MIGRATION 行的迁入专用角色写入通道（编号版本表与日历版本表的 owner 检查）、`org_units` 根表收窄设计（A4 答复后 30 天内）；⑤ADR-0058 补写、ADR-0075 对 `ConfigSetVersion` 登记为历史主体随首个使用方。

**规矩提醒**：合 main 用普通 `git merge`（不被拦）；提交与推送分两次调用；外发被限流写 outbox；取证只走 one-ops，明细与个人信息不进会话；一个后台任务；precheck 只在推送前一次；本地只跑受影响模块测试。

**2026-10-22 下午进展（按 one-coordinator 次序）**：①W2 PR-B 无草案可签（已告知调度，草案到再看 `employment_start_date`）；②DATA_SCORECARD 已补第 35、36 行；③错题本 DATA-001…013、018 已落到可定位处（门禁 §7a、`evidence/name_pattern_scan.py`）；④迁入写入通道设计 `MIGRATION_WRITER_ROLE_DESIGN.md` 已出，opus 首轮复审中（DAT-1022-DAT-01），改后交 one-1a-next-5 / one-1a-par；⑤ADR-0058 补写的数据侧输入 `ADR0058_AMENDMENT_INPUT.md` 已出，交 one-architecture 起草。仍开放：`org_units` 根表收窄设计（A4 答复后 30 天内）、ADR-0075 对 `ConfigSetVersion` 登记。

**2026-10-22 晚交接（以本节为准，覆盖上文下午进展）**：
- 已完成并推送（分支 claude/one-data-handoff-1 @ae6af5e4c，已报 one-merge）：W2 PR-B 草案签「通过」附 C1–C3 与重复工号拒收口径（`reviews/W2-PR-B.md`）；DATA_SCORECARD 第 35、36 行；错题本 DATA-001…013、018 落点（门禁 §7a、`evidence/name_pattern_scan.py`）；迁入写入通道设计 `MIGRATION_WRITER_ROLE_DESIGN.md`（opus、sonnet 两轮已改，按 one-architecture 判定：迁入角色无 UPDATE、日历应用内确认、日明细守护 SECURITY DEFINER、删属主确认检查）；ADR-0058 不补正文（勘误 E-010）；卡 C DDL 通过附 M1–M3（`reviews/B31-CARD-C-DDL.md`）；取证 115 个文件均可留（`reviews/EVIDENCE-ADR0037-SCAN-2026-10-22.md`）。
- 在途/等别人：one-1a-par 的 W2 PR-B 实现 PR（我对 `personnel_commands` 与 ADR-0068 附件逐行比较、核 C1–C3）；#599 的 IT 用例；one-1a-next-5 / one-1a-par 按迁入写入通道设计实现，diff 出来我复核；卡 C 改完后的表结构 PR；收件箱 `2026-10-22-one-data-card-c-retention`（T-D10 保留期，等用户）；one-platform-file 单元 7；B-31 其余卡 DDL 草案。
- 我自己的待办：`org_units` 根表收窄设计（A4 答复后 30 天内）；ADR-0075 对 `ConfigSetVersion` 登记为历史主体（随首个使用方）；DATA-001…013 中非机械项仍是软约束。
- 规矩：提交与推送分两次调用；docs 提交号直接报 one-merge；子代理任务要写任务号 `DAT-1022-DAT-nn` 并登记 tasks/（种类取受控词、用时写「待核」）；本地只在推送前跑一次 precheck。

**2026-10-22 深夜交接（37% 清空；以本节为准，覆盖上文「晚交接」的在途项）**：
- 本段新增已完成：卡 C 第二轮复核通过（`reviews/B31-CARD-C-DDL.md`，T-D10 用户已定 A=10 年归档，intent `2026-10-22-205226-one-data-A`；T-D14 应答只放 ID/编号/状态；T-D15 `is_system_only` 加列 + 紧接 DROP DEFAULT，one-1a-admin 已落 @c520f149c，待表结构 PR 复核实际 diff）；AGG-41 表级改动设计层通过（`reviews/ARC-1022-AFI-01-AGG41.md`，F1–F6，DM-8 仍开，FD-09 前 lines 的 pi 待判）；#599 签「通过」并已在 PR 留言（head a4c562253，等调度批）；函数 search_path 设计稿回写 #622 偏离（§7）；W2 设计 §9 登记 org_structure_command_id 外键触发点；重复工号拒收口径（`reviews/W2-PR-B.md` 末节）。
- 在等（来消息再处理）：one-1a-par 的 W2 PR-B 实现 PR（对 `personnel_commands` 与 ADR-0068 附件逐行比较、核 C1–C3、拒收路径）；#622 实际 diff（基线 34 个函数）；迁入写入通道实现 diff（编号 one-1a-next-5、日历 one-1a-par；日历应用内确认、日明细守护 SECURITY DEFINER、删属主确认检查）；卡 C 表结构 PR；one-platform-file 单元 7；B-31 其余卡 DDL 草案；one-arch-finance 的 AGG-41 DDL 草案。
- 未完成的跨会话事项：W2 HELD 放行题（生效日前放行）我已回 one-architecture 倾向 B（界面拒绝提前放行，到日后放行并补版本，数据零改动；A 要改状态转换/成对守护/剩余超管数判定并起新 ADR），等它与调度裁定；之后若走 A，要复核状态转换与 `separation_holds` 守护的 diff。无待回用户请求。
- 自己的待办：`org_units` 根表收窄设计（A4 答复后 30 天内）；ADR-0075 对 `ConfigSetVersion` 登记为历史主体（随首个使用方）；卡 D 的 DDL 审时核四元组唯一与 `submission_key` 已删的幂等替代。
- 规矩：提交与推送分两次调用；docs 提交号直接报 one-merge；子代理任务号 `DAT-1022-DAT-nn`（已用 01、02）并登记 tasks/；收件箱 inbox 集合在 https://claude.ai/artifact/RVU6nNNSsygF5sVRWLhQ15；清空后他会话经 one-coordinator 转。

