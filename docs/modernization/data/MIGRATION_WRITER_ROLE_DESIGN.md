# 迁入行写入通道设计（`source_kind` / 渠道为 MIGRATION 的行由谁写）

- 起草：one-data，2026-10-22。来源：`data/reviews/IMP-1021-NX5-03.md` 开放项 1 与 2；one-coordinator 2026-10-22 排序第 4 项。
- 状态：**设计，opus 首轮复审（DAT-1022-DAT-01：2 阻断、2 高、2 中）与 sonnet 改后复核（DAT-1022-DAT-02：2 阻断、2 高、3 中）均已按意见改；日历确认路径改为迁入角色列级 UPDATE（需 one-architecture 在迁入规格评审登记对 ADR-0036 授权清单的补充）**；复审后随 one-1a-next-5（编号）与 one-1a-par（日历）的下一批 DDL 一起落，基线开放期直接改基线。
- 不需要用户决定：只落实 ADR-0036 第 1 条已定的专用迁入角色，不新增角色、不改授权。

## 1. 问题

两处触发器只放行「库属主角色的成员」写迁入行：

| 表 | 触发器 / 函数 | 判定 |
|---|---|---|
| `platform_numbering.numbering_rule_versions`（`source_kind` 为 `SEEDED`、`MIGRATION`） | `guard_rule_version_source` | `pg_has_role(current_user, 表属主, 'MEMBER')` |
| `organization_attendance.work_calendar_versions`（`source_kind = 'MIGRATION'`） | `require_owner_for_migration_version` | `pg_has_role(current_user, 表属主, 'USAGE')` |

ADR-0036 第 1 条：迁入由离线命令经各 BC 的迁入用例写目标表，连接用专用库角色 `one_<环境>_history_loader`（无 DDL，只 `SELECT`、`INSERT`，窗口外 `NOLOGIN`），**不是**属主角色成员。所以按现在的触发器，迁入进程插入 `MIGRATION` 行会被拒；而另一条路（迁入进程以属主身份写）违反 ADR-0036 的最小授权。两处的判定函数（`MEMBER` 与 `USAGE`）也不一致，`one_<环境>_migrator` 带 `INHERIT` 且 `SET role` 为属主，两者对它结果相同，对别的角色会分叉。

## 2. 备选

| 方案 | 做法 | 评价 |
|---|---|---|
| A 绑定唯一角色名 | 触发器放行属主角色成员，或「由属主名推出的那一个迁入角色」的成员（`one_<环境>_owner` → `one_<环境>_history_loader`） | 角色名由 ADR-0036 固定；不用正则，不会被同实例别的环境或新建的同名前缀角色放行 |
| B 正则匹配 | `^one_[a-z0-9]+_history_loader$` | **否决**（opus 复审）：PG16 起持 CREATEROLE 的账号可新建匹配的角色再授予运行角色；dev、prod 两套互相放行 |
| C 迁入进程用属主 | 迁入写入以属主身份 | 违反 ADR-0036 最小授权，**否决** |
| D 安全定义者函数 | 迁入进程只能调属主拥有的写入函数 | 迁入用例要做领域校验后写目标表，不是调一个写入函数，**否决** |

## 3. 决定（建议）：方案 A

两个判定函数（各 schema 各放一份同体函数，schema 之间不共用）：

```sql
CREATE FUNCTION <schema>.is_owner_member(table_oid oid) RETURNS boolean LANGUAGE sql STABLE
SET search_path = pg_catalog, pg_temp AS $$
    SELECT COALESCE(pg_has_role(current_user, (SELECT relowner FROM pg_class WHERE oid = table_oid), 'MEMBER'), false)
$$;
CREATE FUNCTION <schema>.is_migration_writer(table_oid oid) RETURNS boolean LANGUAGE sql STABLE
SET search_path = pg_catalog, pg_temp AS $$
    SELECT <schema>.is_owner_member(table_oid) OR COALESCE(pg_has_role(current_user,
        (SELECT r.oid FROM pg_roles r WHERE r.rolname = regexp_replace(pg_get_userbyid((SELECT relowner FROM pg_class WHERE oid = table_oid)), '_owner$', '_history_loader')
           AND r.rolname <> pg_get_userbyid((SELECT relowner FROM pg_class WHERE oid = table_oid))), 'MEMBER'), false)
$$;
```

