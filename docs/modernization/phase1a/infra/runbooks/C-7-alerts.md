# C-7 剧本：四类告警配置与 dev 演练

依据：`proposals/PHASE1A_PLATFORM_CONVENTIONS.md` §4（告警表与「按级别分通道」）、INFRA_RUNBOOK E-13、§9。时点：出口前（出口条件 D-6）。性质：控制台配置属对外变更，执行前须用户确认；接收人见 A-5。

规则文件：`files/alert-rules.yaml`（Prometheus 规则格式，导入 ARMS Prometheus 告警）。

## 规则定义

| 类 | 级别 | 指标（Prometheus 名） | 阈值 | 通知 |
|---|---|---|---|---|
| 登录失败率 | P1 | `forcitis_platform_identity_login_total{outcome}` | 5 分钟失败率 > 20 % 且失败 ≥ 20 | `ONE_ALERT_P1` |
| 未完成事件发布 / 死信 | P2 | `forcitis_platform_integration_event_publications_stale`、`…_exhausted`、`forcitis_platform_integration_deliveries_dead_letters` | 任一 > 0（stale 持续 1 分钟） | `ONE_ALERT_P2` |
| 组织同步延迟或中止 | P2 | `forcitis_organization_workforce_sync_lag_seconds`、`…_sync_held_runs` | lag > 600 秒，或 held > 0 | `ONE_ALERT_P2` |
| 作业失败 | P3 | `tasks_scheduled_execution_seconds_count{outcome="ERROR"}`、`forcitis_platform_approval_deadline_failed_total` | 15 分钟内 > 0 | 看板；汇总进 `ONE_ALERT_P2` |
| 附加：日历未维护 | P2 | `forcitis_organization_attendance_calendar_unmaintained_total` | 1 小时内 > 0 | `ONE_ALERT_P2` |
| 附加：应用不可达 | P1 | `up{job="forcitis-one"}` | = 0 持续 2 分钟 | `ONE_ALERT_P1` |

另配 SLS 日志告警（兜底，防指标链路断）：Logstore 中 `log.level: ERROR and message: ALERT*`，5 分钟内 ≥ 1 条 → `ONE_ALERT_P2`。

## 证书到期（A-1 改选 Let's Encrypt 的前提）

Let's Encrypt 已不发到期提醒邮件，ACME 账号也没填邮箱（session-log one-1a-infra-b4），所以由本机检查兜底。指标链路（ARMS）未接通前也能工作。

| 项 | 内容 |
|---|---|
| 脚本 | `scripts/one_cert_check.sh` → ECS `/usr/local/sbin/one_cert_check.sh`（root 0755） |
| 调度 | `files/one-cert-check.service`、`files/one-cert-check.timer` → `/etc/systemd/system/`；每日 09:10（随机延迟 10 分钟，`Persistent=true`） |
| 检查 | ① 续期后的证书文件剩余天数；② nginx 实际下发的证书剩余天数（经 127.0.0.1:443，防止续期成功但没 reload）；③ 两者不一致；④ `certbot-renew.service` 上次结果；⑤ `certbot-renew.timer` 是否启用 |
| 级别 | 剩余 < 20 天 → P2；< 7 天或读不到证书 → P1；③④⑤ → P2。阈值可用环境变量 `ONE_CERT_WARN_DAYS` / `ONE_CERT_CRIT_DAYS` 调 |
| 输出 | 一行 JSON 写 journal（标签 `one-cert-check`）与 `/data/forcitis-one/logs/ops/cert-check.json`；告警行 `message` 以 `ALERT P1` / `ALERT P2` 开头，可被下面的 SLS 兜底规则捕获（Logtail 采集路径加上 `logs/ops/*.json`） |
| 推送 | `/etc/forcitis-one/ops-alert.env`（root 0600，键 `ONE_ALERT_P1_WEBHOOK`、`ONE_ALERT_P2_WEBHOOK`；P1 未配时退回 P2）存在时直接推企业微信群机器人；Webhook 经 stdin 交给 curl，不出现在进程列表 |
| 退出码 | 0 正常；1 告警；2 检查本身失败。systemd 中单元为 failed 也能从 `systemctl --failed` 看到 |

已验证（2026-10-15，云助手 t-hz0701ank3nqvb4，在 ECS 临时目录运行、用后删除，未安装定时器）：正常 89 天 → 退出 0；阈值调到 200 天分别得到 P2 / P1、退出 1；证书文件不存在 → P1、退出 2。

安装（执行前须确认；与 C-4 同一通道）：

```bash
install -o root -g root -m 0755 one_cert_check.sh /usr/local/sbin/one_cert_check.sh
install -o root -g root -m 0644 one-cert-check.service one-cert-check.timer /etc/systemd/system/
systemctl daemon-reload && systemctl enable --now one-cert-check.timer
systemctl start one-cert-check.service; journalctl -u one-cert-check.service -n 3 --no-pager
```

群机器人建好后（A-5）：`install -o root -g root -m 0600 /dev/null /etc/forcitis-one/ops-alert.env`，经云助手（`KeepCommand=false`）写入两个 Webhook；用 `ONE_CERT_WARN_DAYS=200 /usr/local/sbin/one_cert_check.sh` 演练一次，群里收到即可。

回滚：`systemctl disable --now one-cert-check.timer`，删除两个单元文件与脚本。

## 落地方案：哪些免费、哪些要花钱

依据 2026-10-15 只读查询（`forcitis-production`，cn-hangzhou）：云监控只有一个联系人组「云账号报警联系人」、一条老系统 A8 的 RDS 探测规则，没有站点监控；ARMS 只有云监控 2.0 默认工作区自动建的三个 remote-write 实例，没有抓取 FORCITIS one 的任务；SLS 只有默认工作区、ARMS 托管、门户与操作审计四个 Project，没有 FORCITIS one 的 Project。新 ECS 未绑实例 RAM 角色。

