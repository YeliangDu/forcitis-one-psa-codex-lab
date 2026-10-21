# 第二批派工卡：业务对象、聚合、命令、领域事件、组织与角色（ARC-1021-COO-10 批次 2）

状态：草稿｜2026-10-21｜负责：one-architecture

依据：`proposals/DECISION_IMPACT_NETWORK_PROPOSAL.md` §1.1、§1.3（用户 U1–U11 已定）；批次表 `research/networks/ADR_NODE_FREQUENCY.md` §3 第 2 批（调度已批）：07 组织与角色 43%、10 业务对象 39%、11/I04a 命令 28%、I04b 领域事件 22%、I03 聚合 12%（过时，重登）。理由：这五类沿对象链「对象 → 状态转换 → 命令 → 事件 → 聚合」相互引用，要一起编号；10＋I08 共现 26 份。

第一批交付后的事实（@c01ad708）：INV 148 条中 133 条所属 BC 未建；INV→执行点 3/148、INV→用例 1/148。第二批不追覆盖率，先把对象链编号立起来，连线由 `panorama_build.py` 生成。

## 0. 本批共同约定

- 沿用第一批约定：生成文件开头写「由 <脚本> 生成，勿手改」；手写与生成分文件；文件放 `architecture/ssot/<节点>/`，`meta.status: proposal`。
- **新前缀先过用语**（U9）：本批新前缀 `CMD-`（命令）、`ORG-`（组织单元类别）、`ROLE-`（角色，已有但分散）、`TRN-`（状态转换），与目录名 `ssot/10-objects`、`ssot/07-org-roles`、`ssot/i03-aggregates`、`ssot/11-commands`、`ssot/i04b-events`，开工前由本会话用 `glossary/new_term_issue.py` 交 one-glossary；未批前照用、标 proposal。
- **编号稳定**：已有编号（OBJ-、AGG-、ROLE-、eventType）沿用不重编；新编号按来源全名哈希或按登记顺序追加，重跑不变；删除只标 `retired`，不复用。
- **只统计、不回填**（U2）：不改 `ENTERPRISE_INFO_MAP_DRAFT.md`、`ENTERPRISE_AGGREGATES_DRAFT.md`、`closeout/*`、`events/*.yaml`、代码；转换后在源文件加一行「已迁至 ssot/…」由本会话做。
- **SSOT 重建线暂停令**（intent 2026-10-19-223711）不受影响：本批只建登记、不改设计文件；对象冻结（`panorama/FREEZE_FINAL.yaml`）的结论照搬，不重判。
- 每卡完成后报：条目数、自动生成数、自动化率、`panorama_build.py` 新增检查的缺口数（U2）。
- 基线：`claude/one-architecture-network-batch1` 合入 main 之后的 main。

## 卡 7　ARC-1021-ARC-07　业务对象登记（10）：对象、状态、转换

- **类别**：功能补做（登记）。**执行者**：Codex（机械转换，gpt-6.1-sol，medium）＋ one-architecture（两套状态机合并的语义判定，sonnet 逐条提议、本会话定稿）。
- **做什么**：
  1. Codex 写 `ssot/10-objects/convert_objects.py`：把 `ENTERPRISE_INFO_MAP_DRAFT.md` §1 的对象表逐行转成 `objects.yaml`（`id`、`name`、`parent`、`level`、`term`、`in_system`、`source`），按 `FREEZE_FINAL.yaml` 标冻结结论；不改文字、不合并、不新增；附转换前后条数核对。
  2. Codex 抽出两套状态机原样并列：OBJ 键的 8 个对象与 AGG 键的 130 台（出处见提案 §1.3 第 3 条），写 `state-machines.raw.yaml`，每台带出处行。
  3. one-architecture 定 `transitions.yaml`（手写）：每条转换 `id`（TRN-）、`object`（OBJ）、`from`、`to`、`by_command`（暂空，卡 9 回填）、`source`；两套对不上的列 `pending`，不硬并。