- 两个函数都是 `SECURITY INVOKER`（默认）：若是 `SECURITY DEFINER`，`current_user` 恒为属主，等于放行所有人。守护断言 `prosecdef = false` 且 `proconfig` 含固定的 `search_path`。
- 调用处统一写 `IF NOT <schema>.is_migration_writer(TG_RELID) THEN RAISE …`；函数内已 `COALESCE(…, false)`，NULL 一律拒绝（日历原写法没兜底，要补）。
- 判定统一用 `MEMBER`（`MEMBER` 不看 `INHERIT`，比 `USAGE` 宽：`NOINHERIT` 的属主成员不必 `SET ROLE` 也能通过；所以真正的屏障是部署校验「运行角色与只读角色不是属主角色成员」，下面 §4 第 3 条为硬性检查）；不用 `USAGE`。`one_<环境>_migrator` 因 `ALTER ROLE … SET role = 属主`，会话里 `current_user` 就是属主，两种写法对它结果相同。
- **种子行（编号表 `SEEDED`）只认 `is_owner_member`**，迁入角色不放行；`MIGRATION` 行认 `is_migration_writer`。编号触发器 `guard_rule_version_source` 据此拆成两个分支。
- 部署校验（见 §4 第 2 条）核：迁入角色存在、成员只有批准的账号、运行角色与只读角色不在它的成员链上。

### 3.1 日历迁入行的确认路径（opus 阻断项；sonnet 复核后改定）

现状：日历版本以 `PENDING_CONFIRMATION` 插入，日明细只能加进待确认的版本（`guard_work_calendar_version_day_insert`），确认与撤回的 `UPDATE` 要再过属主判定。迁入角色按 ADR-0036 只授 `SELECT`、`INSERT`，确认不了。

**先想过的「迁入版本直接以 `CONFIRMED` 插入」不采用**（sonnet 复核）：日明细在版本行之后才能插入，而日明细守护只收待确认的父版本，迁入版本的日明细会插不进；放开又让迁入进程事后能向已确认版本补日明细，破坏不可变；「公司假日须登记」检查搬到版本插入时是空检查（日明细尚不存在）。

**决定：迁入版本照常先 `PENDING_CONFIRMATION` 插入、加日明细，再由迁入角色确认。**
- 迁入角色在 `work_calendar_versions` 上加**列级 `UPDATE`**，只限确认所写的列（`confirmation_status`、`confirmed_at`、`decision_audit_entry_id`，以现有确认触发器的 `confirmation_columns` 为准）。这是对 ADR-0036 第 1 条授权清单的一处补充（该清单把版本表的 `UPDATE` 排除在外），须由 one-architecture 在迁入规格评审时登记，**本设计不改 ADR**。列级 `UPDATE` 同时满足日明细守护 `SELECT … FOR SHARE` 所需的权限（`FOR SHARE` 需要对表有至少一列的 `UPDATE`），不必改成 `SECURITY DEFINER`。
- 日历触发器里的 `require_owner_for_migration_version(NEW)`（确认、撤回两处）改为 `is_migration_writer`；该函数改名 `require_migration_writer_for_migration_version` 或直接内联，统一用 `MEMBER`。撤回迁入版本因此属主与迁入角色都能做；列级授权只含确认列，撤回的列不授，所以实际只有属主能撤回，迁入批次回退由 one-ops 以属主执行，这与 ADR-0036 的回退由运维做一致。
- 迁入版本的 `confirmed_by` 为空、`decision_audit_entry_id` 必填，沿用现有 `ck_work_calendar_versions_confirmation_columns`，不改。
- 编号版本表的 `MIGRATION` 行：作者为空，**以 `PENDING_CONFIRMATION` 插入，由管理员与财务两人在界面确认**（`ck_numbering_rule_versions_confirmed` 要求已确认行有确认人，迁入进程不确认），不需要迁入角色的 `UPDATE`。

