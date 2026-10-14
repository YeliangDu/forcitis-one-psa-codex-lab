# B-5 交接包：A8 与老系统库的只读视图和专用账号（给 DBA）

收件人：A8 数据库 DBA、老系统 RDS（SQL Server）DBA（人选见 `DECISIONS_A.md` A-2）。时点：dev 部署前。

**执行状态（2026-10-15，会话 one-ops-1a，经用户授权以 RDS 高权限账号执行）**：A8 部分已完成并校验；老系统部分在 STEP 1 失败（排序规则冲突，未建出任何对象），视图已修正，待复核后重跑。实测要点：

- A8 与老系统库在同一 RDS SQL Server 实例 `rm-bp1690ep53t9778dp`（2016 标准版）上，A8 的 `a8_db` 不在 A8 应用服务器本机；`A8_DB_URL` 指向该实例内网地址。
- 该实例未开 SSL：模板里的 `encrypt=true`（不带 `trustServerCertificate`）会让应用的证书校验失败，须先决定开 SSL（实例要重启）或 dev 期接受 `trustServerCertificate=true`。
- 老系统库列排序规则不一：`ACT_RU_TASK.ID_` 为 `Chinese_PRC_90_CI_AI`，`bpm_task_ext.task_id` 为 `Chinese_PRC_CI_AS`；合并待办视图的连接已加 `COLLATE DATABASE_DEFAULT`（正本与 apply 同步修改）。
- 口令由 apply 的 STEP 3 在 SQL Server 上生成，执行程序只输出用 ECS 上临时 RSA 公钥加密后的密文，在 ECS 上解密写入 `one.env`；原文没有出现在本机、聊天、命令或日志中。

## 要做什么

FORCITIS one 只通过专用视图读这两个库，**不写任何数据，不改任何表**。

| 库 | 新增对象 | 专用账号 | 应用侧变量（运维写入 one.env，DBA 只交口令） |
|---|---|---|---|
| A8（`a8_db`） | schema `forcitis_one` 与 4 个视图：`v_org_unit`、`v_org_relationship`、`v_org_post`、`v_org_member` | 1 个，默认名 `one_a8_views_ro` | `A8_DB_URL`、`A8_DB_USERNAME`、`A8_DB_PASSWORD` |
| 老系统（`ruoyi-vue-pro`） | `dbo.v_psa_one_legacy_todo`（合并待办） | `psa_one_legacy_ro` | `FORCITIS_PLATFORM_APPROVAL_LEGACY_URL`、`_USERNAME`、`_PASSWORD` |
| 老系统（`ruoyi-vue-pro`） | `dbo.v_one_legacy_org_short_name`（部门简称） | `one_legacy_names_ro` | `LEGACY_DB_RO_URL`、`LEGACY_DB_RO_USERNAME`、`LEGACY_DB_RO_PASSWORD` |

账号名可改（脚本用 sqlcmd 变量 `READER_LOGIN` / `INBOX_LOGIN` / `NAMES_LOGIN`），改了要告诉运维，用户名写进 one.env。

## 执行顺序

1. 预约维护窗口（只建视图和账号，不锁业务表，不影响 A8 和老系统使用）。
2. A8：`a8_10_apply.sql` → `a8_20_verify.sql`，结果导出留档。
3. 老系统：`legacy_10_apply.sql` → `legacy_20_verify.sql`，结果导出留档。
4. 白名单（控制台）：两库都放行 ECS 内网地址 `172.16.89.86`，只放这一个地址。
5. 口令交接：apply 脚本 STEP 3 在服务器上随机生成口令，**只显示一次**。DBA 通过约定的安全渠道（当面或密码管理器共享，不经聊天、邮件、工单）交给运维，运维写入 `/etc/forcitis-one/one.env`（`0640 root:forcitis`），以后迁到 KMS（`runbooks/C-8-kms.md`）。
6. 回报 one-coordinator：两份 verify 结果、实际账号名、白名单已加。**不回报口令。**

## 权限为什么这样给（最小化）

- **只给视图 SELECT**：A8 账号只对 `forcitis_one` schema 有 SELECT；老系统两个账号各只对一个视图有 SELECT。视图与基表同属 `dbo`，靠所有权链读取，账号对基表没有任何权限。A8 基表另加 DENY。
- **不加入任何数据库角色**（`db_datareader` 等）：加入就等于读全库；而且应用检查授权时看不到经角色得到的权限。
- **老系统必须是两个账号**：应用对每个连接启动时都检查「能 SELECT 的对象恰好只有这一个视图」（SECURITY_INVARIANTS I-1）。一个账号读两个视图，两边都会拒绝启动。
- **列最小**：A8 视图只含组织与身份列，与 `backend/architecture/seeyon-view-columns.yaml` 完全相等；不含生日、银行卡、合同与金额类字段。老系统视图只含待办 7 列和部门 2 列；令牌表（`system_oauth2_*`）不授权。
- **口令到期关闭、按期轮换**：服务账号口令到期会让同步中断，所以 `CHECK_EXPIRATION = OFF`，改由 DBA 每 90 天轮换（平台约定 §6）。这一点与 `SEEYON_VIEWS.sql` 草稿注释里的 `CHECK_EXPIRATION = ON` 不同，是有意的改动。

## 回滚

- A8：先由运维关闭 `INTEGRATION_SEEYON_VIEWS_ENABLED`、`WORKFORCE_SYNC_ENABLED` 并重启应用，再执行 `a8_90_rollback.sql`。
- 老系统：先由运维清空 `FORCITIS_PLATFORM_APPROVAL_LEGACY_URL`、关闭 `INTEGRATION_LEGACY_ENABLED` 与 `WORKFORCE_SHORT_NAME_ENABLED` 并重启，再执行 `legacy_90_rollback.sql`。合并待办此时只显示新系统待办。

## 验收标准

- `a8_20_verify.sql`：V1 共 33 列且与白名单逐列一致；V2 0 行；V3.1 恰好 4 个视图；V3.2 全为 0；V3.3 不含应用 `ReadOnlyGuard.FORBIDDEN` 中任一项（实测为 `CONNECT` 与 SQL Server 默认授给 public 的 `VIEW ANY COLUMN ENCRYPTION KEY DEFINITION`、`VIEW ANY COLUMN MASTER KEY DEFINITION`；以该账号真实登录时另见 `SHOWPLAN`、`VIEW DATABASE STATE`、`ALTER ANY DATABASE EVENT SESSION`、`KILL DATABASE CONNECTION`，来自实例级授权，也不在禁止清单内）；V3.5 0 行。
- `legacy_20_verify.sql`：V2 0 行；两个账号的「能 SELECT 的对象」各恰好 1 行，且是自己的视图；令牌表全为 0。
- 应用在 dev 启动时三个只读连接的启动校验都通过（日志无 `may read more than` 与 `excessive` 字样）。

## 与正本的关系

视图正文从 `phase1a/SEEYON_VIEWS.sql`、`LEGACY_INBOX_VIEW.sql`、`LEGACY_ORG_NAMES_VIEW.sql` 逐字复制。正本改动后运行 `python3 docs/modernization/phase1a/infra/handoff/B-5-dba/check_view_sync.py`，不一致就同步本目录。正本里 `CREATE LOGIN … PASSWORD = N'<<…>>'` 的写法**不要直接执行**：那会建出一个口令为占位文字的账号；请用本目录的 apply 脚本。
