# 自有表清单与对应业务对象（供 one-architecture 做「对象 ↔ 表」双向核对）

- 作者：one-data，2026-10-17。来源：`origin/main` 的 `backend/architecture/data-access-registry.yaml`（51 个条目，其中 `act_*` 是一条框架通配）；已提议的表来自 F-1 卡（`claude/one-1a-par-f1-card`）与 ADR-0034-IMPL 卡及 #476。
- 「对应对象」是我的判断，写法：**确定**（信息图 OBJ 明确对应，或已有设计页写明）、**推定**（按信息图推定）、**不确定**、**无（平台）**（one-architecture 对 F-1 A-1 的裁定：授权类数据归 `platform.identity` 自有，不进信息图）、**无（技术）**（队列、幂等、框架、锁、对照、日志等技术构件）。
- 注册表 role 栏照抄注册表；信息图编号取 `architecture/proposals/ENTERPRISE_INFO_MAP_DRAFT.md`。
- 方向提醒：用户 10-17 要求每个业务对象至少对应一张或一组表。下表只列「表 → 对象」；「对象 → 表」缺口的反向清单见末尾「反向缺口」。

## 1. 已有 DDL（main）

| 表 | bc | 主要用途 | 注册表 role | 对应对象 | 把握 |
|---|---|---|---|---|---|
| cost_timesheet.timesheets | 成本·工时 | 工时单 | BUSINESS_DOCUMENT | OBJ-66 工时单 | 确定（粒度与信息图不一致，见 `review-parts/timesheet_attendance_audit_idem_sched.md` T-5） |
| cost_timesheet.consumed_events | 成本·工时 | 监听方已处理事件，幂等快路径 | IDEMPOTENCY | 无（技术） | 确定 |
| organization_attendance.work_calendar_years | 组织·考勤 | 工作日历年份头 | AGGREGATE_ROOT | OBJ-28 工作日历 | 确定 |
| organization_attendance.work_calendar_days | 组织·考勤 | 工作日历例外日（节假日、调休） | AGGREGATE_CHILD | OBJ-28 工作日历 | 确定 |
| organization_workforce.legal_entities | 组织·人事 | 法人主体 | AGGREGATE_ROOT | OBJ-54 法人主体 | 确定 |
| organization_workforce.legal_entity_versions | 组织·人事 | 法人名称与状态的版本 | DIMENSION_VERSIONED | OBJ-54 法人主体（版本） | 确定 |
| organization_workforce.org_units | 组织·人事 | 组织单元当前快照 | SNAPSHOT | OBJ-55 组织单元 | 确定 |
| organization_workforce.org_unit_versions | 组织·人事 | 组织单元历史版本 | DIMENSION_VERSIONED | OBJ-55 组织单元（版本） | 确定 |
| organization_workforce.employees | 组织·人事 | 员工（人员）当前快照 | SNAPSHOT | OBJ-56 人员 | 确定 |
| organization_workforce.positions | 组织·人事 | 岗位目录 | SNAPSHOT | OBJ-58 岗位 | 确定 |
| organization_workforce.assignments | 组织·人事 | 任职当前快照 | SNAPSHOT | OBJ-57 任职 | 确定 |
| organization_workforce.assignment_versions | 组织·人事 | 任职历史版本 | DIMENSION_VERSIONED | OBJ-57 任职（版本） | 确定 |
| organization_workforce.legal_entity_attributions | 组织·人事 | 法人归属（聚合根） | AGGREGATE_ROOT | 无独立对象；是 OBJ-55 / OBJ-56 的「默认法人」「数据范围法人」关系 | 推定 |
| organization_workforce.org_unit_legal_entities | 组织·人事 | 部门的法人归属期间 | DIMENSION_VERSIONED | 同上（OBJ-55 的关系行） | 推定 |
| organization_workforce.employee_legal_entities | 组织·人事 | 员工的法人归属期间 | DIMENSION_VERSIONED | 同上（OBJ-56 的关系行） | 推定 |
| organization_workforce.legal_entity_attribution_corrections | 组织·人事 | 归属期间的更正记录（DATA-RULE-07、RULE-416） | LOG_APPEND_ONLY | 同上（关系的更正日志） | 推定 |
| organization_workforce.sync_runs | 组织·人事 | 老系统/A8 组织同步的运行记录 | AGGREGATE_ROOT | 无（技术，同步用例）；一次投产口径（ADR-0054/0055）下是否保留待定 | 确定 |
| organization_workforce.sync_diffs | 组织·人事 | 同步差异与问题明细 | AGGREGATE_CHILD | 无（技术，同上） | 确定 |
| organization_workforce.sync_run_held_separations | 组织·人事 | 被拦截的离职名单 | AGGREGATE_CHILD | 无（技术，同上） | 确定 |
| platform_approval.approval_requests | 平台·审批 | 审批请求 | AGGREGATE_ROOT | 无（平台）；信息图没有审批对象 | 不确定：是否要补一个通用「审批」对象，请 one-architecture 判 |
| platform_approval.approval_tasks | 平台·审批 | 审批任务 | AGGREGATE_CHILD | 同上 | 不确定 |
| platform_approval.approval_task_records | 平台·审批 | 办理记录 | AGGREGATE_CHILD | 同上 | 不确定 |
| platform_approval.start_rounds | 平台·审批 | 发起轮次 | AGGREGATE_CHILD | 同上 | 不确定 |
| platform_approval.process_archive_skips | 平台·审批 | 归档跳过登记 | LOG_APPEND_ONLY | 无（技术） | 确定 |
| platform_approval.act_*（引擎表） | 平台·审批 | 流程引擎自有表 | FRAMEWORK | 无（技术，框架） | 确定 |
| platform_audit.audit_entries | 平台·审计 | 审计条目（按月分区） | LOG_APPEND_ONLY | 无（平台） | 确定 |
| platform_audit.audit_archive_batches | 平台·审计 | 审计归档批次 | LOG_APPEND_ONLY | 无（技术） | 确定 |
| platform_idempotency.idempotent_responses | 平台·幂等 | 接口幂等响应缓存 | IDEMPOTENCY | 无（技术） | 确定 |
| platform_identity.user_accounts | 平台·身份 | 账号 | AGGREGATE_ROOT | 无（平台）；与 OBJ-56 人员 1:0..1 | 确定（A-1 裁定） |
| platform_identity.roles | 平台·身份 | 角色身份（编码、类别） | AGGREGATE_ROOT | 无（平台） | 确定 |
| platform_identity.role_permissions | 平台·身份 | 角色权限集合（将被 `role_versions` 取代退役） | AGGREGATE_CHILD | 无（平台，过渡） | 确定 |
| platform_identity.account_roles | 平台·身份 | 账号持有角色（将被 `account_role_grants` 取代退役） | AGGREGATE_CHILD | 无（平台，过渡） | 确定 |
| platform_identity.authorization_requests | 平台·身份 | 授权申请（双人确认） | AGGREGATE_ROOT | 无（平台） | 确定 |
| platform_identity.emergency_admins | 平台·身份 | 应急管理员 | AGGREGATE_ROOT | 无（平台） | 确定 |
| platform_identity.navigation_items | 平台·身份 | 导航菜单配置 | CONFIG | 无（平台配置） | 确定 |
| platform_identity.ticket_issuers | 平台·身份 | 单点登录票据签发方配置 | CONFIG | 无（平台配置） | 确定 |
| platform_identity.login_states、sso_ticket_uses、browser_identifiers | 平台·身份 | 登录状态、票据使用记录、浏览器标识 | ONE_TIME | 无（技术） | 确定 |
| platform_identity.sessions、sessions_attributes | 平台·身份 | 会话 | FRAMEWORK | 无（技术，框架） | 确定 |
| platform_integration.outbound_deliveries | 平台·集成 | 对外投递（企业微信通知等） | QUEUE | 无（技术）；服务通知用例 | 确定 |
| platform_integration.source_references | 平台·集成 | 外部/老系统标识与自有 ID 的来源对照（ADR-0033） | AGGREGATE_ROOT | 无（平台，对照） | 确定 |
| platform_integration.event_publication、event_publication_archive | 平台·集成 | 模块间事件发布登记 | FRAMEWORK | 无（技术，框架） | 确定 |
| platform_numbering.numbering_rules | 平台·编号 | 单据编号规则 | CONFIG_VERSIONED | 无（平台配置）；服务所有单据对象 | 确定 |
| platform_numbering.numbering_rule_versions | 平台·编号 | 编号规则版本 | AGGREGATE_CHILD | 无（平台配置） | 确定 |
| platform_numbering.numbering_counters | 平台·编号 | 编号计数器 | COUNTER | 无（技术） | 确定 |
| platform_numbering.legacy_numbers | 平台·编号 | 老单据编号对照（迁入检索） | LINEAGE | 无（迁入对照，ADR-0033） | 确定 |
| platform_scheduling.scheduler_locks | 平台·调度 | 定时作业互斥锁 | FRAMEWORK | 无（技术，框架） | 确定 |