### 3.2 同类表

| 表 | 处理 |
|---|---|
| `platform_numbering.numbering_rule_versions` | 本设计（触发器拆分支） |
| `organization_attendance.work_calendar_versions` | 本设计（含 §3.1） |
| `platform_file.state_transitions`（`cause_kind = 'MIGRATION'`，迁入时命令与事件 ID 为空，`platform_file_baseline` 约 34–42 行） | 该表是追加表，运行角色可写；`MIGRATION` 行的写入身份是否也要库层拦，随迁入规格评审（§14.2 M1/M3）按各 schema 的 `state_transitions` 一并定，不在本批 |
| `role_versions`、`account_role_grants`（`MIGRATION` 渠道） | 同上，迁入规格评审时定 |
| W2 员工版本表（`change_channel_kind = 'MIGRATION'`） | 同上 |

函数名固定 `is_owner_member`、`is_migration_writer`，加进 `FUNCTION_SEARCH_PATH_PINNING.md` 附录 A 的函数清单。

## 4. 验收（给实现会话）

1. 集成测试（Testcontainers），两张表各一组：
   - 建 `one_test_owner` 为属主、`one_test_history_loader`（`GRANT INSERT`、不入属主角色）：迁入角色插入 `MIGRATION` 行成功，插入 `SEEDED` 行被拒；属主两者都成功；属主名不以 `_owner` 结尾的库里，迁入角色一律被拒；
   - 建 `one_test_app`（同样 `INSERT`）：`MIGRATION` 与 `SEEDED` 行都被拒；运行角色 `SET ROLE` 到它所属的其他角色后再插入，同样被拒；
   - 冒名：另建 `one_x_history_loader` 并授予运行角色，插入 `MIGRATION` 行被拒；
   - 日历：迁入角色按 §3.1 的列级授权，插入 `MIGRATION` 待确认版本、**带日明细（含 `COMPANY`、`MANUAL` 日）**、再确认，整条成功；缺登记的公司假日版本确认被拒；迁入角色确认之后再向该版本加日明细被拒；迁入角色不能撤回；运行角色确认 `MIGRATION` 版本被拒；`MIGRATION` 行直接以 `CONFIRMED` 插入、非 `MIGRATION` 行直接以 `CONFIRMED` 插入，均被拒；
   - 判定函数在属主不存在、迁入角色不存在时返回 `false` 而不是 NULL；
   - 迁入角色对 schema 有 `USAGE`、对两个判定函数有 `EXECUTE`（`PUBLIC` 默认有，部署校验核没被收回）。
2. 守护测试：`is_owner_member`、`is_migration_writer` 的 `prosecdef = false` 且 `proconfig` 固定了 `search_path`；属主拥有的 `SECURITY DEFINER` 函数不得写这两张表（现有的审计与文件服务函数都不写，守护防以后新增）。
3. 部署校验脚本（`phase1a/infra/runbooks/sql/c3_*`、`c9_*` 的 V 系列，one-ops 维护）加：运行角色与只读角色不是属主角色成员、也不在迁入角色的成员链上；迁入角色不是属主角色成员、无 `CREATE`、对两张版本表无 `DELETE`，对日历版本表的 `UPDATE` 只限 §3.1 的确认列，对编号版本表无 `UPDATE`；窗口外 `rolcanlogin = false`。
4. `DdlNamingTest` / 注册表无新增例外；表注释与列注释里「只由迁移写」「作者为空只允许属主写」改为「种子只由属主写，迁入只由属主或迁入角色写」（编号版本表基线约 182 行、日历表约 87、93 行）。`guard_rule_version_source` 一并固定 `search_path`。

## 5. 范围与不碰

- 只改触发器判定、列级授权与测试，不改表结构；基线开放期直接改两个基线文件，之后走追加迁移。
- 迁入角色本身的建立与授权、时间窗开关是 one-ops 的事（ADR-0036 第 1 条），本设计不替它写 SQL。
- 日历的「运维起草单人路径作者可空」已在 IMP-1021-NX5-03 复核记录里改判，本设计不再讨论。
