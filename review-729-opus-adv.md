复审结论：必改
commit：a7761208148fdc80335e01423c257d19cc08cc77
复审者：opus-终审-729adv

patch-id：43a37f0c24f83a7b51239bd9ac6f717193c356e4

## 影响

只涉及数据库：新增表 `platform_identity.permission_adjustments`，新增一行 `platform_audit.audit_subject_kinds`（`PERMISSION_ADJUSTMENT`），新增枚举常量，并在 §4b 与 dev/prod 迁移后脚本里撤销运行角色的 DELETE、TRUNCATE。主代码还没有读写这张表的地方，所以影响只到两处：启动检查 `AuditSubjectKindCheck`，以及运行角色在库里的权限。两处我都在临时 PG17 集群上实测过：全部 29 个迁移以 `one_dev_owner` 身份执行，随后执行 `one-db-roles-schemas.sql` 与 `dev-db-after-migrations.sql`，攻击语句一律以 `SET ROLE one_dev_app` 执行。证明到的调用层级是一层。

## 必改

1. **本 PR 新加的防线「运行角色无 DELETE/TRUNCATE」没有任何测试会因它被删而变红**（防线不可证，按 PROMPT 的破坏验证一节，不变红即必改）。
   - 位置：`docs/ops/one-db-roles-schemas.sql:150`、`docs/ops/runbooks/sql/dev-db-after-migrations.sql:38`、`docs/ops/runbooks/sql/prod-db-after-migrations.sql:38`。
   - 复现：在临时副本里删掉三份文件中的 `REVOKE DELETE, TRUNCATE ON platform_identity.permission_adjustments ...` 行，再运行 `python3 -I scripts/guards/db_scripts_aligned.py`，输出 `database scripts aligned: 13 schemas, 9 version tables`，返回码 0，没有变红。
   - 也没有测试断言这条权限：`git grep -n -E "has_table_privilege|table_privileges" -- backend/src/test` 只命中 `RoleSeparatedDeploymentIT:196`（审计分区）和 `AdministrationGrantsIT:86`；`backend/src/test` 里提到 `permission_adjustments` 的地方，没有一处断言 DELETE、TRUNCATE 或 `one_dev_app`。
   - `RoleSeparatedDeploymentIT#theRuntimeAccountHasOnlyWhatItNeeds`（172-177 行）只循环检查 `role_versions` 和 `account_role_grants`。这个 IT 本来就会执行 §4b（`prepare()`，见 78-90 行），适合加断言的位置就在这里。
   - 实测撤销本身是生效的：`one_dev_app` 只剩 `INSERT,SELECT,UPDATE`，`DELETE` 和 `TRUNCATE` 都报 `permission denied for table permission_adjustments`。问题在于以后有人删掉这一行，不会有测试发现。
   - 修法：在 `RoleSeparatedDeploymentIT#theRuntimeAccountHasOnlyWhatItNeeds` 的表清单里加入 `platform_identity.permission_adjustments`，断言 delete 和 truncate 都以 permission denied 被拒。这不是验收测试，实现者可以改。随后删掉 REVOKE 行，确认这条断言会变红。

2. **`ck_permission_adjustments_uphold_reason` 限制不了原文长度：两端补空格后，任意长度的维持原因都能入库**（硬约束失守，丢失一道防线）。
   - 位置：`V20261103120000__platform_identity_permission_adjustments.sql:83`，约束写的是 `CHECK (char_length(btrim(uphold_reason)) BETWEEN 1 AND 500)`。
   - 依据：
     - 卡上不变量 14 写「维持必填原因，非空白，≤ 500 字」，承担者只有这一条约束，「落不成约束的理由」一栏是「—」。
     - `docs/rules/naming-standard.md` §2.4a 第 5 项写的是「Free-text columns have a length cap (`CHECK (char_length(col) <= n)`)」，限的是列的原值。
     - 仓库里已有的写法是 `char_length(col) <= 500 AND btrim(col) <> ''`，例如 `organization_workforce` 基线的 `ck_positions_name`、`ck_org_units_name`。
   - 复现（以运行角色执行）：插入一行 `UPHELD` 敏感角色复核，`uphold_reason = repeat(' ',4000)||'x'||repeat(' ',4000)`，插入被接受，读回 `char_length(uphold_reason) = 8001`。验收用例 `theUpholdReasonIsOneToFiveHundredCharacters` 只测了 `"留".repeat(501)`，没有覆盖带空格补齐的情形，所以测不出这个漏洞。
   - 补充：卡 7.1（f-1.md 第 531 行）给出的预期 DDL 正是这种写法，实现照抄了卡，缺陷出在卡上。所以要两边一起改：迁移里改成 `CHECK (char_length(uphold_reason) <= 500 AND btrim(uphold_reason) <> '')`，同时更正卡 7.1 的预期 DDL，免得结构差异核对对不上。这个迁移还没合并，可以直接改。实现者不能改验收测试，「补齐后超长被拒」这条用例交给测试代理补。

