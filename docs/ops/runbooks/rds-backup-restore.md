# C-2 剧本：RDS 删除保护、备份策略与恢复演练

依据：`docs/architecture/proposals/phase1-a-platform-conventions.md` §5（RPO ≤ 5 分钟、RTO ≤ 1 小时，用户已采纳）。时点：配置在 dev 前；首次演练在 1a 出口前，之后每季度一次。性质：配置属对外变更；演练会**新建临时实例（产生费用）**，执行前须用户确认。

## 一、配置（dev 前）

### 执行前确认点
- 用户确认备份保留天数：dev 期 ≥ 7 天；生产建议 30 天，另每月一份长期快照保留 1 年（会增加备份存储费用）。

### 步骤（RDS 控制台，pgm-bp13vak0sew3m003）
1. 「基本信息」→ 开启「实例释放保护 / 删除保护」。
2. 「备份恢复」→「备份设置」：
   - 数据备份：每日，时段避开 04:30（事件归档清理作业）与月结窗口，建议 02:00–03:00；
   - 保留天数：按确认值；
   - 日志备份：开启，保留天数同上（RPO 依赖日志备份与按时间点恢复）；
   - 「按时间点恢复」：确认可用。
3. 跨地域备份（可选，生产前再定，不在 1a 范围）。

### 实测（2026-10-15，one-ops-1a，只读核对）
- 删除保护：API 返回 `OperationDenied.PayType`——包年包月实例不支持删除保护（该开关只对按量付费实例有效）；实例已开自动续费（1 个月），到期不续才会释放。步骤 1 对本实例不适用。
- 备份策略：每日物理全量 02:00–03:00 **UTC**（北京时间 10:00–11:00，工作时间内），数据与日志备份各保留 7 天，日志备份已开。
- 日志备份实际按约 500 MB 一包、约每 2.5 小时上传一次（最近 10 个文件），即实例整体丢失时按时间点恢复最多丢约 2.5 小时，**达不到 RPO ≤ 5 分钟**。高可用备库同步复制覆盖单节点故障，但不覆盖误删误改后的按时间点恢复。缩短上传间隔会增加日志备份存储（可能超出免费额度），按用户「可产生费用的先不做」只记录，生产前决定。
- RTO ≤ 1 小时须靠恢复演练证明（要临时实例，产生费用，本次不做）。

### 执行记录（2026-10-15，one-ops）

