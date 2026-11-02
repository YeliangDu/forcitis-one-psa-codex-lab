# B-5 交接包：A8 与老系统库的只读视图和专用账号（给 DBA）

> #742：`tools/` 下的执行脚本 `b5_legacy_run.py`、`b5_read_all_run.py`、`b5_evidence_run.py`、`set_env.py` 已执行完毕并删除，原文见 `git show 7d3f2d2ea:docs/modernization/phase1a/infra/handoff/B-5-dba/tools/<文件>`；只读取证入口 `tools/ops/dba/b5_evidence_scan.py` 共用的通道函数在 `tools/ops/dba/b5_remote.py`。下文提到这些脚本处是执行记录。

收件人：A8 数据库 DBA、老系统 RDS（SQL Server）DBA。时点：dev 部署前。

**执行状态（2026-10-15，经用户授权以 RDS 高权限账号执行）**：A8 部分已完成并校验（one-ops-1a）；老系统部分首次在 STEP 1 失败（排序规则冲突，未建出任何对象），视图修正后由 one-ops 重跑完成：apply 与 verify（云助手 t-hz0701f88r8dedc）全部符合验收标准，在途待办 196、部门 41；两个账号写入 `one.env`（t-hz0701f8mylkpa8，dev 连接按用户决定加 `trustServerCertificate=true`，`A8_DB_URL` 同步）；真实登录（t-hz0701f8s68gikg）两个账号都能读自己的视图，读基表、读人员表、读令牌表、读对方视图、写基表、经视图删除、建表、读 `a8_db` 8 项全部被拒（229 / 4405 / 262 / 916）；两台服务器临时目录与临时密钥已删除。执行工具在 `tools/`（`b5_legacy_run.py` 编排；`OneSql.java` 执行 sqlcmd 风格脚本，`one_time_secret` 列只输出 RSA-OAEP 密文；`set_env.py` 从 stdin 写 `one.env`）。实测要点：

- A8 与老系统库在同一 RDS SQL Server 实例 `rm-bp1690ep53t9778dp`（2016 标准版）上，A8 的 `a8_db` 不在 A8 应用服务器本机；`A8_DB_URL` 指向该实例内网地址。
- 该实例未开 SSL：模板里的 `encrypt=true`（不带 `trustServerCertificate`）会让应用的证书校验失败，须先决定开 SSL（实例要重启）或 dev 期接受 `trustServerCertificate=true`。
- 老系统库列排序规则不一：`ACT_RU_TASK.ID_` 为 `Chinese_PRC_90_CI_AI`，`bpm_task_ext.task_id` 为 `Chinese_PRC_CI_AS`；合并待办视图的连接已加 `COLLATE DATABASE_DEFAULT`（正本与 apply 同步修改）。
- 口令由 apply 的 STEP 3 在 SQL Server 上生成，执行程序只输出用 ECS 上临时 RSA 公钥加密后的密文，在 ECS 上解密写入 `one.env`；原文没有出现在本机、聊天、命令或日志中。

## 要做什么

FORCITIS one 只读这两个库（ADR-0037：全库只读；运行时仍经下列专用视图读取，视图冻结），**不写任何数据，不改任何表**。

| 库 | 新增对象 | 专用账号 | 应用侧变量（运维写入 one.env，DBA 只交口令） |
|---|---|---|---|
| A8（`a8_db`） | schema `forcitis_one` 与 4 个视图：`v_org_unit`、`v_org_relationship`、`v_org_post`、`v_org_member` | 1 个，默认名 `one_a8_views_ro` | `A8_DB_URL`、`A8_DB_USERNAME`、`A8_DB_PASSWORD` |
| 老系统（`ruoyi-vue-pro`） | `dbo.v_psa_one_legacy_todo`（合并待办） | `psa_one_legacy_ro`（2026-10-22 已撤，OPS-1022-OPS-01；视图仍在） | `FORCITIS_PLATFORM_INTEGRATION_LEGACY_INBOX_URL`、`_USERNAME`、`_PASSWORD` |
| 老系统（`ruoyi-vue-pro`） | `dbo.v_one_legacy_org_short_name`（部门简称） | `one_legacy_names_ro` | `LEGACY_DB_RO_URL`、`LEGACY_DB_RO_USERNAME`、`LEGACY_DB_RO_PASSWORD` |

