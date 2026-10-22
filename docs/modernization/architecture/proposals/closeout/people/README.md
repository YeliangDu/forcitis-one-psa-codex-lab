# 架构收尾 · 人员组（one-arch-people）

范围：VS-07 任用员工、VS-09 达成经营目标、VS-11 沉淀复用知识、VS-13 获得报酬；BC：B-13 `organization.workforce`、B-14 `organization.attendance`、B-15 `organization.payroll`、B-18 `operations.planning`、B-27 `organization.leave`、B-30 `km.knowledge`（25 个聚合）。依据 `ARCH_CLOSEOUT_PLAN.md` §1 ①②、§4。

## 文件

字段与文件名以 `../CONVENTIONS.md`、`../README.md` 为准（one-architecture 2026-10-16，PR #384）。

| 文件 | 内容 |
|---|---|
| `processes.people.yaml` | 本组流程的 ⑥b 骨架片段（交 one-architecture 合并为 `processes.v2.yaml`） |
| `data-model.people.yaml` | 本组聚合的表级片段（合并为 `data-model.v1.yaml`） |
| `NOTES.md` | 接缝问题、结构问题、新用语、格式问题、待用户定 |
| `DATA_MODEL.md` | 表级说明稿（依据、已建 / 改形 / 新建、迁入来源） |
| `parts/` | 铺开分片底稿（各分片的 yaml、说明与 notes），已合并进上面三个文件 |
| `bpmn/` | 样板描述级 BPMN（PRC-83、84、137）与样板生成脚本 |
| `SAMPLE.md` | 流程样板：入职到账号开通（PRC-83 + PRC-137 + PRC-84），用户先看（intent 2026-10-16-153009） |
| `REVIEW.md` | opus 首轮评审与 sonnet 复核记录 |

## 本组流程（19 个业务流程，人工编排 8 个 + 6 个事件策略 + 3 个单聚合维护 + 1 个读模型）

- 业务流程：PRC-80–85、88、89、107、115–118、120–122、124、125、128；VS-13.5 的付款 PRC-75 归财务组（B-10），本组只对接缝。
- 事件策略：PRC-113、126、127、129、130、134（PRC-131、132 归财务组）。
- 单聚合维护：PRC-79、119、123；读模型：PRC-90。

## 已定口径（照用）

- 员工与岗位版本以人事业务生效日为准（U-12，intent 2026-10-16-134434）：可提前登记、到日生效，补登落回真实日期；修改时间由审计承担。
- 新员工以入职后开通的企业微信账号登录（Q-03）；B-13 升级为人事记录系统，入转调离驱动账号开通与吊销（安全关键）。
- 岗位只要职位目录、不管编制（intent 2026-10-16-134134 Q-08）；岗位体系重梳在做（`JOB_CATALOG_DRAFT.md`、one-data `JOB_MAPPING.md`）。
- 薪酬等敏感个人信息迁入按 ADR-0036；分级按 ADR-0041、ADR-0043。
- 分管领导（ROLE-55）的分管范围是 D15 配置项，由人力维护、总经理批准（LEB-06，提交 LEB-20261022-082103-ioy，intent 2026-10-22-082120，按本组推荐 A；roles.yaml ROLE-55 config、vacancy）：每版记生效日、批准人与依据，按业务日期取当时生效的唯一版本。缺位处理：按分管领导解析审批人或升级对象时，若未配置、该人已离职或分管范围有空缺，不静默跳过、不自动通过，改指总经理（同 AR-12「角色缺位改指上级」），同时提醒人力补配置，并在单据上记「因分管缺位改指」；配置版本缺失或区间重叠时，发起即拒绝并告警。表级落点见 `DATA_MODEL.md`「分管范围配置」。

## 进度

- 2026-10-16 开工：画像、README；字段约定待 one-architecture 统一，先做试点（VS-07 骨架、B-13/B-14/B-27 表），约定已出（CONVENTIONS.md），试点改按它写后铺开。
- 2026-10-16 试点：VS-07（PRC-80–85、128、129、130、136–138）骨架、B-13/B-14/B-27 表（18 个聚合、44 张表）已写；AGG-117、118 补号。
- 2026-10-16 按 one-architecture 转用户要求出样板 `SAMPLE.md`，其余流程与 B-15/18/30/31 表暂停，等用户意见（铺开备忘见 NOTES.md 末节）。
- 2026-10-16 用户批准方法与样板（intent 162820、162821），铺开：26 个业务流程、7 个事件策略、3 个单聚合维护全部出骨架；32 个聚合、91 张表；NOTES 合并五个分片。下一步：按新原则（审批规则、why_human、八维度 QC）逐价值流修订并自检。
- 2026-10-16 按新原则修订、八维度 QC、脚本缺口清零、聚合收敛（commands / state-machines / events / raci，parts/rebuild.py 合并）；落实 S-28、S-32、S-35、S-36、S-40、S-41、S-42；one-architecture 跨组接缝检查清零。待用户答确认页后回改。

- 2026-10-17 C7-people：SD-12 roles 已核对，SD-15 归段说明与闭环结局映射已对齐；rebuild 生成，origin/main 8ff6baf4 检查复跑并按五个价值流/支撑章节形成 REVIEW_PACK.md。QC 缺口 0、BPMN 30/30/0 警告、stage 6a 未说明 0；AGG-52、PRC-181 依赖边、命名/对象残余交架构。one-arch-people 审稿通过，返工 0 轮。
- 2026-10-17 并入云端试点 VS-11（claude/arch-cloud-pilot @ 1b914e4d，pilot/RESULT.md 第 4 节，one-architecture 裁定）：PRC-107 第 1–12 条采纳试点写法（去〔缺〕退回知识条目，退回由评审结论表达；结局加已修订；加定时复审触发；评审 P10D 非中断升级 SH-M1，估值仍归待用户定 8），PRC-182 保留本组版本；AGG-107 撤回与逾期中断回补、目录 stages_note 由 one-architecture 做。复跑 QC 缺口 0、converge 同前、BPMN 30/30、接缝同前。
- 2026-10-17 C8-people-VS14：B-31 七个流程（PRC-139、142、143、144、155、156、157）按 VS-14「获得内部服务」归段，去 support、写专属 stages_note、补负担估值与提醒结局；QC 缺口 0、BPMN 30/30、七流程归段未说明 0；评审包 REVIEW_VS14.md（交架构 A1–A9、待用户定 B31-1–10）。Codex 起草，审稿通过，返工 0 轮；rebuild.py 空行累积已修。
- 2026-10-17 ARC-1017-APE-01（旧编号 C9-people-B31）：按 B31 答复（one-architecture 03adf7eb）同步 B-31 七个流程，9aaf20dc；Codex 返工 1 轮（首轮丢注释）。进行中 ARC-1017-APE-02：按 33e28272 改 REF-04 用印核验口径、补取消员工活动登记。

## 待办（对外承诺）

- ARC-1022-APE-01 已落骨架（PRC-122 第 5 步「奖金核定重分类」→ B-16，按法人与项目拆分）；拆分口径与新增 B-15 → B-16 读边待 one-architecture、one-arch-finance 核（NOTES 结构问题，vs13pay 第 14 条）。
- PRC-127 骨架名随目录改为「按逐人实付结果更新薪酬行并标记整期已发放」（未改）。
- B-27 聚合稿定稿（补休建模为请假类型、补休额度记入时点）后通知 one-rules，以便其 RULE-413（补休扣减最低时数，现标 Review）转正；RULE-412 已写工时不读加班审批与补休余额。
