---
卡号: OPS-1022-OPS-01
状态: 完成
负责会话: one-ops
---
# 撤号卡 OPS-1022-OPS-01：撤销老待办只读账号 psa_one_legacy_ro

- 任务名：[OPS·platform.approval] 撤销合并待办（老待办）只读账号 psa_one_legacy_ro；主线 OPS；种类 文档
- 性质：**生产变更**（老系统生产 RDS 上删账号）。卡写好后经 one-coordinator 交用户批准，**批准前不执行**。
- 依据：one-architecture 裁定 LEGACY_INBOX_CONNECTION_RULING_2026-10-22（老待办的「受控退役用途」只读账号随老待办一起撤，账号撤了再删入口）；ADR-0037 第 6 项（读取与退役）；one-1a-par 的 F 线删老待办。

## 1. 核实（2026-10-22，只读，未打印任何凭据）
- RDS SQL Server 实例 `rm-bp1690ep53t9778dp` 上账号 `psa_one_legacy_ro` **仍在**（云接口 DescribeAccounts：Normal / Available）。
- dev ECS（i-bp122xlpar8w2uylmal2）`/etc/forcitis-one/one.env` 仍保留 `FORCITIS_PLATFORM_INTEGRATION_LEGACY_INBOX_URL/_USERNAME/_PASSWORD/_DETAIL_URL_TEMPLATE`，但 `INTEGRATION_LEGACY_ENABLED=false`、`WORKFORCE_SHORT_NAME_ENABLED=false`：dev 当前不读老待办。
- 代码侧：核实命令 `ReadOnlyVerificationCommand` 的连接名 `integration-legacy-inbox` 与 `application-read-only-verification.properties` 仍引用该账号（one-1a-par 的 F2 在改，等本卡完成再删入口与 INFRA_RUNBOOK.md 第 126 行）。

## 2. 撤哪个、在哪个实例
| 对象 | 位置 | 动作 |
|---|---|---|
| 登录与数据库用户 `psa_one_legacy_ro` | 生产 RDS SQL Server `rm-bp1690ep53t9778dp`，数据库 `ruoyi-vue-pro` | 只走 RDS 云接口 DeleteAccount（`psa_one_legacy_ro`）；备选只有两句：`DROP USER [psa_one_legacy_ro]`、`DROP LOGIN [psa_one_legacy_ro]`（逐字，不用脚本、不用变量；备选须先经用户批准高权限账号 u8c）。**不得执行 `legacy_90_rollback.sql`**：它一次删两个用户、两个登录和两个视图，会误删 `one_legacy_names_ro` 与视图 |
| 视图 `dbo.v_psa_one_legacy_todo`（合并待办视图） | 同库 | **本卡不删**，撤号后随老系统退役另评（视图只读、无账号则无人可读；删视图是对老库的第二次写，单独请示） |
| dev `one.env` 的四个 `…LEGACY_INBOX_*` 变量 | dev ECS | 撤号后删除（备份 one.env，重启确认自检通过） |
| 文档 | `handoff/B-5-dba/README.md`（账号表、第 19 行）、`RESOURCES.md`、`OPS_CONTEXT.md`、`INFRA_RUNBOOK.md` 第 103、126 行、`legacy_10/20/90` 脚本与 `legacy_inbox_*.sql` | 撤号后改文本；脚本是否保留由 one-1a-par 与 one-architecture 定（回退要用） |

## 3. 影响谁
- 新系统应用：**无运行时影响**——dev 与生产都不启用老待办（`INTEGRATION_LEGACY_ENABLED=false`），合并待办只显示新待办。生产目前未部署新系统。
- 核实命令（`--connection=integration-legacy-inbox`）：撤号后该连接名无法通过校验；入口由 one-1a-par 随 F2 删除，**须在撤号后删，不能先于撤号**。
- 受控退役用途（用户 2026-10-15 的「只读退役用途」）：本账号不再有此用途；老系统退役读取改用取证账号 `one_evidence_ro`（ADR-0037 第 7 项）。
- 老系统业务：无（账号是新系统自建的专用只读账号，老系统程序不使用；撤号不触碰老表）。
- 其他会话：one-1a-par（F2 删入口与手册行）、one-architecture（裁定落地）。

