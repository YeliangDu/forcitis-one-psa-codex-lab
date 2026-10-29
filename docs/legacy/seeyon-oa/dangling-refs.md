# A8 组织引用悬空计数（生产只读取证）

采集时间：2026-10-15T01:00:46+08:00（SQL Server `SYSDATETIMEOFFSET()`） · 会话：psa-1a-platform-bridge-reimagine · 用户决定：「现在查 (Recommended)」（intent/2026-10-14-185914）

## 结论

| 引用 | 引用行 | 涉及部门 | 悬空（引用人不在本次快照人员范围） | 其中人员已不存在 | 受影响部门 |
|---|---|---|---|---|---|
| 部门负责人（关系类型 -1726439953579618944） | 54 | 54 | **0** | 0 | 0 |
| 部门区域应收负责人（关系类型 -8187907605834545337） | 6 | 6 | **0** | 0 | 0 |
| 上级部门（PATH 去掉末 4 位后的部门） | 366 个非根部门 | — | **0** | — | — |

三类引用当前都没有悬空，B2 新增的同 schema 外键（V20261015010100）对现有生产数据不会失败。对账仍按 B2-H3 处理悬空（记 ISSUE、置空、同步继续），以防今后出现。

## 方法

- 通道：与 `docs/legacy/runtime-usage/README.md`、`A8_DEPT_NULL_FLAGS.md` 相同。阿里云云助手在生产老系统应用服务器上运行单文件 Java 程序，只读连接，隔离级别 READ UNCOMMITTED，通过三段式名称 `a8_db.dbo.*` 读取 A8 基表（`forcitis_one.v_*` 视图尚未由 DBA 建立）。
- 凭据只在服务器进程内使用，不打印、不回传；程序拒绝非 SELECT 语句；临时目录 `/tmp/one-ro-a8-dangling` 执行后删除（输出 CLEANED=yes）。
- 结果只有计数，不含姓名或 ID。
- 口径与 `SeeyonViewSql` 及 `SEEYON_VIEWS.sql` 视图定义一致：部门范围为 `PATH LIKE '00000001%'`；人员范围为 IS_INTERNAL = 1、工号非空、所属部门在范围内。

## 所用 SQL

```sql
-- d1_head_refs
SELECT COUNT(*), COUNT(DISTINCT t.unit_id), SUM(t.dangling), SUM(t.missing), COUNT(DISTINCT CASE WHEN t.dangling = 1 THEN t.unit_id END) FROM (SELECT R.OBJECTIVE0_ID AS unit_id, CASE WHEN R.SOURCE_ID IN (SELECT M.ID FROM a8_db.dbo.ORG_MEMBER M JOIN a8_db.dbo.ORG_UNIT MU ON MU.ID = M.ORG_DEPARTMENT_ID WHERE M.IS_INTERNAL = 1 AND M.CODE IS NOT NULL AND M.CODE <> '' AND MU.PATH LIKE '00000001%') THEN 0 ELSE 1 END AS dangling, CASE WHEN EXISTS (SELECT 1 FROM a8_db.dbo.ORG_MEMBER X WHERE X.ID = R.SOURCE_ID) THEN 0 ELSE 1 END AS missing FROM a8_db.dbo.ORG_RELATIONSHIP R JOIN a8_db.dbo.ORG_UNIT U ON U.ID = R.OBJECTIVE0_ID WHERE R.OBJECTIVE1_ID = -1726439953579618944 AND U.PATH LIKE '00000001%') t
-- d2_regional_ar_refs
SELECT COUNT(*), COUNT(DISTINCT t.unit_id), SUM(t.dangling), SUM(t.missing), COUNT(DISTINCT CASE WHEN t.dangling = 1 THEN t.unit_id END) FROM (SELECT R.OBJECTIVE0_ID AS unit_id, CASE WHEN R.SOURCE_ID IN (SELECT M.ID FROM a8_db.dbo.ORG_MEMBER M JOIN a8_db.dbo.ORG_UNIT MU ON MU.ID = M.ORG_DEPARTMENT_ID WHERE M.IS_INTERNAL = 1 AND M.CODE IS NOT NULL AND M.CODE <> '' AND MU.PATH LIKE '00000001%') THEN 0 ELSE 1 END AS dangling, CASE WHEN EXISTS (SELECT 1 FROM a8_db.dbo.ORG_MEMBER X WHERE X.ID = R.SOURCE_ID) THEN 0 ELSE 1 END AS missing FROM a8_db.dbo.ORG_RELATIONSHIP R JOIN a8_db.dbo.ORG_UNIT U ON U.ID = R.OBJECTIVE0_ID WHERE R.OBJECTIVE1_ID = -8187907605834545337 AND U.PATH LIKE '00000001%') t
-- d3_parent_refs
SELECT COUNT(*), SUM(t.dangling) FROM (SELECT CASE WHEN EXISTS (SELECT 1 FROM a8_db.dbo.ORG_UNIT P WHERE P.PATH = LEFT(U.PATH, LEN(U.PATH) - 4)) THEN 0 ELSE 1 END AS dangling FROM a8_db.dbo.ORG_UNIT U WHERE U.PATH LIKE '00000001%' AND LEN(U.PATH) > 8) t
-- d0_now
SELECT CONVERT(varchar(33), SYSDATETIMEOFFSET(), 126)
```
