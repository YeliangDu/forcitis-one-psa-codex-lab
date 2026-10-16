# 人员组流程样板：入职到账号开通（PRC-83 + PRC-137 + PRC-84）

- 用途：用户看设计是否符合想法（intent 2026-10-16-153009）；意见回来前不批量铺开其余流程。
- 范围：一个人从「录用决定已作出」到「能用企业微信登录 FORCITIS one、已参保、有薪酬档案」。三个流程首尾相接：PRC-83 办理入职与任职（B-13，含劳动合同单级批准）→ PRC-137 入职开通企业微信并绑定外部身份（新号）→ PRC-84 参保增员与建立薪酬档案（B-15）。
- 格式：`../CONVENTIONS.md`。本页的 yaml 是样板定稿；`processes.people.yaml` 中这三个流程已与本页同步（含 `where` / `evidence`），`data-model.people.yaml` 的相关表在用户意见回来后同步。
- 〔缺〕= 聚合稿没有的命令或事件，提议新增（见第 4 节），不假装已存在。

## 0. BPMN 图

描述级 BPMN 2.0.2 协作图（含 BPMNDI，按 `../BPMN_STYLE.md`）：`bpmn/PRC-83.bpmn`（主流程）、`bpmn/PRC-137.bpmn`、`bpmn/PRC-84.bpmn`；三者以信号事件「入职已登记」「Employed」衔接，跨 BC 的去向写在文本注释里。

## 1. 关键设计点与依据（一行一条）