账号名可改（脚本用 sqlcmd 变量 `READER_LOGIN` / `INBOX_LOGIN` / `NAMES_LOGIN`），改了要告诉运维，用户名写进 one.env。

## 执行顺序

1. 预约维护窗口（只建视图和账号，不锁业务表，不影响 A8 和老系统使用）。
2. A8：`a8-10-apply.sql` → `a8-20-verify.sql`，结果导出留档。
3. 老系统：`legacy-10-apply.sql` → `legacy-20-verify.sql`，结果导出留档。
4. 白名单（控制台）：两库都放行 ECS 内网地址 `172.16.89.86`，只放这一个地址。
5. 口令交接：apply 脚本 STEP 3 在服务器上随机生成口令，**只显示一次**。DBA 通过约定的安全渠道（当面或密码管理器共享，不经聊天、邮件、工单）交给运维，运维写入 `/etc/forcitis-one/one.env`（`0640 root:forcitis`），以后迁到 KMS（`docs/ops/runbooks/kms.md`）。
6. 回报 one-coordinator：两份 verify 结果、实际账号名、白名单已加。**不回报口令。**

## 2026-10-15 改为全库只读（取代下一节的最小化授权）

依据：用户「新系统可以读任何旧系统数据」，澄清选「两者都是」（迁移取证与应用运行时都可以），随后「修改权限」；对凭据列答复「全部可读，不排除」（均经 one-coordinator 转达）。变更清单经 one-coordinator 确认后由 one-ops 执行。

| 账号 | 变更前 | 变更后 |
|---|---|---|
| a8_db `one_a8_views_ro` | `forcitis_one` schema SELECT；`dbo` schema DENY SELECT、VIEW DEFINITION 与全部写类 | 库级 GRANT SELECT；`dbo` 的 DENY SELECT、DENY VIEW DEFINITION 撤销；库级 DENY INSERT、UPDATE、DELETE、ALTER、EXECUTE、REFERENCES、CREATE TABLE / VIEW / PROCEDURE / FUNCTION / SCHEMA；schema 级原有写类 DENY 保留 |
| 老系统库 `psa_one_legacy_ro`、`one_legacy_names_ro` | 各自一个视图的 SELECT | 库级 GRANT SELECT；库级 DENY 同上；原视图授权保留 |

- 脚本：`read-all/a8-apply.sql`、`read-all/legacy-apply.sql`（各一个事务），回滚 `read-all/a8-rollback.sql`、`read-all/legacy-rollback.sql`；执行与验证用的 `b5_read_all_run.py` 已删（见文首）。三个账号都不加入任何数据库角色。
- 执行记录（云助手）：前后授权导出与变更 t-hz0702doo7c0xkw：a8_db 授权 16 行 → 26 行，老系统库 4 行 → 28 行，与清单逐项一致，角色成员 0 行不变。登录验证 t-hz0702dpgo0dukg：三个账号以 one.env 中现有口令真实登录（口令在新 ECS 上以临时公钥加密转交，本会话只经手密文），基表、元数据与原视图均可读（只出计数）；UPDATE、DELETE 被拒（229），CREATE TABLE 被拒（262），allowed=0。两台服务器临时目录已清理。
- **与应用启动校验冲突**：下一节所述「每个只读连接能 SELECT 的对象恰好一个视图」（SECURITY_INVARIANTS I-1）与 `ReadOnlyGuard`、列白名单（`seeyon-view-columns.yaml` 已并入 `external-read-registry.yaml`）仍按最小化授权写。改为全库只读后应用启动校验会拒绝；运行时读取方式与校验口径由 ADR-0037 定，首次部署前须由代码会话改校验（已由 ADR37-1 按 ADR-0037 第 4 项 (a)–(f) 重写，见下文「验收标准」）。`legacy-*-deny.sql` 中「读基表、读令牌表、读另一视图」三类期望已不成立，以 `read-all/*-login-*.sql` 为准。
- 账号是否合并由 ADR-0037 定，应用配置暂不变。

## 2026-10-15 取证账号与服务器级权限（ADR-0037）