- **允许改动**：`architecture/ssot/10-objects/`；`panorama_build.py` 读取段（节点 09 对象改从 `objects.yaml` 读，同第一批 INV 的做法）。
- **验收**：对象条数＝原表条数；`parent` 指向的 OBJ 都存在；冻结结论逐条一致；`panorama_build.py` 原有 N09 系列检查缺口数不增（增了须逐条解释）；sonnet 抽 15 条与原表逐字比对。

## 卡 8　ARC-1021-ARC-08　聚合重登（I03）：已建聚合与规划聚合并成一份

- **类别**：功能补做（生成器＋对照）。**执行者**：Codex（扫描，gpt-6.1-sol，high）＋ one-architecture（已建 ↔ 规划对照）。**前置**：卡 7（对象编号）。
- **做什么**：
  1. Codex 写 `panorama/scan_aggregates.py`，按 `ddd/aggregates.yaml` 头部的既定判据（domain 包中继承 `kernel.AggregateRoot` 的类，并上数据访问注册表 role=AGGREGATE_ROOT 的 `aggregate` 字段）从代码生成 `ssot/i03-aggregates/built.yaml`：`id`（`<模块>/<类>`）、`bc`、`root_class`、`tables`、`commands`（application 包中改该聚合的类名）、`events`（发布的 eventType）、`source`。现登记停在 10-16（15 条），此后新建的聚合由此补上。
  2. Codex 把 `ENTERPRISE_AGGREGATES_DRAFT.md` 的规划聚合表（AGG-001..）转成 `planned.yaml`（逐行、不改字）。
  3. one-architecture 写 `mapping.yaml`：已建 `<模块>/<类>` ↔ 规划 `AGG-nnn`，一对一、一对多或无对应各写依据；sonnet 提议、本会话定稿。
  4. `ddd/aggregates.yaml` 改为只引用 `built.yaml`（头部加「已迁至 ssot/i03-aggregates」），避免两份登记。
- **允许改动**：`panorama/scan_aggregates.py` 及自测；`ssot/i03-aggregates/`；`panorama_build.py` 读取段；`ddd/aggregates.yaml` 头部一行。
- **验收**：重跑一致；`built.yaml` 覆盖 15 条旧登记且差异逐条说明；破坏验证：临时副本删一个聚合根的 `extends AggregateRoot` 后该条消失；新增检查 N09-M6「每个已建聚合对上规划聚合或写明无对应」。

## 卡 9　ARC-1021-ARC-09　命令编号（11 / I04a）：CMD- 一套编号，挂对象转换与 BC

- **类别**：功能补做（生成器＋对照）。**执行者**：Codex（gpt-6.1-sol，high）＋ one-architecture（规划命令与已建命令对照、`changes` 判定）。**前置**：卡 7、卡 8。
- **做什么**：
  1. Codex 写 `panorama/scan_commands.py`，从 `closeout/RESPONSIBILITY_MATRIX.yaml` 与 `closeout/{finance,people,revenue}/commands.yaml` 合并规划命令（中文名为键，586 / 592 条，去重规则：同 BC 同中文名为同一条，跨 BC 同名分立并互列），按「BC＋中文名」生成稳定 `CMD-` 编号；字段 `name`、`owner_bc`、`object`（OBJ）、`aggregate`（AGG）、`actor`（角色名原文）、`source`。
  2. 同一脚本从代码抽已建命令（`application` 包中的命令处理类，判据与 `ddd/aggregates.yaml` 的 `commands` 一致），写 `built` 段，按类名列出。
  3. one-architecture 写 `command-mapping.yaml`：已建类 ↔ CMD；以及 `changes`（CMD → TRN，回填卡 7 的 `by_command`）。对不上的列清单不硬挂。