| # | 设计点 | 依据 |
|---|---|---|
| S1 | FORCITIS one 是人事记录系统：入职、任职、劳动合同由人力在 FORCITIS one 登记，不再从 A8 同步 | 用户 U-1 自成体系、U-7 / C-7 A8 停用（intent 2026-10-16-133348、144943）；one-architecture 判为已定事项的落地改写 |
| S2 | 入职日、任职生效日是人事业务生效日，人力登记时填写；可提前登记、到日生效，补登落回真实日期 | 用户 U-12（intent 2026-10-16-134434） |
| S3 | 「入职已登记」与「入职生效（Employed）」分成两个事件：登记时发前者，入职日到时发后者 | 由 S2 推出：提前登记时账号要能提前准备，参保与计薪要从真实入职日起算；聚合稿只有 Employed 一个事件，发出时点未定（PG-01） |
| S4 | 账号开通由「入职已登记」触发，账号可提前建好，但登录只从入职日起放行 | 用户 Q-03「通过入职后开通企业微信账号，解决」，不另设首日兜底（intent 2026-10-16-134134）；取证：入职到首次企业微信登录中位数 0 天、P90 1 天（`IDENTITY_LIFECYCLE.md`） |
| S5 | 开通账号的编排在 `platform.identity`：订阅 B-13 事件，经 `platform.integration` 调企业微信，绑定记在身份侧；B-13 不存企业微信 userid | 现有依赖边 `platform.identity → organization.workforce, platform.integration`，`platform.integration` 不依赖任何模块（`backend/architecture/module-dependencies.txt` 第 15、17 行），按此不新增边；D16 外部 ID 只作来源对照 |
| S6 | 企业微信 userid 由 FORCITIS one 定（取工号），开通后按 userid 精确绑定，不按姓名或手机号模糊匹配；匹配不到失败告警 | CLAUDE.md 第 6 条（缺失输入失败并告警）；取证：老系统企业微信入口只按 userid 找人、建号晚一轮同步才写入 userid（`IDENTITY_LIFECYCLE.md`） |
| S7 | 不带入老系统「手机号 / 工号 + 统一默认口令」兜底 | `IDENTITY_LIFECYCLE.md` 含义第 2 条；SECURITY_INVARIANTS |
| S8 | 入职日已到而账号仍未绑定：告警人力与信息部门，不静默 | CLAUDE.md 第 6 条；提前量与时限待用户（第 5 节 T-2） |
| S9 | 任职 = 组织单元 + 职位（职位目录）+ 职级 + 汇报线 + 主 / 兼，按生效区间版本化；主任职区间不重叠 | Q-08 只要职位目录（intent 134134）；`JOB_CATALOG_DRAFT.md` P-0 职位与职级两维；INV-62（用户 intent 072440 已定）；§14.1 D6、D17 |
| S10 | 任职生效后 `platform.identity` 订阅「任职已变更」重算默认角色与数据范围 | 现有边 `platform.identity → organization.workforce`；按岗位授权的具体规则随 ADR-0029 重起草，本样板只定触发点 |
| S11 | 劳动合同批准是 B-13 聚合内单级批准命令，不经 `platform.approval` | intent 2026-10-16-114200 F-3（已建边 `platform.approval → organization.workforce`，B-13 实现审批钩子会成环） |
| S12 | 试用期超法定上限拒绝；用工满一个月未签书面合同提醒 | INV-14（劳动合同法第 19 条）；P-05（劳动合同法第 10、82 条） |
| S13 | 员工法人三口径（劳动合同用人单位、数据范围法人、个税扣缴义务人）分别记录，不一致只预警不拒绝；预警是 B-15 的事件策略（订阅劳动合同已签订、法人归属已变更、薪酬档案已变更，读 B-13 另两口径），不在 PRC-83 内——one-architecture S-20 | 用户 Q-09（intent 134134）；聚合稿 Q27 涉及 AGG-62、67、73、93 |
| S14 | 参保增员与薪酬档案在 B-15，订阅 Employed（真实入职日），不订阅「入职已登记」 | 聚合稿 AGG-62 订阅方 B-15、P-32（入职 30 日内办社保与公积金登记，社会保险法第 58 条）；由 S3 推出 |
| S15 | 薪酬档案属敏感个人信息，按生效日版本化；入职时只建档，不迁入时另走迁入通道 | ADR-0043、ADR-0041（D13）；D17 维度型；迁入按 ADR-0036（M7：迁入不发事件、不触发本流程） |
| S16 | 表：自有主键 UUIDv7，跨 BC 只按 ID 引用，状态为本系统枚举 | §14.1 D16；CONVENTIONS §3 |
| S17 | 历史全量迁入：人员、任职版本、劳动合同、参保、薪酬档案按人事生效日回放；迁入不触发本流程 | 用户 U-3、U-4（intent 133348）；ADR-0036 M7 |

## 2. 流程骨架（CONVENTIONS §2）

