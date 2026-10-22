# 迁入行写入通道设计（`source_kind` / 渠道为 MIGRATION 的行由谁写）

- 起草：one-data，2026-10-22。来源：`data/reviews/IMP-1021-NX5-03.md` 开放项 1 与 2；one-coordinator 2026-10-22 排序第 4 项。
- 状态：**设计，opus 首轮复审（DAT-1022-DAT-01：2 阻断、2 高、2 中）已按意见改；待 sonnet 复核**；复审后随 one-1a-next-5（编号）与 one-1a-par（日历）的下一批 DDL 一起落，基线开放期直接改基线。
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
- 判定统一用 `MEMBER`（PG16 起它不看 `INHERIT`、`SET` 选项，运行角色 `NOINHERIT` 加 `SET ROLE` 这条路也拦得住）；不用 `USAGE`。`one_<环境>_migrator` 因 `ALTER ROLE … SET role = 属主`，会话里 `current_user` 就是属主，两种写法对它结果相同。
- **种子行（编号表 `SEEDED`）只认 `is_owner_member`**，迁入角色不放行；`MIGRATION` 行认 `is_migration_writer`。编号触发器 `guard_rule_version_source` 据此拆成两个分支。
- 部署校验（见 §4 第 2 条）核：迁入角色存在、成员只有批准的账号、运行角色与只读角色不在它的成员链上。

### 3.1 日历迁入行的确认路径（opus 阻断项）

现状：日历版本以 `PENDING_CONFIRMATION` 插入，确认与撤回的 `UPDATE` 都要再过属主判定；迁入角色按 ADR-0036 不授 `UPDATE`，运行角色有 `UPDATE` 却过不了属主判定，迁入版本会停在待确认。**决定：迁入版本直接以 `CONFIRMED` 插入。** 现有 `ck_work_calendar_versions_confirmation_columns` 已允许这种形态（`CONFIRMED` 且 `confirmed_by` 为空当且仅当 `source_kind = 'MIGRATION'`，`confirmed_at`、`decision_audit_entry_id` 必填）。改动：

- `guard_work_calendar_version_insert`：「新版本必须是 `PENDING_CONFIRMATION`」改为「`MIGRATION` 行必须是 `CONFIRMED`（且 `is_migration_writer`），其余行必须是 `PENDING_CONFIRMATION`」；
- 确认时才做的校验搬到 `MIGRATION` 行的插入守护里：公司假日或取消补班日的版本须 `company_leave_register_kind = 'REGISTERED'`；迁入行是第一版（`ck_…_migration_is_first_version`），没有前一版本要关闭；
- 确认与撤回的 `UPDATE` 路径里 `require_owner_for_migration_version` 保留（`MIGRATION` 行已确认，撤回只有属主能做，对应迁入批次回退由 one-ops 以属主执行）。

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
   - 建 `one_test_owner` 为属主、`one_test_history_loader`（`GRANT INSERT`、不入属主角色）：迁入角色插入 `MIGRATION` 行成功，插入 `SEEDED` 行被拒；属主两者都成功；
   - 建 `one_test_app`（同样 `INSERT`）：`MIGRATION` 与 `SEEDED` 行都被拒；运行角色 `SET ROLE` 到它所属的其他角色后再插入，同样被拒；
   - 冒名：另建 `one_x_history_loader` 并授予运行角色，插入 `MIGRATION` 行被拒（只认属主名推出的那一个角色）；
   - 日历：迁入角色插入的 `CONFIRMED` 迁入版本成功，缺登记的公司假日版本被拒；
   - 判定函数在属主不存在、角色不存在（`pg_has_role` 返回 NULL）时返回 `false` 而不是 NULL。
2. 守护测试：`is_owner_member`、`is_migration_writer` 的 `prosecdef = false` 且 `proconfig` 固定了 `search_path`；属主拥有的 `SECURITY DEFINER` 函数不得写这两张表（现有的审计与文件服务函数都不写，守护防以后新增）。
3. 部署校验脚本（`phase1a/infra/runbooks/sql/c3_*`、`c9_*` 的 V 系列，one-ops 维护）加：运行角色与只读角色不是属主角色成员、也不在迁入角色的成员链上；迁入角色不是属主角色成员、无 `CREATE`、对两张版本表无 `UPDATE`、`DELETE`；窗口外 `rolcanlogin = false`。
4. `DdlNamingTest` / 注册表无新增例外；表注释与列注释里「只由迁移写」「作者为空只允许属主写」改为「种子只由属主写，迁入只由属主或迁入角色写」（编号版本表注释、日历表约 93 行）。

## 5. 范围与不碰

- 只改触发器判定与测试，不改表结构；基线开放期直接改两个基线文件，之后走追加迁移。
- 迁入角色本身的建立与授权、时间窗开关是 one-ops 的事（ADR-0036 第 1 条），本设计不替它写 SQL。
- 日历的「运维起草单人路径作者可空」已在 IMP-1021-NX5-03 复核记录里改判，本设计不再讨论。
