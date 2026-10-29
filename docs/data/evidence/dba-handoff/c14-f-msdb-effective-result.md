# C-14 (f) H-1′ 实测结果（RDS SQL Server 2016，2026-10-16，one-ops）

方法：在 msdb 上下文以各账号自己的真实登录跑 `evidence/c14_f_msdb_effective.sql`（只读，只出类别与计数）。账号：one_a8_views_ro、one_legacy_names_ro、psa_one_legacy_ro（三个正式账号），one_evidence_ro（样本账号）。用户 2026-10-16 在 one-ops 会话批准正式账号这一次的只读查询。云助手 t-hz0706cwgbmivi8，凭据在 ECS 上从 one.env / evidence.env 读取，不进会话；临时目录用完已删（回读 CLEAN）。

**四个账号的结果逐项完全相同，没有报错。**

| 项 | 结果 |
|---|---|
| 1 六项有效权限 + IMPERSONATE | CONTROL、ALTER、ALTER ANY ROLE、ALTER ANY USER、ALTER ANY SCHEMA、CREATE PROCEDURE、IMPERSONATE（dbo）全部为 0 |
| 2 fn_my_permissions(NULL,'DATABASE') 全集 | CONNECT、KILL DATABASE CONNECTION、SHOWPLAN、VIEW ANY COLUMN ENCRYPTION KEY DEFINITION、VIEW ANY COLUMN MASTER KEY DEFINITION、VIEW DATABASE STATE，各 1（共 6 项） |
| 3 令牌主体的授权，去掉「对象级 GRANT SELECT / EXECUTE」后 | 本人用户：DATABASE GRANT CONNECT 1、OBJECT_OR_COLUMN DENY SELECT 1；public：DATABASE DENY 的 ALTER、BACKUP DATABASE、BACKUP LOG、CREATE FUNCTION、CREATE PROCEDURE、CREATE TABLE、CREATE VIEW 各 1，DATABASE GRANT 的 VIEW ANY COLUMN ENCRYPTION KEY DEFINITION、VIEW ANY COLUMN MASTER KEY DEFINITION 各 1。无任何其他 class 与 permission |
| 4 对象级 EXECUTE GRANT 的对象 | SQL_STORED_PROCEDURE 101 个、SQL_SCALAR_FUNCTION 4 个，**全部不带 EXECUTE AS**（0 个带） |
| 5 dbo schema 的有效权限 | 无（`fn_my_permissions('dbo','SCHEMA')` 空）；IMPERSONATE(dbo 用户) 为 0（见第 1 项） |
| 6 令牌里 type='ROLE' | public、SQLAgentOperatorRole、SQLAgentReaderRole、SQLAgentUserRole 各 1，没有其他角色 |
| 7 令牌内主体拥有的物 | 只有三个 SQL Agent 角色各拥有同名 schema 1 个；本人用户、public 不拥有任何 schema、对象、角色；令牌里没有 guest（本人用户存在），所以也没有 guest 的 schema；没有「other」 |

说明：第 4 项是令牌各主体（本人、public、三个 SQL Agent 角色）合计的对象级 EXECUTE GRANT 对象。新检查若只许这四种角色、这五类授权、这四对拥有关系，**不会误拒这四个账号**。