- **取证账号 `one_evidence_ro`**（ADR-0037 第 5 项第 4 条）：一个登录（`docs/data/evidence/dba-handoff/evidence-login.sql`，在 master 上下文执行，口令在服务器端生成、只经新 ECS 临时公钥加密的密文），a8_db 与老系统库各一个用户（`docs/data/evidence/dba-handoff/evidence-user-*.sql`），授权形状与运行时账号相同：库级 SELECT、库级 DENY 写 / DDL / EXECUTE / REFERENCES、不入角色。口令在新 ECS `/etc/forcitis-one/evidence.env`（root 0600），C-8 轮换表一行。执行与验证用的 `b5_evidence_run.py`（已删，见文首；云助手 t-hz07037ww67qsjk 变更，t-hz07037y4vsbfnk 登录验证）：服务器级授权只多出本登录的 CONNECT SQL，两库只多出本用户的 13 行授权；两库基表与元数据可读，UPDATE、DELETE 被拒（229），CREATE TABLE 被拒（262），allowed=0。回滚 `docs/data/evidence/dba-handoff/evidence-rollback.sql`。
- **凭据列拒绝清单**：`docs/data/evidence/dba-handoff/credential-columns.csv`（`tools/ops/dba/b5_evidence_scan.py` 以取证账号扫 `INFORMATION_SCHEMA.COLUMNS`，按 ADR-0037 第 3 项第 4 条的词筛出后人工复核）：credential 92 列（A8 75、老系统 17），review 25 列（A8 的 AppKey、`KEY_VALUE` 等，可能是标识而非凭据，实现 PR 逐列定）。工作流引擎的流程定义键、业务键、内容哈希、幂等键、`secret_level` 等已剔除。
- **风险登记（用户「接受并登记风险，加校验与轮换 (Recommended)」）**：RDS SQL Server 平台给所有登录（服务器角色 public 等）授予服务器级 ALTER ANY LOGIN、ALTER ANY LINKED SERVER、ALTER ANY CONNECTION、ALTER ANY EVENT SESSION、ALTER SERVER STATE、ALTER TRACE（及 VIEW SERVER STATE、VIEW ANY DATABASE），用户与高权限账号都不能 GRANT / DENY 服务器级权限（库级 DENY 报 4631，master 上 DENY 报 4613）。新系统只读口令泄露时，持有人可能借 ALTER ANY LOGIN 改其他非 sysadmin 登录的口令。缓解：口令只放新 ECS、按 C-8 90 天轮换；应用启动校验服务器级有效权限等于下列平台基线，逐项写死、多出即拒绝。实例白名单不动（用户「老系统先不管」）。不在生产上验证改口令（2026-10-15 补充，H-1 / M-3 取证）：老系统应用数据源 u8c（RDS 高权限账号）不是 sysadmin（`IS_SRVROLEMEMBER` 为 0，可见固定服务器角色只有 processadmin），只读基线的 ALTER ANY LOGIN 理论上能改它的口令；是否有平台保护触发器拦截 ALTER LOGIN：**看不到，无法判定**——用户批准破例以 u8c 只读查（「我刚才看错了，你可以去测试触发器」），`sys.server_triggers` 为 0 行，但 u8c 的服务器级有效权限与只读平台基线完全相同（9 项，无 VIEW ANY DEFINITION、CONTROL SERVER；VIEW SERVER STATE 不含服务器级对象元数据），即本账号能用的任何登录都看不到服务器触发器；u8c 属 processadmin，不属 setupadmin 与 sysadmin。
- **有效权限平台基线**（四个账号真实登录 `fn_my_permissions` 导出，完全相同）：
  - 服务器级：ALTER ANY CONNECTION、ALTER ANY EVENT SESSION、ALTER ANY LINKED SERVER、ALTER ANY LOGIN、ALTER SERVER STATE、ALTER TRACE、CONNECT SQL、VIEW ANY DATABASE、VIEW SERVER STATE。
  - 库级：CONNECT、KILL DATABASE CONNECTION、SELECT、SHOWPLAN、VIEW ANY COLUMN ENCRYPTION KEY DEFINITION、VIEW ANY COLUMN MASTER KEY DEFINITION、VIEW DATABASE STATE（A8 账号在 a8_db 同样没有单列的 VIEW DEFINITION）。
  - 角色只有 public；`HAS_DBACCESS` 为真的库：自己的库加 master、msdb、rdscore、tempdb（guest），取证账号为两库加这四个。

## 权限为什么这样给（最小化，2026-10-15 前）

