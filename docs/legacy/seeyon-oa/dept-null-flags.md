# A8 部门停用标志空值核查（RULE-355）

日期：2026-10-14 · 会话：psa-1a-platform-bridge-reimagine · 派工：psa-coordinator · 用户授权原话：「查一下空值有多少，同意只读查询」

## 结论

**生产 A8 中，`ORG_UNIT` 的 `IS_DELETED`、`IS_INTERNAL`、`IS_ENABLE` 三个标志的 NULL 行数都是 0。** 不论是全表（368 行），还是同步范围内（`LEFT(PATH,8)='00000001'`，367 行），结果都一样。

- RULE-355 担心的「NULL 按三值逻辑落入『有效』」的情况，在当前数据中不存在。若改为「NULL 视为停用」，受影响的部门、A8 在职人员和 老系统在职用户都是 0。
- 当前结论（NULL 视为有效，另出同步例外报告）不会改变任何部门的状态。该结论作为防御规则保留即可：新系统同步时若遇到 NULL，按有效处理，同时写入例外报告并告警。其他三种做法（A8 源头补齐、按其他线索推断、本地覆盖表）目前没有可作用的对象。
- 同步范围内实际有效的部门是 40 个（`IS_DELETED=0、IS_INTERNAL=1、IS_ENABLE=1`），加 1 个 `Account` 根节点。老系统 `system_dept` 中有效部门 41 个，与之吻合。

## 数据

| 项 | 全表 | 同步范围 |
|---|---|---|
| 总行数 | 368 | 367（Department 366、Account 1） |
| `IS_DELETED` 为 NULL | 0 | 0 |
| `IS_INTERNAL` 为 NULL | 0 | 0 |
| `IS_ENABLE` 为 NULL | 0 | 0 |
| 任一为 NULL | 0 | 0 |

同步范围内三个标志的取值组合：

| IS_DELETED | IS_INTERNAL | IS_ENABLE | TYPE | 行数 | 老判定为停用 | 「NULL 视为停用」判定为停用 |
|---|---|---|---|---|---|---|
| 1 | 1 | 0 | Department | 291 | 291 | 291 |
| 0 | 1 | 1 | Department | 40 | 0 | 0 |
| 0 | 1 | 0 | Department | 31 | 31 | 31 |
| 0 | 0 | 1 | Department | 4 | 4 | 4 |
| 0 | 1 | 1 | Account | 1 | 0 | 0 |

两种判定逐行一致，差异为 0。

- 含 NULL 的部门清单（派工第 2 项）：0 行。
- 这些部门在 老系统 `system_dept` 中的状态（第 3 项）：0 行。
- 影响对照（第 4 项）：直接受影响的部门 0 个，其子树也是 0 个；受影响的 A8 在职人员 0 人，老系统在职用户 0 人。作为参照，老系统当前在职用户 305 人，有效部门 41 个。

## 方法

- **通道**：沿用评估期的生产只读通道（`docs/legacy/runtime-usage/README.md`）。通过阿里云云助手在生产老系统应用服务器上运行单文件 Java 程序，以只读连接加 `READ UNCOMMITTED` 隔离级别访问 `ruoyi-vue-pro`，经三段式名称 `a8_db.dbo.*` 读取 A8 表。
- **凭据**：只在服务器进程内使用，不打印、不回传。
- **只读保证**：程序拒绝一切非 `SELECT`/`WITH` 语句，以及含写操作关键字的语句。服务器上的临时目录执行后即删除。
- **个人信息**：结果只含计数，不含姓名。
- **口径**：
  - 同步范围、停用判定与老系统 `A8DeptMapper.selectSyncScopedDepts` 一致。
  - A8 在职人员的判定与 `A8UserMapper.selectInternalUsers` 一致：`IS_INTERNAL=1`、工号非空，且 `ORG_PRINCIPAL.LOGIN_NAME` 非空（RULE-363）。
  - 老系统在职用户：`system_users.deleted=0 AND status=0`。

## 所用 SQL