## 4. 执行步骤（批准后）
1. 预检（只读）：`psa_one_legacy_ro` 近期无活动连接（取证账号查 `sys.dm_exec_sessions` 的 login_name 计数，只出个数）。有活动连接则停下报告。
2. 备份回退所需信息：本卡 §5 的重建脚本无需备份凭据（凭据按重建时新生成）。
3. 删账号前后都回读（只出有无；库内用户一项口径 2026-10-22 经 one-coordinator 改为：先看 DescribeAccounts 的 DatabasePrivileges 里 `one_legacy_names_ro` 在 `ruoyi-vue-pro` 的授权记录，只抽 AccountName、DBName、AccountPrivilege；删前看不到就退回「登录在 + 两视图在 + DescribeAccounts 两账号都在」三项，库内用户无法回读，由 DeleteAccount 只删指定账号保证，并在报告里写明）：`one_legacy_names_ro` 的数据库用户与登录、视图 `v_psa_one_legacy_todo` 与 `v_one_legacy_org_short_name`，均须在；删前 `psa_one_legacy_ro` 在。删账号：RDS 云接口 DeleteAccount（`psa_one_legacy_ro`）。删后回读 DescribeAccounts 与 `sys.server_principals` 均无 `psa_one_legacy_ro`，且上述 `one_legacy_names_ro` 与两个视图仍在；任何一项缺失立即停下报告。
4. dev `one.env` 删四个 LEGACY_INBOX 变量，重启，健康检查与自检通过。
5. 通知 one-1a-par 可删核实命令入口与手册行；改 `README.md`、`RESOURCES.md`、`OPS_CONTEXT.md` 文本；登记（ADR-0037 配置变更一行，写进 CONFIG_CHANGELOG 片段）。

## 5. 回退
- 重建账号：用 `legacy_10_apply.sql` 的 INBOX_LOGIN 部分（STEP 2 账号与授权；视图未删，故 STEP 1 视图段不需要执行）在 `ruoyi-vue-pro` 重建 `psa_one_legacy_ro` 并按其 STEP 授权与 DENY，再用 `legacy_20_verify.sql` 验证；密码重新生成、不复用旧值；dev 回填 `one.env` 变量。需要高权限账号 `u8c`（仅授权变更用，须用户批准）。
- 因应用不启用老待办，无需紧急回退；重建约十分钟。

## 6. 禁止事项
- 批准前不删账号、不改 `one.env`、不删视图；批准后只做 §4，不得执行 `legacy_90_rollback.sql`。
- 不打印、不写入任何凭据；不触碰 `one_legacy_names_ro`、`one_evidence_ro`、`one_a8_views_ro`。
- 不改老表、不写老数据。

## 7. 报告去向
- 起草完成报 one-coordinator（交用户批准）；批准并执行后向 one-coordinator 与 one-1a-par 报完成点。

## 8. 执行记录（2026-10-22，用户在 one-ops 会话直接回「批」）
- 删前：登录 psa_one_legacy_ro 与 one_legacy_names_ro 均在，两个视图在，psa_one_legacy_ro 活动连接 0；DescribeAccounts 看不到 one_legacy_names_ro 的 DatabasePrivileges（账号为 SQL 建，RDS 不展示库授权），按改后口径退回「登录在 + 两视图在 + DescribeAccounts 两账号都在」三项，库内用户一项无法回读，由 DeleteAccount 只删指定账号保证。
- 执行：RDS DeleteAccount（psa_one_legacy_ro，RequestId 01A16224-6CA5-5160-872B-CB6F32C4D3A1）。
- 删后回读：DescribeAccounts 账号 13→12，psa_one_legacy_ro 已无；one_legacy_names_ro 登录在；v_one_legacy_org_short_name、v_psa_one_legacy_todo 仍在。
- dev：one.env 备份 one.env.bak-20261022134603，删 FORCITIS_PLATFORM_INTEGRATION_LEGACY_INBOX_URL/_USERNAME/_PASSWORD/_DETAIL_URL_TEMPLATE 四项，重启 forcitis-one，active，管理端口 8081 健康 200，启动日志无错误。