## 建议

- 「非空白」库里只拦住了空格：全角空格 `E'　'` 作为原因能入库（已实测）。`F1_729_ACCEPTANCE.md:36` 已经记下这一点，并交给 #730 的 400 承担，这里只做记录。
- 运行角色有 UPDATE，可以把 `original_permission_adjustment_id` 改成互相引用，形成环（121↔122，已实测被接受）。按卡的设计，状态只经聚合方法改变，所以这不是库内缺陷。属主角色一条 DELETE 就能删掉整个环（已实测），不会阻碍「按链删除」。
- 卡外的旧问题：#728 的两张子表 `role_version_custom_org_units`、`authorization_request_custom_org_units` 也没有权限断言，可以趁必改 1 一起加进同一个循环。

## 已核且无问题（攻击均以 one_dev_app 执行；「拒」表示被对应约束拒绝）

| 攻击 | 结果 |
|---|---|
| 合法 OPEN 任职变化条目（正对照） | 接受 |
| SCOPE_REVIEW 转 EXPIRED | 拒（`ck_permission_adjustments_expiry`） |
| 关于角色的条目（ORG_UNIT_INACTIVE）转 CLOSED_BY_SEPARATION | 拒（`ck_permission_adjustments_closed`） |
| ASSIGNMENT_CHANGE 同时带人事命令和组织结构命令 | 拒（`ck_permission_adjustments_trigger_command`） |
| 触发字段为 ORG_UNIT，但前后部门相同 | 拒（`ck_permission_adjustments_assignment_change`） |
| 范围复核来源①按 POSITION 触发 | 拒（`ck_permission_adjustments_recipient`） |
| 敏感角色复核带收件人 | 拒（`ck_permission_adjustments_recipient`） |
| 同一（类型，人，人事命令，触发字段）插两条 | 拒（`uk_permission_adjustments_assignment_trigger`） |
| 来源②已有一条 OPEN 时，把另一条 ADJUSTED 用 UPDATE 改回 OPEN | 拒（`uk_permission_adjustments_inactive_org_unit`） |
| EXPIRED 且 is_held_at_expiry = false，却没有 regeneration_decided_at | 拒（`ck_permission_adjustments_expiry`） |
| 维持人就是当事人 | 拒（`ck_permission_adjustments_not_own`） |
| 任职变化条目转 ADJUSTED，但没有调整人 | 拒（`ck_permission_adjustments_adjusted`） |
| UPHOLD_EXPIRY 条目引用自身 | 拒（`ck_permission_adjustments_original`） |
| escalation_due_at = created_at | 拒（`ck_permission_adjustments_escalation_due_at`） |
| 运行角色执行 DELETE / TRUNCATE | 拒（permission denied） |

- 列、约束、索引与卡 7.1 逐项对照一致，含 DD 裁定后的人事命令列与暂停事实列。卡 7.2 的「CHECK 取值」已由对象类别登记表加外键取代，迁移插入的登记行 `has_object = true`，与常量 `PERMISSION_ADJUSTMENT(true)` 一致；`AuditSubjectKindCheck` 第 79 行会比对 has_object。
- 全部迁移按版本顺序在 PG17 上执行无错；新迁移的版本号 `V20261103120000` 和 `V20261103120100` 都高于基线上最高的 `V20261102180000`。
- 这次没有改动已合并的迁移。
- 破坏验证：

| 防线 | 破坏方式 | 结果 |
|---|---|---|
| 运行角色无 DELETE/TRUNCATE | 删掉三份脚本里的 REVOKE 行 | `db_scripts_aligned.py` 不变红；也没有 IT 断言它（必改 1） |
| 维持原因 ≤ 500 字 | 不改代码，直接用两端补空格的原因插入 | 约束本身放行（必改 2） |
| 对象类别登记行 | 未在本轮重做；首轮 review-729-opus.md 已证明删掉登记行会让启动失败 | 采信首轮 |

临时集群与副本都在 scratchpad，结束后已停止并删除。