| 项 | 结果 |
|---|---|
| 备份窗口 | 原为 UTC 02:00–03:00（北京 10:00–11:00，工作时间）。one-coordinator 定改到北京 02:00–03:00（免费）。改前核对：实例近 7 天 CPU 峰值夜间（01:00–04:00）0.1 %、全天 0.3 %，IT 门户无夜间批处理迹象。已改为 UTC 18:00–19:00；数据备份保留 7 天、日志备份开启、日志备份保留 7 天不变。注意与可维护时间段（UTC 18:00–22:00）重叠 |
| 续费 | 自动续费已开（每次 1 个月），实例 2026-10-18 到期；账户现金余额充足、无信用额度依赖。用户「续费不用管」：续费由用户自理，运维不核对 |
| 付费项 P-5 | 日志备份已开启；备份总量约 36 GB，`PaidBackupSize=0`，未超免费额度 |
| RPO | 日志备份频率不能经接口设置（`LogBackupFrequency` 为空、`archive_timeout` 不可改），实测 7 天 71 个日志文件、平均约 2.4 小时一个。用户决定「你在控制台查频率选项，查不到再接受 (Recommended)」：用户在控制台「备份恢复 › 备份设置」查。**查到**则按 RPO ≤ 5 分钟设置；**查不到**则平台约定改为「主机故障不丢数据（高可用）；误操作恢复精度取决于日志备份频率（实测约 2.4 小时），生产前再评估」，并列为生产前复核项 |
| RPO 结论（2026-10-15） | 官方文档：`LogBackupFrequency` 仅适用于 SQL Server，PostgreSQL 没有日志备份频率选项（[ModifyBackupPolicy](https://help.aliyun.com/zh/rds/developer-reference/api-rds-2014-08-15-modifybackuppolicy)）。按用户决定「你在控制台查频率选项，查不到再接受 (Recommended)」接受现状，平台约定改为：**主机故障不丢数据（高可用）；误操作恢复精度取决于日志备份频率（实测约 2.4 小时），生产前再评估**。生产前可选：PostgreSQL 高频快照备份（`BackupInterval`，云盘实例、开启秒级备份，按快照存储计费，[高频快照](https://help.aliyun.com/en/rds/apsaradb-rds-for-postgresql/use-the-high-frequency-snapshot-backup-feature-for-an-apsaradb-rds-for-postgresql-instance)），由用户届时决定 |
| SSL | 实例 SSL 已开启（阿里云签发，2027-09-18 到期），dev 已用 `verify-full`（C-3）。证书私钥曾进入会话记录，生产前维护窗口重签（C-3 执行记录「安全事件」） |

> **注意：不得调用会返回密钥的接口。** `rds DescribeDBInstanceSSL` 的返回含实例证书私钥；查询云资源时在进程内只取所需字段，不打印原始返回。

### 校验
- 控制台显示删除保护已开、日志备份已开；「备份列表」次日出现第一份数据备份；「可恢复时间范围」连续。

### 回滚
关闭对应开关即可（删除保护建议始终开启）。

## 二、恢复演练（出口前首次，之后每季度）

### 执行前确认点
- 用户同意新建一台临时按量实例（与源同可用区、最小可用规格），演练结束当天释放。
- 临时实例不接任何对外流量；冒烟进程关闭所有作业与出站（见步骤 2），它在临时实例上的少量内部写入（会话清理等）随实例释放丢弃，不影响源实例。
- 选定恢复时间点 T（例如演练开始前 10 分钟），并在 T 之前于 dev 库做一次可识别的写入作为水位（例如由组织数据管理员保存一次工作日历，记下审计时间）。

### 步骤
1. 记录 `t0`（开始时间）。控制台「备份恢复」→「按时间点恢复」→ 新建实例，时间点选 T，网络与白名单同源实例（只加 172.16.89.86）。
2. 实例可用后记录 `t1`。在 ECS 上用临时 env 文件启动一次应用的只读冒烟（不改 `/etc/forcitis-one/one.env`）：
   ```bash
   # /root/drill.env：复制 one.env，只改 SPRING_DATASOURCE_URL 指向临时实例，并设 FORCITIS_JOBS_ENABLED=false（总开关，#228 起生效；之前的发布包不能用于演练）、WORKFORCE_SYNC_ENABLED=false、
   # WECOM_ENABLED=false、APPROVAL_DEADLINE_ENABLED=false、FLOWABLE_ASYNC_EXECUTOR=false、WORKFORCE_SHORT_NAME_ENABLED=false、EVENT_PUBLICATION_JOBS_ENABLED=false、SERVER_PORT=18080、MANAGEMENT_SERVER_PORT=18081
   # 口令经 EnvironmentFile 传入，不出现在命令行参数
   install -o root -g root -m 0600 /etc/forcitis-one/one.env /root/drill.env   # 然后编辑上面几项
   systemd-run --unit=one-drill --uid=forcitis --gid=forcitis -p EnvironmentFile=/root/drill.env \
     /usr/bin/java -Duser.timezone=UTC -jar /data/forcitis-one/current/forcitis-one.jar
   ```
   另用只读角色执行关键表核对：
   ```sql
   SELECT 'user accounts' t, count(*) FROM platform_identity.user_accounts
   UNION ALL SELECT 'employees', count(*) FROM organization_workforce.employees
   UNION ALL SELECT 'approval requests', count(*) FROM platform_approval.approval_requests
   UNION ALL SELECT 'audit entries', count(*) FROM platform_audit.audit_entries;
   SELECT max(created_at) FROM platform_audit.audit_entries;   -- 应 ≥ 水位写入时间、≤ T
   ```
   表名以当时的迁移为准，执行前用 `\dt platform_*.*` 核对。
3. 冒烟：本机 `curl -s 127.0.0.1:18081/actuator/health` 为 UP；登录与待办在 dev 页面不切换，只核对数据与健康。记录 `t2`。
4. `systemctl stop one-drill`；控制台释放临时实例；删除 `/root/drill.env`。记录 `t3`。

### RPO / RTO 记录模板（每次演练一份，存公司内部，不进仓库个人信息）

| 项 | 值 |
|---|---|
| 演练日期 / 执行人 / 见证人 | |
| 源实例 / 临时实例 ID | |
| 选定恢复时间点 T | |
| 水位写入时间 W（T 之前最后一条可识别写入） | |
| 恢复后能看到的最后写入时间 L | |
| **实际 RPO** = T − L（目标 ≤ 5 分钟） | |
| t0 开始 / t1 实例可用 / t2 冒烟通过 | |
| **实际 RTO** = t2 − t0（目标 ≤ 1 小时） | |
| 关键表行数（源 @T 附近 / 恢复后） | |
| 偏差与原因 | |
| 改进项 | |
| t3 临时实例已释放（是 / 否） | |

### 验收
实际 RPO ≤ 5 分钟、RTO ≤ 1 小时；不达标时把原因与改进项交 one-coordinator，由用户决定是否调整目标或方案。

### 回滚
演练不改源实例；异常时直接释放临时实例。
