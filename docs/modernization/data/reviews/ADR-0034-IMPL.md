## 数据设计审查：ADR-0034-IMPL

- 审查：one-data，2026-10-17，依据卡 `claude/one-1a-par-adr0034-card@bc7c14bc`（`phase1a/cards/ADR-0034-IMPL.md`，数据设计一节与第 7 节；基线 `cfb04dd4`）。门禁 B1 对授权表以「授权数据归 `platform.identity`、不进信息图」（one-architecture 对 F-1 A-1 的裁定，A-12 同）为出处，通过。
- 总体结论：**部分通过**，按门禁 §1.3 拆分签发：PR-1 的两张新表与 PR-2 的 `roles` 收窄可签；`authorization_requests` 的改动（两列、`VOIDED`、`ck_authorization_requests_base`）因 A-3、A-7、A-11 未裁，不签；B 组（ID-01、ID-02、ID-10、ID-13、AQ-2、AQ-6）卡自己标「待 C-3」，不在本次范围，并入后补审。

### 逐条判定（涉及签发范围的）

- 通过：B2 聚合与 schema；B3 / B3a 角色版本与授予（`ENABLED/DISABLED`、「已删除 = 无开放版本」、授予无状态列，由 `effective_to_at` 与 `revocation_cause_kind` 成对承担）；B4 关系与基数（同 schema 外键齐全、跨 schema 只存 ID）；B5 / O4 不变量 24 条（承担者与「落不成约束的理由」成立；#1、#2、#13 用排他约束，#5 触发器 + §4b 双保险）；O3；O5；O6（D17 维度型、生效依据 `DECLARED`、一个来源一个负责人、`code / kind / is_administrative` 不进版本的逐列理由）；O8；O10（当前读取部分索引，热点阈值成立；`ix_role_versions_role_id_current` 等）；O13 / O14；O15（条款对照完整）；C1、C4（`change_reason` 等的允许写入内容清单成立）；A、T、E1–E3。
- 我核对过的 DDL 细节（读卡文，未执行）：`refuse_history_rewrite` 的 `to_jsonb(NEW) - TG_ARGV` 写法能表达「只许改所列关闭列」，TRUNCATE 语句级触发器在函数第一个分支即拒绝，不触碰 OLD/NEW；`ex_role_versions_role_id_period` 的 `[)` 区间允许零长；`is_normalized_code_set` 对空数组与单元素的结果为真；两张表都有 `created_at` / `updated_at`。**实际能否建库以表结构 PR 的 `pg_dump` 对比与 `DdlNamingTest`、`DataArchitectureTest` 为准。**

### 回答 D 组 6 点

- **D-1 保留期写法：接受 `{policy: PERMANENT}` + 注释**（与 `authorization_requests` 先例同法）。理由：D4 要求 `<n>y` 指名已存在的作业，而该作业 3 年内无到期行；用 `gaps` 会因「不违反任何规则」被守护判失败（注册表第 231 行已有同类说明）。条件：注释里写全四项——0034-Q2 口径（在线 3 年、归档到满 10 年）、清理作业落点（随审计在线删除作业实现）、**负责人**、**期限**（建议「首次生产部署后 30 个月内」）；并写明清理要按依赖顺序（子表 `role_version_custom_org_units` 随其角色版本一起处理，F-1 子表跟随）。该条登记进下一次保留期清理的待办（`RETENTION_PROPOSAL.md` 同类条目），避免永远停在 PERMANENT。
- **D-2 `_by` 外键：建**。同 schema 的账号 ID 建 `fk_…_recorded_by / closed_by / granted_by / revoked_by`（`ON DELETE RESTRICT`）与部分索引，卡已按此写；F-1 `permission_adjustments` 的 `upheld_by`、`adjusted_by` 我在 F-1 第二轮已要求补外键，现在两卡口径一致，F-1 的「待核 5」取消。
- **D-3 `MIGRATION` / `MIGRATION_ROLLBACK` / `migration_batch_id`：不判 B7 不通过。** 它们是已采纳 ADR-0034（第 1 项第 2 条、第 2 项第 1、3、4、6 条）明文规定的列与取值；门禁文件头规定与上位规则冲突时以上位规则为准，且 SESSION_PLAYBOOK TEAM-30 要求字面不一致交 one-architecture 而不是自行删。B7 的判例（报价、记账凭证）是没有 ADR 规定的取值。我留一条 `[建议]`：迁入卡写 E3 时核对这几个取值与批次回退语义，不对就在基线关闭前改。
- **D-4 PR-1 `writers: []`：接受**。比 `optional + due` 准确（`optional` 表示表只在一侧存在，这里表是存在的）；PR-2 / PR-3 改为 `[platform.identity]`，由 `DataArchitectureTest` 兜底。
- **D-5 `granted_by` 成对规则「有且只有 MANUAL 有」：接受**。比 ADR 收紧但不冲突（`DEFAULT_ROLE`、`MIGRATION` 是系统写入，没有授予人；运维命令的操作人与见证人本就记在审计）。请在 `ck_account_role_grants_grant` 的列注释里写明，将来加渠道时同步改这条约束。
- **D-6 `account_role_grants.role_id` 只有部分索引：接受**。`roles` 行永不删除，`RESTRICT` 检查不会发生；卡列出的查询路径里没有按角色查已关闭授予的路径（还原走 `user_id`）。若以后出现「某角色历史上所有持有人」查询，再加全量索引（可无损追加）。

### 需修改 / 建议

