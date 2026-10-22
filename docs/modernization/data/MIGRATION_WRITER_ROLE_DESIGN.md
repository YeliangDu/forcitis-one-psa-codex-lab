# 迁入行写入通道设计（`source_kind` / 渠道为 MIGRATION 的行由谁写）

- 起草：one-data，2026-10-22。来源：`data/reviews/IMP-1021-NX5-03.md` 开放项 1 与 2；one-coordinator 2026-10-22 排序第 4 项。
- 状态：**设计，待 opus 首轮复审**；复审后随 one-1a-next-5（编号）与 one-1a-par（日历）的下一批 DDL 一起落，基线开放期直接改基线。
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
| A 角色名约定 | 触发器放行属主角色成员，或某个名字符合 `^one_[a-z0-9]+_history_loader$` 的角色的成员 | 不依赖部署时建组角色；名字由 ADR-0036 固定；建角色要 CREATEROLE，持有者本来就高于触发器 |
| B 组角色 | 部署脚本另建 `NOLOGIN` 组角色，加载账号入组，触发器查组 | 多一个要在 dev/prod 两套里同名同权的对象；角色是实例级，易错 |
| C 迁入进程用属主 | 迁入写入以属主身份 | 违反 ADR-0036 最小授权，**否决** |
| D 安全定义者函数 | 迁入进程只能调属主拥有的写入函数 | 与「迁入用例直接写目标表」冲突，**否决** |

## 3. 决定（建议）：方案 A

两处合并成同一写法，各 schema 各放一份同体函数（schema 之间不共用函数）：

```sql
CREATE FUNCTION <schema>.is_migration_writer(table_oid oid) RETURNS boolean LANGUAGE sql STABLE
SET search_path = pg_catalog, pg_temp AS $$
    SELECT pg_has_role(current_user, (SELECT relowner FROM pg_class WHERE oid = table_oid), 'MEMBER')
        OR EXISTS (SELECT 1 FROM pg_roles r WHERE r.rolname ~ '^one_[a-z0-9]+_history_loader$' AND pg_has_role(current_user, r.oid, 'MEMBER'))
$$;
```

- 两处触发器 / 函数改用它；`MEMBER` 统一（不用 `USAGE`）。运行角色 `one_<环境>_app` 与只读角色都不是属主或迁入角色的成员，仍被拒。
- 种子（`SEEDED`）仍只有属主身份（Flyway）能写：函数对 `SEEDED` 另判「只属主」，迁入角色不放行。
- 其余有迁入渠道的表（`role_versions`、`account_role_grants` 的 `MIGRATION` 渠道、W2 员工版本表）本批不加触发器：它们的迁入行在 ADR-0036 下由迁入用例经领域校验写入，库层拦截留给迁入规格评审（§14.2 M1/M3）时按「该表有无作者空例外」再定；列在 `MIGRATION_QUALITY_CHECKS.md` 的待办。
- 函数名固定 `is_migration_writer`，加进 `FUNCTION_SEARCH_PATH_PINNING.md` 附录 A 的函数清单（须 `SET search_path = pg_catalog, pg_temp`）。

## 4. 验收（给实现会话）

1. 集成测试（Testcontainers）：建角色 `one_test_history_loader`（`GRANT INSERT`、不入属主角色）→ 插入 `MIGRATION` 行成功；建角色 `one_test_app`（同样 `INSERT`）→ 插入 `MIGRATION` 行与 `SEEDED` 行都被拒；`one_test_history_loader` 插入 `SEEDED` 行被拒；属主插入 `SEEDED`、`MIGRATION` 均成功。两张表各一组。
2. 部署校验脚本（`phase1a/infra/runbooks/sql/c3_*`、`c9_*` 的 V 系列，one-ops 维护）加三项：运行角色与只读角色不是属主角色成员、也不是任何 `*_history_loader` 的成员；`*_history_loader` 不是属主角色成员、无 `CREATE`、对两张版本表无 `UPDATE`、`DELETE`；窗口外该角色 `rolcanlogin = false`。
3. `DdlNamingTest` / 注册表无新增例外；表注释里「只由迁移写」改为「只由属主（种子）与迁入角色（迁入）写」。

## 5. 范围与不碰

- 只改触发器判定与测试，不改表结构；基线开放期直接改两个基线文件，之后走追加迁移。
- 迁入角色本身的建立与授权、时间窗开关是 one-ops 的事（ADR-0036 第 1 条），本设计不替它写 SQL。
- 日历的「运维起草单人路径作者可空」已在 IMP-1021-NX5-03 复核记录里改判，本设计不再讨论。