## 2. 已提议的表（尚未合入 main）

| 表 | bc | 主要用途 | 出处与状态 | 对应对象 | 把握 |
|---|---|---|---|---|---|
| platform_identity.role_versions | 平台·身份 | 角色定义的每个版本（名称、状态、范围类型、权限集合） | ADR-0034-IMPL PR-1，#476，我已复核数据项 | 无（平台） | 确定 |
| platform_identity.account_role_grants | 平台·身份 | 账号持有某角色的一段时间 | 同上 | 无（平台） | 确定 |
| platform_identity.role_version_custom_org_units | 平台·身份 | 角色版本的自定义部门明细（含下级/仅本部门） | F-1 卡第 7.1 节，已签发；引用 OBJ-55 组织单元（跨 schema 只存 ID） | 无（平台）；引用 OBJ-55 | 确定 |
| platform_identity.authorization_request_custom_org_units | 平台·身份 | 授权申请中申请的自定义部门 | F-1 卡第 7.1 节，已签发 | 无（平台） | 确定 |
| platform_identity.permission_adjustments | 平台·身份 | 待调整权限条目（敏感角色复核、范围复核） | F-1 卡第 7.1 节，已签发；保留期用户已定（在线 3 年归档到满 10 年） | 无（平台，待办） | 确定 |