- **需修改（小）**：
  1. 注册表 `role_versions` / `account_role_grants` 的保留期注释按 D-1 补四项（负责人、期限、清理顺序）。
  2. `ck_account_role_grants_grant` 的注释写明 D-5 的「有且只有」。
  3. 卡「待核 D-1…D-6」改为已答，指向本记录。
- **建议（不阻塞）**：
  1. A-6：同一变化可能出现两条审计（事务内直写一条、`AuditDomainEvents` 监听再写一条）。数据角度建议只留事务内直写的那条，对应事件标不审计——取证时一变化一条，`version_audit_entry_id` 才有唯一指向；最终由 one-architecture 裁。
  2. A-5：`change_reason NOT NULL` 在 MANUAL 路径只能填固定说明，信息量近于零。若 ADR 字面不变，保持；若 one-architecture 愿放宽，MANUAL 可空更诚实。
  3. A-4：关闭时 `updated_at` 写关闭时刻，与 F-1 子表「行不可改、`updated_at` 等于 `created_at`」口径一致（都是「最后一次修改时刻」），我接受。
  4. 控制 `MIGRATION_ROLLBACK` 的 `revoked_by` 应为空，可顺手加进 `ck_account_role_grants_revocation`；现在只约束了 `MANUAL`、`SEPARATION`。

### 不签发的部分及原因

- `authorization_requests` 的 `base_account_version_number`、`base_role_version_ids`：A-3（`uuid[]` 无类别词，须先定 §2.4 增 `_ids` 还是改子表）未裁。数据角度：基准版本是只做相等比较的快照值，版本行只追加不删除，数组不需要外键，我接受数组形态；但类别词要 one-architecture / one-glossary 先定。
- `VOIDED`：B3a ④ 要求事件与转换一一对应，而作废的事件 / 审计动作（A-7）未裁；`ck_authorization_requests_base` 与具名 `ck_authorization_requests_request_status` 超出 ADR 字面（A-11），数据上我认可（更严、符合具名规则），但按 §11 第 9 条由 one-architecture 记偏差。
- B 组：待 C-3 并入后，对 ID-01、ID-02、ID-10、ID-13（授权申请部分）、AQ-2、AQ-6 补审；其预期 diff 须按 `DATA-REPAIR-identity.md` 原文逐字并入。

- 结论：部分通过
- 数据设计：通过（one-data，2026-10-17，依据 `claude/one-1a-par-adr0034-card@bc7c14bc`，范围限 PR-1 的 `role_versions`、`account_role_grants` 及其函数、触发器、btree_gist 存在性检查，与 PR-2 的 `roles` 收窄、`role_permissions` 与 `account_roles` 退役、种子移出；不含 `authorization_requests` 的任何改动与 B 组）

---

## 补签（2026-10-17，依据 `claude/one-1a-par-f1-card@e0d60120` 上的 ADR-0034-IMPL 卡）

- 自 `bc7c14bc` 起该卡「数据设计」一节与第 7 节 DDL 无改动（比对了差异，只动了测试表、第 13 节的裁定记录与 A-13 的复职触发方式），原签发继续有效。
- 新裁定使两项可补签：A-3（one-architecture 取 (a)：`NAMING_STANDARD` §2.4 增 `_ids`（uuid[]）类别词并同 PR 改 `DdlNamingTest`）、A-11（接受具名与 `ck_authorization_requests_base`）。
  - **补签**：`authorization_requests` 的 `base_account_version_number`、`base_role_version_ids` 两列及其注释，与 `ck_authorization_requests_base`（前提：§2.4 与守护改动在同一 PR）。
  - **仍不签**：`VOIDED` 取值与具名 `ck_authorization_requests_request_status`——B3a ④ 要求事件与转换一一对应，而作废的事件 / 审计动作（A-7）仍未裁；B 组仍待 C-3。
- 数据设计：通过（one-data，2026-10-17，补签，依据 `claude/one-1a-par-f1-card@e0d60120`；增加范围：`authorization_requests` 两列与 `ck_authorization_requests_base`；其余同原签发）

---

## 第二次补签（2026-10-17，依据 `claude/one-1a-par-f1-card@f46c9bf5` 上的 ADR-0034-IMPL 卡）

- 三处小改已落卡并核对：保留期注释四项（口径、清理落点、负责人 one-1a-par、期限首次生产部署后 30 个月内、按依赖顺序清理）、`granted_by` 注释写明「有且只有 MANUAL 有」并提示加渠道时同步改约束、待核 D-1…D-6 改为已答。
- A-7 已裁定（one-architecture 2026-10-17）：直写审计 `platform.identity.authorization.voided`，同时发事件 `AuthorizationVoided` 给消费方并标 `@NotAudited`。B3a ④ 成立：转换 `CONFIRMED → VOIDED` ↔ 事件 `AuthorizationVoided` ↔ 审计动作 `authorization.voided` ↔ 状态值 `VOIDED` ↔ `AuthorizationRequestStatus` 枚举。
- **补签**：`VOIDED` 取值与具名 `ck_authorization_requests_request_status`（A-11 已接受具名）。
- 请在卡的「状态、关系、基数落点」一段把「作废见 A-7」改为写明上述事件名（文字更新，不影响本次签发）。
- 仍不在范围：B 组（待 C-3）。
- 数据设计：通过（one-data，2026-10-17，第二次补签，依据 `claude/one-1a-par-f1-card@f46c9bf5`；增加范围：`authorization_requests` 的 `VOIDED` 取值与 `ck_authorization_requests_request_status`；至此 PR-1、PR-2、PR-3 的 DDL 均已签发，B 组除外）