```yaml
- id: PRC-83
  name: 办理入职与任职
  trigger:
    type: event
    events: [{event: 录用决定已作出, aggregate: AGG-62}]
    desc: 候选人确认入职日后，人力办理入职（同 BC）
  stages: [VS-07.3]
  owner_bc: B-13
  human_orchestration: false
  roles:
    - {id: SH-E6, as: performer}      # 人力
    - {id: SH-E6, as: approver}       # 人力负责人，劳动合同单级批准
    - {id: SH-H1, as: external}       # 候选人：确认入职日、签合同
    - {id: SH-E1, as: informed}       # 用人经理
  steps:
    - actor: SH-E6
      where: in_system
      command: 登记入职
      aggregate: AGG-62
      emits: [〔缺〕入职已登记]        # S3；PG-01
      desc: 填入职日（人事业务生效日，S2）；用工类型；工号由本系统发号
      cross_bc:
        - {mode: event, direction: out, bc: platform.identity, name: 〔缺〕入职已登记}   # 触发 PRC-137（S4、S5）
      decision:
        - {when: 入职日不晚于今天, goto: 2}
        - {when: otherwise, goto: 2}      # 提前登记：第 2 步在入职日到时由系统执行
    - actor: system
      where: automatic
      on_time: 入职日到达（当天开始时）
      command: 〔缺〕入职生效
      aggregate: AGG-62
      emits: [Employed]
      desc: 人员状态由「待入职」转「在职」；补登时立即执行，生效日仍为登记的入职日（S2）
      cross_bc:
        - {mode: event, direction: out, bc: B-15, name: Employed}               # 触发 PRC-84
        - {mode: event, direction: out, bc: platform.identity, name: Employed}  # 放行登录（S4）
    - actor: SH-E6
      where: in_system
      command: 建立任职
      aggregate: AGG-63
      emits: [任职已变更]
      desc: 组织单元、职位、职级、汇报线、主 / 兼；生效日默认等于入职日（S9）
      decision:
        - {when: 与该人员已有主任职区间重叠, goto: 3}    # INV-62 拒绝，改正后重提
        - {when: otherwise, goto: 4}
      cross_bc:
        - {mode: event, direction: out, bc: platform.identity, name: 任职已变更}   # 默认角色与数据范围（S10）
    - actor: SH-E6
      where: in_system
      command: 提交劳动合同
      aggregate: AGG-67
      desc: 用人单位（法人口径之一，S13）、期限、试用期
      decision:
        - {when: 试用期超过法定上限, goto: 4}            # INV-14 拒绝
        - {when: otherwise, goto: 5}
    - actor: SH-E6
      where: in_system
      command: 批准劳动合同
      aggregate: AGG-67
      approval:
        doc_type: 劳动合同批准
        engine: aggregate                                # B-13 聚合内单级批准，不经 platform.approval（S11）
        approver: SH-E6
        multi_level: false
        on_reject: 4                                     # 退回人力改合同（驳回命令〔缺〕，PG-09）
    - actor: SH-E6
      where: offline
      evidence: [已签署劳动合同扫描件, 签署日]
      command: 签订劳动合同
      aggregate: AGG-67
      emits: [劳动合同已签订]
      desc: 候选人（SH-H1）线下或电子签署，人力登记签署日
      decision:
        - {when: 用工满一个月仍未签订, goto: 6}          # P-05 提醒，不拒绝（S12）
        - {when: otherwise, goto: end}    # 三口径预警移至 B-15 事件策略（S-20）

- id: PRC-137
  name: 入职开通企业微信账号并绑定外部身份
  trigger:
    type: event
    events: [{event: 〔缺〕入职已登记, aggregate: AGG-62}]
  stages: [VS-07.3]
  owner_bc: platform.identity   # J-1 / S-14 one-architecture 同意
  human_orchestration: false
  roles:
    - {id: SH-E6, as: performer}      # 信息（IT）
    - {id: SH-E6, as: informed}       # 人力，逾期告警
    - {id: SH-E3, as: informed}       # 新员工，收到开通通知
  steps:
    - actor: platform
      where: automatic
      bc: platform.identity
      command: 〔缺〕建立待开通登录身份
      desc: 以人员 ID 为键；拟定企业微信 userid = 工号（S6）；登录放行起点 = 入职日（S4）
      cross_bc:
        - {mode: event, direction: in, bc: B-13, name: 〔缺〕入职已登记}
    - actor: system                   # 用户已定 T-1：本系统在登记入职后自动开通（intent 2026-10-16-162821）
      where: external:企业微信
      evidence: [企业微信返回的成员 userid, 开通时间]
      bc: platform.identity
      command: 〔缺〕开通企业微信成员
      desc: 经 platform.integration 的企业微信适配器自动开通成员；入职日前 3 个工作日开通（提前登记时排到该日，晚于该日登记的立即开通）（用户已定 T-1、T-2）
      cross_bc:
        - {mode: api, direction: out, bc: platform.integration, name: 〔缺〕开通企业微信成员}
      decision:
        - {when: 企业微信返回失败或 userid 已被占用, goto: 4}
        - {when: otherwise, goto: 3}
    - actor: platform
      where: automatic
      bc: platform.identity
      command: 〔缺〕绑定外部身份
      desc: 按 userid 精确绑定到人员（S6）；入职日前绑定的身份处于「待生效」，订阅 Employed 后放行登录
      cross_bc:
        - {mode: event, direction: in, bc: B-13, name: Employed}
      decision:
        - {when: otherwise, goto: end}
    - actor: system
      where: automatic
      on_time: 开通失败，或入职日已到仍未绑定
      desc: 告警信息部门与人力，不静默（S8）；开通失败由信息部门 1 个工作日内处理（用户已定 T-2）
      decision:
        - {when: 已修正并重试, goto: 2}
        - {when: otherwise, goto: end}

- id: PRC-84
  name: 入职后参保增员与建立薪酬档案
  trigger:
    type: event
    events: [{event: Employed, aggregate: AGG-62}]
  stages: [VS-07.3]
  owner_bc: B-15
  human_orchestration: false
  roles:
    - {id: SH-E6, as: performer}      # 人力（薪酬与社保）
  steps:
    - actor: SH-E6
      where: offline
      evidence: [社保增员回执, 公积金增员回执, 办理日]
      command: 参保增员
      aggregate: AGG-74
      emits: [参保已增员]
      desc: 入职 30 日内办理，逾期提醒（P-32）
      cross_bc:
        - {mode: event, direction: in, bc: B-13, name: Employed}
      decision:
        - {when: 用工类型为退休返聘或实习, goto: 2}   # 不参保（用户已定 T-4）
        - {when: otherwise, goto: 2}
    - actor: SH-E6
      where: in_system
      command: 建立薪酬档案
      aggregate: AGG-93
      emits: [薪酬档案已变更]
      desc: 薪酬属性版本生效日 = 入职日（S2、S15）
      cross_bc:
        - {mode: api, direction: out, bc: B-13, name: 读取人员与任职}
      decision:
        - {when: otherwise, goto: end}
```