**本节已被 ADR-0037 取代**（只给视图 SELECT、A8 基表 DENY、老系统必须两个账号、列最小都不再成立），只有「不加入任何数据库角色」保留，现由应用校验 (c) 断言。现行形状见上一节与 `read-all/`。

- **只给视图 SELECT**：A8 账号只对 `forcitis_one` schema 有 SELECT；老系统两个账号各只对一个视图有 SELECT。视图与基表同属 `dbo`，靠所有权链读取，账号对基表没有任何权限。A8 基表另加 DENY。
- **不加入任何数据库角色**（`db_datareader` 等）：加入就等于读全库；而且应用检查授权时看不到经角色得到的权限。
- **老系统必须是两个账号**：应用对每个连接启动时都检查「能 SELECT 的对象恰好只有这一个视图」（SECURITY_INVARIANTS I-1）。一个账号读两个视图，两边都会拒绝启动。
- **列最小**：A8 视图只含组织与身份列，与 `backend/architecture/external-read-registry.yaml` 中 a8_db 视图列完全相等；不含生日、银行卡、合同与金额类字段。老系统视图只含待办 7 列和部门 2 列；令牌表（`system_oauth2_*`）不授权。
- **口令到期关闭、按期轮换**：服务账号口令到期会让同步中断，所以 `CHECK_EXPIRATION = OFF`，改由 DBA 每 90 天轮换（平台约定 §6）。这一点与 `seeyon-views.sql` 草稿注释里的 `CHECK_EXPIRATION = ON` 不同，是有意的改动。

## 回滚

- A8：先由运维关闭 `INTEGRATION_SEEYON_VIEWS_ENABLED` 并重启应用，再执行 `a8-90-rollback.sql`。
- 老系统：先由运维清空 `FORCITIS_PLATFORM_INTEGRATION_LEGACY_INBOX_URL`、关闭 `INTEGRATION_LEGACY_ENABLED` 与 `WORKFORCE_SHORT_NAME_ENABLED` 并重启，再执行 `legacy-90-rollback.sql`。合并待办此时只显示新系统待办。

## 验收标准

**ADR-0037 后的验收**（取代下面 V1 中的白名单授权含义、V3.1–V3.3 与老系统「恰好 1 行」「令牌表全为 0」）：每个运行时账号与取证账号以真实登录执行 `read-all/readonly-check.sql`，(a)–(f) 全部等于上一节的平台基线；应用在 dev 启动时三条只读连接的校验通过（ADR-0037 第 4 项，无关闭开关），拒绝时日志按 (a)–(f) 报出违反项。视图冻结，A8 视图列仍与 `backend/architecture/external-read-registry.yaml` 中 a8_db 视图列完全相等。以下为 2026-10-15 前的原验收，留作执行记录：

- `a8-20-verify.sql`：V1 共 33 列且与白名单逐列一致；V2 0 行；V3.1 恰好 4 个视图；V3.2 全为 0；V3.3 不含应用 `ReadOnlyGuard.FORBIDDEN` 中任一项（实测为 `CONNECT` 与 SQL Server 默认授给 public 的 `VIEW ANY COLUMN ENCRYPTION KEY DEFINITION`、`VIEW ANY COLUMN MASTER KEY DEFINITION`；以该账号真实登录时另见 `SHOWPLAN`、`VIEW DATABASE STATE`、`ALTER ANY DATABASE EVENT SESSION`、`KILL DATABASE CONNECTION`，来自实例级授权，也不在禁止清单内）；V3.5 0 行。
- `legacy-20-verify.sql`：V2 0 行；两个账号的「能 SELECT 的对象」各恰好 1 行，且是自己的视图；令牌表全为 0。
- 应用在 dev 启动时三个只读连接的启动校验都通过（日志无 `may read more than` 与 `excessive` 字样）。

## 与正本的关系

视图正文从 `docs/data/legacy-views/seeyon-views.sql`、`legacy-inbox-view.sql`、`legacy-org-names-view.sql` 逐字复制。正本改动后人工对照并同步本目录（原比对脚本 `check_view_sync.py` 已于 #742 删除，无替代）。正本里 `CREATE LOGIN … PASSWORD = N'<<…>>'` 的写法**不要直接执行**：那会建出一个口令为占位文字的账号；请用本目录的 apply 脚本。