## 3. 反向缺口（信息图有对象、目前没有表，或表不能一一对应）

下列是我从现有表与信息图对照读出的，**不是穷尽**，且以信息图当前状态为准；未建的业务模块（销售、项目、财务等）不在此列，那是各阶段的事。

- OBJ-28 工作日历：有表。注意 `ADR-0033` 提到工作日历路径将带适用范围（法人/默认日历），表尚无适用范围列（见 `data/reviews/` 里 calendar_scope 的收件箱结论）。
- OBJ-68 请假、OBJ-70 考勤记录、OBJ-72 加班、OBJ-60 招聘需求、OBJ-62 社保与公积金参保、OBJ-64 注册执业证书、OBJ-65 资质证书：目前无表，属后续阶段（组织/人事业务）。
- OBJ-66 工时单：表粒度与对象粒度不一致（见 T-5），请 one-architecture 与我一起判是改表还是改对象口径。
- 法人归属（`legal_entity_attributions` 等四张）：没有独立信息图对象，我推定为 OBJ-55 / OBJ-56 的关系；若要独立成对象请回我，数据设计随之调整。
- 平台类（审批、审计、账号、角色、授权申请、编号、集成）：信息图无对象；one-architecture 对授权数据已裁定归 `platform.identity` 自有。审批请求是否补通用对象请裁（上表「不确定」栏）。