- **允许改动**：`panorama/scan_commands.py` 及自测；`ssot/11-commands/`；`ssot/10-objects/transitions.yaml` 的 `by_command`；`panorama_build.py` 读取段。
- **验收**：重跑一致；合并前后条数核对与去重清单；新增检查 N11-1「每个命令有 owner BC」、N11-2「每个已建命令类对上 CMD」、P-1「流程步骤改状态须声明命令」（步骤 `prc` 全空的，先只计数）。

## 卡 10　ARC-1021-ARC-10　领域事件接入（I04b）

- **类别**：功能补做（读取与连线）。**执行者**：Codex（gpt-6.1-sol，medium）。**前置**：卡 9。
- **做什么**：`panorama_build.py` 读 `docs/modernization/events/*.yaml`（59 条消息，不改源）：节点 EVT（键 eventType），字段发布者 BC、版本、订阅方；连线 EVT→BC（发布者）、CMD→EVT（经 `built.yaml` 的聚合与命令）、EVT→AGG；`fact` 字段先留空（12 业务事实未登记，第三批）。
- **允许改动**：`panorama_build.py` 及自测；生成物。
- **验收**：重跑一致；新增检查 N04b-1「每个事件有唯一发布者与版本」、N04b-2「跨 BC 只走已发布事件或契约」（以 `module-dependencies.txt` 为准，只报告）。

## 卡 11　ARC-1021-ARC-11　组织与角色（07）：ORG、ROLE 一份登记，挂权限码与命令

- **类别**：功能补做（登记＋生成）。**执行者**：Codex（权限码抽取，gpt-6.1-sol，medium）＋ one-architecture（三处角色名的合并判定，sonnet 提议、本会话定稿）。**前置**：卡 9（命令的 `actor`）。
- **做什么**：
  1. Codex 从 `platform/identity/api/Permission.java` 与 `platform_identity` 迁移中的角色与授予种子抽出权限码与系统角色，生成 `ssot/07-org-roles/permissions.yaml`（全自动）。
  2. one-architecture 写 `roles.yaml`：合并 `ssot/01-stakeholders/SSOT.yaml` roles（11）、`glossary/terms.yaml` kind=role（33）、`RESPONSIBILITY_MATRIX.yaml` 角色（28）三处；每个 ROLE 写 `aliases`（三处原名）、`permissions`、`bears`（CAP，可空）、`host_category`；同名异义、方言交 one-glossary（`new_term_issue.py`），不自行裁定。
  3. `org-units.yaml`：组织单元类别与法人（ORG），从 `organization_workforce` 迁移的类别枚举与 `ENTERPRISE_INFO_MAP_DRAFT.md` 抽取，不含个人信息与生产数据。
- **允许改动**：`ssot/07-org-roles/`；`panorama_build.py` 读取段。
- **验收**：权限码条数＝代码中枚举数；三处角色名每个都落到一个 ROLE 或列入待用语裁定清单；新增检查 N07-1「每个命令的 actor 对上 ROLE」、N07-2「每个 ROLE 有人承担（host_category 非空）」、N07-3「职责分离：同一命令的发起与审批角色不同」（只报告）。

## 依赖与顺序

卡 7 → 卡 8 → 卡 9 → 卡 10、卡 11（10、11 可并行）。卡 7、8 的 Codex 部分可并行起步（转换互不依赖），对照部分按序。

## 本批交付后报告的数

| 层 | 条目数 | 自动生成 | 自动化率 | 缺口 |
|---|---|---|---|---|
| 10 对象 | 原表条数 | 全部（转换） | 待交付 | 状态机未合并的条数 |
| I03 聚合 | 已建＋规划 | 已建全部、规划转换 | 待交付 | 已建无规划对应数 |
| 11 / I04a 命令 | 合并后条数 | 全部 | 待交付 | 已建类无 CMD 数、无 owner BC 数 |
| I04b 事件 | 59 | 全部 | 100% | 无唯一发布者数 |
| 07 组织与角色 | 合并后条数 | 权限码全部 | 待交付 | actor 对不上 ROLE 数 |
