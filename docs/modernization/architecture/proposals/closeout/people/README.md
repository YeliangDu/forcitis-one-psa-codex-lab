# 架构收尾 · 人员组（one-arch-people）

范围：VS-07 任用员工、VS-09 达成经营目标、VS-11 沉淀复用知识、VS-13 获得报酬；BC：B-13 `organization.workforce`、B-14 `organization.attendance`、B-15 `organization.payroll`、B-18 `operations.planning`、B-27 `organization.leave`、B-30 `km.knowledge`（25 个聚合）。依据 `ARCH_CLOSEOUT_PLAN.md` §1 ①②、§4。

## 文件

字段与文件名以 `../CONVENTIONS.md`、`../README.md` 为准（one-architecture 2026-10-16，PR #384）。

| 文件 | 内容 |
|---|---|
| `processes.people.yaml` | 本组流程的 ⑥b 骨架片段（交 one-architecture 合并为 `processes.v2.yaml`） |
| `data-model.people.yaml` | 本组聚合的表级片段（合并为 `data-model.v1.yaml`） |
| `NOTES.md` | 接缝问题、结构问题、新用语、格式问题、待用户定 |
| `PROCESS_SKELETONS.md`、`DATA_MODEL.md` | 说明稿（依据、已建 / 改形 / 新建、迁入来源、提议新增流程） |
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

## 进度

- 2026-10-16 开工：画像、README；字段约定待 one-architecture 统一，先做试点（VS-07 骨架、B-13/B-14/B-27 表），约定已出（CONVENTIONS.md），试点改按它写后铺开。
- 2026-10-16 试点：VS-07（PRC-80–85、128、129、130、136–138）骨架、B-13/B-14/B-27 表（18 个聚合、44 张表）已写；AGG-117、118 补号。
- 2026-10-16 按 one-architecture 转用户要求出样板 `SAMPLE.md`，其余流程与 B-15/18/30/31 表暂停，等用户意见（铺开备忘见 NOTES.md 末节）。