## 2a. 每步在哪里完成（`where`，用户问「哪些节点在本系统、怎么考虑的」）

判据：业务决定与登记在本系统（in_system）；规则能定的由本系统自动做（automatic）；外部系统有接口、本系统能发起并拿到回执的走集成（external:<系统名>）；只能在系统外完成、没有可用接口的线下办（offline），本系统只登记结果与证据。

| 流程 | 步 | 在哪 | 本系统留下的证据 | 为什么这样放 |
|---|---|---|---|---|
| PRC-83 | 1 登记入职 | in_system | — | 人事记录系统在本系统（S1） |
| PRC-83 | 2 入职生效 | automatic | — | 到日生效是规则，不需人点（S2、S3） |
| PRC-83 | 3 建立任职 | in_system | — | 组织、职位、职级由人力定（S9） |
| PRC-83 | 4 提交劳动合同 | in_system | — | 合同要素在本系统起草 |
| PRC-83 | 5 批准劳动合同 | in_system | — | 聚合内单级批准（S11） |
| PRC-83 | 6 签订劳动合同 | offline | 已签署合同扫描件、签署日 | 目前纸面签署；若以后接电子签可改 external:电子签（待用户） |
| PRC-145（B-15 事件策略） | 法人三口径预警 | automatic | — | 比对是规则（S13）；放 B-15（S-20） |
| PRC-137 | 1 建立待开通登录身份 | automatic | — | 由入职登记事件触发（S4、S5） |
| PRC-137 | 2 开通企业微信成员 | external:企业微信 | 企业微信返回的 userid、开通时间 | 企业微信保留为外部服务（U-1）；自动调接口还是信息部门在企业微信后台手工开见 T-1 |
| PRC-137 | 3 绑定外部身份 | automatic | — | 按 userid 精确绑定（S6） |
| PRC-137 | 4 逾期告警 | automatic | — | 不静默（S8） |
| PRC-84 | 1 参保增员 | offline | 社保增员回执、公积金增员回执、办理日 | 人社与公积金网办没有面向本系统的接口，人力在官方网办平台办理后回填；有接口再改 external（待核） |
| PRC-84 | 2 建立薪酬档案 | in_system | — | 薪酬自算，档案是计算依据（intent 2026-10-16-152603） |