```sql
-- 01_totals_all
SELECT COUNT(*) AS total, SUM(CASE WHEN U.IS_DELETED IS NULL THEN 1 ELSE 0 END) AS is_deleted_null, SUM(CASE WHEN U.IS_INTERNAL IS NULL THEN 1 ELSE 0 END) AS is_internal_null, SUM(CASE WHEN U.IS_ENABLE IS NULL THEN 1 ELSE 0 END) AS is_enable_null, SUM(CASE WHEN (U.IS_DELETED IS NULL OR U.IS_INTERNAL IS NULL OR U.IS_ENABLE IS NULL) THEN 1 ELSE 0 END) AS any_null FROM a8_db.dbo.ORG_UNIT U
-- 02_totals_scope
SELECT COUNT(*) AS total, SUM(CASE WHEN U.IS_DELETED IS NULL THEN 1 ELSE 0 END) AS is_deleted_null, SUM(CASE WHEN U.IS_INTERNAL IS NULL THEN 1 ELSE 0 END) AS is_internal_null, SUM(CASE WHEN U.IS_ENABLE IS NULL THEN 1 ELSE 0 END) AS is_enable_null, SUM(CASE WHEN (U.IS_DELETED IS NULL OR U.IS_INTERNAL IS NULL OR U.IS_ENABLE IS NULL) THEN 1 ELSE 0 END) AS any_null FROM a8_db.dbo.ORG_UNIT U WHERE LEFT(U.PATH,8)='00000001'
-- 03_totals_scope_by_type
SELECT ISNULL(U.TYPE,'(null)') AS type, COUNT(*) AS total, SUM(CASE WHEN (U.IS_DELETED IS NULL OR U.IS_INTERNAL IS NULL OR U.IS_ENABLE IS NULL) THEN 1 ELSE 0 END) AS any_null FROM a8_db.dbo.ORG_UNIT U WHERE LEFT(U.PATH,8)='00000001' GROUP BY U.TYPE ORDER BY 2 DESC
-- 04_combos_scope
SELECT ISNULL(CAST(U.IS_DELETED AS varchar(8)),'NULL') AS is_deleted, ISNULL(CAST(U.IS_INTERNAL AS varchar(8)),'NULL') AS is_internal, ISNULL(CAST(U.IS_ENABLE AS varchar(8)),'NULL') AS is_enable, ISNULL(U.TYPE,'(null)') AS type, COUNT(*) AS n, SUM((CASE WHEN U.IS_DELETED=1 OR U.IS_INTERNAL<>1 OR U.IS_ENABLE<>1 THEN 1 ELSE 0 END)) AS old_disabled, SUM((CASE WHEN ISNULL(U.IS_DELETED,1)=1 OR ISNULL(U.IS_INTERNAL,0)<>1 OR ISNULL(U.IS_ENABLE,0)<>1 THEN 1 ELSE 0 END)) AS new_disabled_if_null_disabled FROM a8_db.dbo.ORG_UNIT U WHERE LEFT(U.PATH,8)='00000001' GROUP BY U.IS_DELETED, U.IS_INTERNAL, U.IS_ENABLE, U.TYPE ORDER BY 5 DESC
-- 05_null_list
SELECT TOP 50 CAST(U.ID AS varchar(32)) AS id, U.NAME AS name, P.NAME AS parent_name, ISNULL(U.TYPE,'(null)') AS type, ISNULL(CAST(U.IS_DELETED AS varchar(8)),'NULL') AS is_deleted, ISNULL(CAST(U.IS_INTERNAL AS varchar(8)),'NULL') AS is_internal, ISNULL(CAST(U.IS_ENABLE AS varchar(8)),'NULL') AS is_enable, (CASE WHEN U.IS_DELETED=1 OR U.IS_INTERNAL<>1 OR U.IS_ENABLE<>1 THEN 1 ELSE 0 END) AS old_disabled, (CASE WHEN ISNULL(U.IS_DELETED,1)=1 OR ISNULL(U.IS_INTERNAL,0)<>1 OR ISNULL(U.IS_ENABLE,0)<>1 THEN 1 ELSE 0 END) AS new_disabled, (SELECT COUNT(DISTINCT M.ID) FROM a8_db.dbo.ORG_MEMBER M JOIN a8_db.dbo.ORG_PRINCIPAL OPP ON OPP.MEMBER_ID=M.ID WHERE M.ORG_DEPARTMENT_ID=U.ID AND M.IS_INTERNAL=1 AND ISNULL(OPP.LOGIN_NAME,'')<>'' AND ISNULL(M.CODE,'')<>'') AS a8_active_members, (SELECT COUNT(*) FROM a8_db.dbo.ORG_UNIT C WHERE C.PATH LIKE U.PATH+'%' AND LEN(C.PATH)=LEN(U.PATH)+4) AS child_units, ISNULL(CAST(d.status AS varchar(8)),'(absent)') AS psa_status, ISNULL(CAST(d.deleted AS varchar(8)),'(absent)') AS psa_deleted, (SELECT COUNT(*) FROM system_users su WHERE su.dept_id=U.ID AND su.deleted=0 AND su.status=0) AS psa_active_users FROM a8_db.dbo.ORG_UNIT U LEFT JOIN a8_db.dbo.ORG_UNIT P ON LEN(U.PATH)>8 AND P.PATH=LEFT(U.PATH,LEN(U.PATH)-4) LEFT JOIN system_dept d ON d.id=U.ID WHERE LEFT(U.PATH,8)='00000001' AND (U.IS_DELETED IS NULL OR U.IS_INTERNAL IS NULL OR U.IS_ENABLE IS NULL) ORDER BY U.PATH
-- 06_null_psa_state
SELECT ISNULL(CAST(d.status AS varchar(8)),'(absent)') AS psa_status, ISNULL(CAST(d.deleted AS varchar(8)),'(absent)') AS psa_deleted, COUNT(*) AS n FROM a8_db.dbo.ORG_UNIT U LEFT JOIN system_dept d ON d.id=U.ID WHERE LEFT(U.PATH,8)='00000001' AND (U.IS_DELETED IS NULL OR U.IS_INTERNAL IS NULL OR U.IS_ENABLE IS NULL) GROUP BY d.status, d.deleted ORDER BY 3 DESC
-- 07_impact_direct
SELECT COUNT(*) AS depts_newly_disabled, SUM(x.a8) AS a8_active_members, SUM(x.psa) AS psa_active_users, SUM(x.psa_dept_active) AS psa_depts_currently_active FROM (SELECT U.ID, (SELECT COUNT(DISTINCT M.ID) FROM a8_db.dbo.ORG_MEMBER M JOIN a8_db.dbo.ORG_PRINCIPAL OPP ON OPP.MEMBER_ID=M.ID WHERE M.ORG_DEPARTMENT_ID=U.ID AND M.IS_INTERNAL=1 AND ISNULL(OPP.LOGIN_NAME,'')<>'' AND ISNULL(M.CODE,'')<>'') AS a8, (SELECT COUNT(*) FROM system_users su WHERE su.dept_id=U.ID AND su.deleted=0 AND su.status=0) AS psa, CASE WHEN d.id IS NOT NULL AND d.deleted=0 AND d.status=0 THEN 1 ELSE 0 END AS psa_dept_active FROM a8_db.dbo.ORG_UNIT U LEFT JOIN system_dept d ON d.id=U.ID WHERE LEFT(U.PATH,8)='00000001' AND (CASE WHEN U.IS_DELETED=1 OR U.IS_INTERNAL<>1 OR U.IS_ENABLE<>1 THEN 1 ELSE 0 END)=0 AND (CASE WHEN ISNULL(U.IS_DELETED,1)=1 OR ISNULL(U.IS_INTERNAL,0)<>1 OR ISNULL(U.IS_ENABLE,0)<>1 THEN 1 ELSE 0 END)=1) x
-- 08_impact_subtree
SELECT COUNT(*) AS subtree_units_incl_self, SUM(x.a8) AS a8_active_members_subtree, SUM(x.psa) AS psa_active_users_subtree FROM (SELECT C.ID, (SELECT COUNT(DISTINCT M.ID) FROM a8_db.dbo.ORG_MEMBER M JOIN a8_db.dbo.ORG_PRINCIPAL OPP ON OPP.MEMBER_ID=M.ID WHERE M.ORG_DEPARTMENT_ID=C.ID AND M.IS_INTERNAL=1 AND ISNULL(OPP.LOGIN_NAME,'')<>'' AND ISNULL(M.CODE,'')<>'') AS a8, (SELECT COUNT(*) FROM system_users su WHERE su.dept_id=C.ID AND su.deleted=0 AND su.status=0) AS psa FROM a8_db.dbo.ORG_UNIT C WHERE LEFT(C.PATH,8)='00000001' AND EXISTS (SELECT 1 FROM a8_db.dbo.ORG_UNIT U WHERE LEFT(U.PATH,8)='00000001' AND (CASE WHEN U.IS_DELETED=1 OR U.IS_INTERNAL<>1 OR U.IS_ENABLE<>1 THEN 1 ELSE 0 END)=0 AND (CASE WHEN ISNULL(U.IS_DELETED,1)=1 OR ISNULL(U.IS_INTERNAL,0)<>1 OR ISNULL(U.IS_ENABLE,0)<>1 THEN 1 ELSE 0 END)=1 AND C.PATH LIKE U.PATH+'%')) x
-- 09_psa_active_users_total
SELECT COUNT(*) AS psa_active_users, (SELECT COUNT(*) FROM system_dept WHERE deleted=0 AND status=0) AS psa_active_depts FROM system_users WHERE deleted=0 AND status=0
```