| 告警 | 落在哪里 | 费用 | 状态 |
|---|---|---|---|
| 证书到期、续期失败 | 本机脚本 + 企业微信群机器人 | 免费 | 脚本已备好，待确认安装 |
| ECS CPU、内存、磁盘（/data 使用率 > 85 %）、实例状态 | 云监控基础监控阈值告警。2026-10-15 查询：云监控插件 `Status=running`，但操作系统级监控 `OsMonitorStatus=stopped`、`LoongCollectorStatus=NotEnabled`，内存与磁盘使用率须先在 C-4 中启用 | 免费；短信按云监控免费额度，超出另计 | 待 A-5 联系人后配置 |
| RDS CPU、连接数、存储使用率（共用实例，阈值须与 it_portal 商定） | 云监控 RDS 基础指标告警 | 免费 | 同上 |
| 应用不可达（`up == 0`） | ARMS Prometheus 抓取 | 按写入量计费（**付费项 P-1**） | 免费替代：本机 systemd `OnFailure=` + 群机器人，或下面的 SLS 规则 |
| 四类业务告警（登录失败率、未完成发布 / 死信、组织同步、作业失败）与日历未维护 | ARMS Prometheus 抓 `/actuator/prometheus` 的自定义指标 | 按写入量计费（**付费项 P-1**）；指标约几十个时序，量很小，单价以费用中心为准 | 出口条件 D-6 必需 |
| 兜底：应用日志中的 `ALERT*` 行 | SLS Logstore + 日志告警 | SLS 写入、存储、索引按量计费（**付费项 P-2**）；Logstore 保留 30 天（E-17） | E-11 Logtail 安装后可配 |
| 外部可用性探测（从公网访问 `https://one.forcitis.com`） | 云监控云拨测 | 付费（**付费项 P-3**） | 建议先不做：本机检查 + `up` 已覆盖进程与证书；网络层出问题时用户会先发现 |

付费项（汇总到 one-coordinator，由用户决定；未决定前不开）：
- **P-1 ARMS Prometheus 抓取 FORCITIS one 指标**：出口条件 D-6 四类告警演练必需，没有免费替代能做比率类告警。建议：dev 部署后开，先看一周的实际写入量与费用再定生产。
- **P-2 SLS 日志 Project 与日志告警**：E-11 本就要求，日志检索也靠它。建议：与 P-1 同时开，Logstore 保留 30 天。
- **P-3 云拨测**：建议暂不开。

## 通知渠道变量（只在 ARMS / SLS 控制台中配置，值不进仓库）

| 变量 | 内容 | 来源 |
|---|---|---|
| `ONE_ALERT_P1_WEBHOOK` | 「FORCITIS one 运维」企业微信群机器人 Webhook（含 key，**机密**，存 KMS） | A-5 |
| `ONE_ALERT_P1_SMS` | P1 短信接收人（ARMS 联系人，按人登记手机号，不进仓库） | A-5 |
| `ONE_ALERT_P2_WEBHOOK` | P2 企业微信群机器人 Webhook（机密） | A-5 |
| `ONE_ALERT_ONCALL` | 值班表（谁、何时） | A-5 |

ARMS 中建两个通知策略：`ONE_ALERT_P1`（群机器人 + 短信，24 × 7）、`ONE_ALERT_P2`（群机器人，工作时间；夜间合并到次日 09:00）。

## 执行前确认点

1. A-5 已定（群、短信接收人、值班）；群机器人已建，Webhook 已存 KMS。
2. E-12 已完成，ARMS 中能查询到 `forcitis_` 指标；规则中的指标名先在 ARMS 查询页逐个查到再导入。
3. 作业失败依赖 Spring 定时任务观测指标 `tasks_scheduled_execution_*`：先在 dev 的 prometheus 端点确认存在；不存在则回报代码会话补指标。Flowable 死信任务数目前没有指标（`CROSSCHECK.md` 第 13 条），本规则先不含。

## 演练（dev，逐类触发并记录）

| 类 | 故障注入 | 期望 | 恢复 |
|---|---|---|---|
| 登录失败率 | 用脚本对 `POST /api/v1/platform/sessions:wecom-login` 发 25 次无效 code（来源为测试机） | 5 分钟内 P1 群与短信收到 | 停止脚本 |
| 未完成发布 | 在 dev 临时把 `WECOM_BASE_URL` 指向不可达地址并触发一条审批通知，等待超过 10 分钟 | stale > 0 告警；用尽后 exhausted / 死信告警 | 改回地址并重启；按 INFRA_RUNBOOK §9 处理残留 |
| 组织同步 | dev 中把 `A8_DB_URL` 指向不可达地址并重启（或 DBA 临时撤销视图账号 CONNECT） | 10 分钟后 lag 告警 | 恢复配置 |
| 缩水保护 | 在 dev 把 `A8_ROOT_PATH` 临时改为一个很小的子单位路径 | held > 0 告警，组织数据管理员收到企业微信 | 恢复配置，放行或等待自动恢复 |
| 作业失败 | dev 中撤销 `one_dev_app` 对某作业表的权限（由 DBA 执行）或停 RDS 白名单 1 分钟 | 作业失败告警 | 恢复 |
| 应用不可达 | `systemctl stop forcitis-one` | 2 分钟后 P1 | 启动 |

演练记录：每类一行（注入时间、告警到达时间、渠道、处理人、恢复时间），存公司内部，摘要交 one-coordinator 作为 D-6 证据。

## 回滚

停用或删除对应告警规则；通知策略保留。