## 3. 涉及的表（CONVENTIONS §3）

```yaml
- aggregate: AGG-62            # 人员
  bc: B-13
  tables:
    - name: employees
      role: root
      key: uuid_v7
      versioned: none
      history_migration: full
      sensitivity: SENSITIVE
      column_families: [工号, 姓名, 联系方式, 当前用工状态, 乐观锁, 来源对照]
      reason: "根只留当前值（D17 细则 2 第四类，个人信息最小化）；随时间变化的用工属性进 employment_versions；不存企业微信 userid（S5）"
    - name: employment_versions
      role: version
      key: uuid_v7
      references: [{to: AGG-62, by: employee_id}]
      versioned: effective_dated
      history_migration: full
      sensitivity: SENSITIVE
      column_families: [生效区间, 用工类型, 用工状态（待入职 → 在职 → 待离职 → 已离职）, 入职日 / 转正日 / 离职日]
      reason: "生效依据：人事业务生效日（U-12）；历史按老系统员工档案与异动回放"
    - name: hiring_decisions
      role: child
      key: uuid_v7
      references: [{to: AGG-62, by: employee_id}, {to: AGG-65, by: recruitment_requisition_id}, {to: AGG-64, by: job_id}]
      versioned: none
      history_migration: full
      sensitivity: SENSITIVE
      column_families: [录用结论, 拟入职日, 拟任职位与职级, 决定人与时间]
      reason: "用户已定 T-5：候选人面试与录用记录迁入，加个人信息保护（分级与访问按 ADR-0043，含未入职候选人，访问限招聘相关角色，保留期待 one-data 按 D12 登记）"

- aggregate: AGG-63            # 任职
  bc: B-13
  tables:
    - name: assignments
      role: root
      key: uuid_v7
      references: [{to: AGG-62, by: employee_id}]
      versioned: none
      history_migration: full
      sensitivity: INTERNAL
      column_families: [所属人员, 主任职或兼任, 乐观锁]
    - name: assignment_versions
      role: version
      key: uuid_v7
      references:
        - {to: AGG-63, by: assignment_id}
        - {to: AGG-61, by: org_unit_id}
        - {to: AGG-64, by: job_id}
        - {to: AGG-64, by: job_grade_id}
        - {to: AGG-62, by: reports_to_employee_id}
      versioned: effective_dated
      history_migration: full
      sensitivity: INTERNAL
      column_families: [生效区间, 组织单元, 职位, 职级, 汇报线]
      reason: "INV-62 主任职区间排他约束；生效依据 U-12"

- aggregate: AGG-67            # 劳动合同
  bc: B-13
  tables:
    - name: labor_contracts
      role: root
      key: uuid_v7
      references: [{to: AGG-62, by: employee_id}, {to: AGG-60, by: employing_legal_entity_id}]
      versioned: none
      history_migration: full
      sensitivity: SENSITIVE
      column_families: [合同编号, 用人单位, 类型与期限, 试用期, 状态（草案 → 审批中 → 已批准 → 已签署生效 → 已终止）, 批准人与时间, 签署日, 乐观锁]
      reason: "续签另立新合同，状态由审计还原；老系统是否有劳动合同记录待取证（不据此推定）"

- aggregate: AGG-117           # 法人归属（已建，补号）
  bc: B-13
  tables:
    - name: employee_legal_entities
      role: version
      key: uuid_v7
      references: [{to: AGG-62, by: employee_id}, {to: AGG-60, by: legal_entity_id}]
      versioned: effective_dated
      history_migration: full
      sensitivity: INTERNAL
      column_families: [生效区间, 数据范围法人]
      reason: "三口径之一（数据范围法人，S13）；已建表保留"

- aggregate: AGG-74            # 社保与公积金参保
  bc: B-15
  tables:
    - name: insurance_enrollments
      role: root
      key: uuid_v7
      references: [{to: AGG-62, by: employee_id, cross_bc: true}, {to: AGG-60, by: contributing_legal_entity_id, cross_bc: true}]
      versioned: none
      history_migration: full
      sensitivity: SENSITIVE
      column_families: [参保人员, 缴纳主体, 状态, 乐观锁]
      reason: "老系统来源待取证"
    - name: insurance_enrollment_versions
      role: version
      key: uuid_v7
      references: [{to: AGG-74, by: insurance_enrollment_id}]
      versioned: effective_dated
      history_migration: full
      sensitivity: SENSITIVE
      column_families: [生效区间, 险种, 缴费基数, 增员 / 减员]
      reason: "缴费基数属个人财产信息，按 ADR-0043 列级访问"

- aggregate: AGG-93            # 人员薪酬档案
  bc: B-15
  tables:
    - name: payroll_profiles
      role: root
      key: uuid_v7
      references: [{to: AGG-62, by: employee_id, cross_bc: true}]
      versioned: none
      history_migration: full
      sensitivity: SENSITIVE
      column_families: [所属人员, 乐观锁]
    - name: payroll_profile_versions
      role: version
      key: uuid_v7
      references: [{to: AGG-93, by: payroll_profile_id}, {to: AGG-60, by: withholding_legal_entity_id, cross_bc: true}]
      versioned: effective_dated
      history_migration: full
      sensitivity: SENSITIVE
      column_families: [生效区间, 薪资结构与标准, 个税扣缴义务人, 发薪账户]
      reason: "敏感个人信息与金融账户信息（个人信息保护法第 28 条，ADR-0043 最高访问级别）；扣缴义务人是三口径之一（S13）；迁入按 ADR-0036"
```

外部身份绑定表归 `platform.identity`（ADR-0039 第 3 条 C1 设计），不在本组表内；本样板只要求它以人员 ID 为键、记录企业微信 userid 与「登录放行起点」。

## 4. 本样板提出的新命令与事件（交 one-architecture 补聚合稿，用语交 one-glossary）

| 名称 | 所在 | 理由 |
|---|---|---|
| 入职已登记（事件） | AGG-62 | S3；登记时发出 |
| 入职生效（命令，系统定时） | AGG-62 | S3；入职日到时发 Employed |
| 建立待开通登录身份、开通企业微信成员、绑定外部身份 | platform.identity | S4、S5、S6；C1 设计的一部分 |
| 驳回劳动合同 | AGG-67 | 批准有退回分支却无命令（PG-09） |
| 员工法人三口径不一致预警 | PRC-145，B-15 事件策略 | S13；S-20 定放 B-15（扣缴义务人在 AGG-93，B-15 → B-13 已有边） |

## 5. 用户答复（intent 2026-10-16-162820、162821，方法与样板已批准）

- T-1 本系统在登记入职后自动开通企业微信成员。
- T-2 入职日前 3 个工作日开通；开通失败由信息部门 1 个工作日内处理。
- T-3 同意提前开通、入职日才放行登录。
- T-4 退休返聘与实习不参保。
- T-5 候选人面试与录用记录迁入，须加个人信息保护措施，分级与访问按 ADR-0043。

## 6. 接缝（交 one-architecture）

- J-1 PRC-137、PRC-138 owner 改 `platform.identity`（同 PRC-129），沿用现有边——one-architecture 已同意（S-14）。
- J-2 `platform.identity` 订阅 B-13 的「入职已登记」「Employed」「任职已变更」沿现有边 `platform.identity → organization.workforce`，方向一致，无需新边；聚合稿 AGG-62、63 的订阅方列需补 platform.identity。
- J-3 PRC-84 不变；B-15 订阅 Employed 已在聚合稿登记。
