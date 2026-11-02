# C-7 剧本：四类告警配置与 dev 演练

依据：`docs/architecture/proposals/phase1-a-platform-conventions.md` §4（告警表与「按级别分通道」）、INFRA_RUNBOOK E-13、§9。时点：出口前（出口条件 D-6）。性质：控制台配置属对外变更，执行前须用户确认；接收人见 A-5。

规则文件：`files/alert-rules.yaml`（Prometheus 规则格式，导入 ARMS Prometheus 告警）。

## 规则定义

| 类 | 级别 | 指标（Prometheus 名） | 阈值 | 通知 |
|---|---|---|---|---|
| 登录失败率 | P1 | `forcitis_platform_identity_login_total{outcome}` | 5 分钟失败率 > 20 % 且失败 ≥ 20 | `ONE_ALERT_P1` |
| 未完成事件发布 / 死信 | P2 | `forcitis_platform_integration_event_publications_stale`、`…_exhausted`、`forcitis_platform_integration_deliveries_dead_letters` | 任一 > 0（stale 持续 1 分钟） | `ONE_ALERT_P2` |
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
| 推送 | 告警时调用 `/usr/local/sbin/one_alert_send`（`scripts/one_alert_send.py`）：`/etc/forcitis-one/ops-alert.env`（root 0600）有 `ONE_ALERT_WEBHOOK` 时发到「FORCITIS one 运维」群机器人（A-5 现行渠道），否则以 FORCITIS one 应用消息发给 `ONE_ALERT_WECOM_USERID`；直接调企业微信接口，不依赖业务服务，不打印凭据 |
| 退出码 | 0 正常；1 告警；2 检查本身失败。systemd 中单元为 failed 也能从 `systemctl --failed` 看到 |

已安装（2026-10-15，one-coordinator 在授权内同意；云助手 t-hz0701b833ib7r4）：脚本 SHA256 校验通过，首次运行 `Result=success`，定时器下次 09:14。此前的验证（云助手 t-hz0701ank3nqvb4，在 ECS 临时目录运行、用后删除）：正常 89 天 → 退出 0；阈值调到 200 天分别得到 P2 / P1、退出 1；证书文件不存在 → P1、退出 2。

安装（执行前须确认；与 C-4 同一通道）：

```bash
install -o root -g root -m 0755 one_cert_check.sh /usr/local/sbin/one_cert_check.sh
install -o root -g root -m 0644 one-cert-check.service one-cert-check.timer /etc/systemd/system/
systemctl daemon-reload && systemctl enable --now one-cert-check.timer
systemctl start one-cert-check.service; journalctl -u one-cert-check.service -n 3 --no-pager
```

**推送已接通（2026-10-15）**：群机器人 Webhook 经运维机上的安全输入窗口（隐藏输入，不经聊天、不落本机）由云助手 SendFile（f-hz0701pprrskhz4）写入 `ops-alert.env`（root 0600）；`one_alert_send` 与新版 `one_cert_check.sh` 已安装；测试消息（t-hz0701ppx0ee0w0）企业微信返回成功，用户在群里确认收到。告警演练：`ONE_CERT_WARN_DAYS=200 /usr/local/sbin/one_cert_check.sh`（会向群里发一条 P2）。

回滚：`systemctl disable --now one-cert-check.timer`，删除两个单元文件与脚本。

## ClamAV 健康检查（OPS-1021-OPS-02 第 6 条）

| 项 | 内容 |
|---|---|
| 脚本 | `scripts/one_clamav_check.sh` → ECS `/usr/local/sbin/one_clamav_check.sh`（root 0755）；`files/one-clamav-check.service`、`files/one-clamav-check.timer` → `/etc/systemd/system/`；开机 2 分钟后起、之后每 5 分钟 |
| 检查 | ① clamd 经 socket 的 PING；② clamd 回报的病毒库日期（库龄）；③ `forcitis-one-freshclam` 是否运行 |
| 级别 | clamd 不应答（检测不可用，上传一律拒收）→ P1；库龄 > 48 小时（`ONE_CLAMAV_MAX_AGE_HOURS`）、读不到库日期、freshclam 未运行 → P2 |
| 输出 | 一行 JSON 写 journal（标签 `one-clamav-check`）与 `/data/forcitis-one/logs/ops/clamav-check.json`；告警行以 `ALERT P1` / `ALERT P2` 开头，被 SLS 兜底规则捕获 |
| 推送 | 经 `one_alert_send`；级别变化时发，持续告警每 6 小时（`ONE_CLAMAV_REMIND_HOURS`）重发，恢复只写日志；状态在 `/var/lib/one-clamav-check/last` |
| 演练 | `ONE_CLAMAV_DRILL=1` 时推送级别用 TEST、消息前缀「演练」 |
| 应用侧 | 健康检查端点把「库过期」当「不可用」由应用侧实现（卡第 6 条）；本脚本是运维侧告警 |
| 回滚 | `systemctl disable --now one-clamav-check.timer`，删两个单元文件与脚本 |

dev 演练（2026-10-21，ECS i-bp122xlpar8w2uylmal2，沿用既有 C-7 群机器人通道，三条「演练」消息）：脚本 SHA256 回读一致；停 clamd → `ALERT P1`、恢复后 OK；库龄阈值压到 0 小时 → `ALERT P2`、恢复 OK；停 freshclam → `ALERT P2`、恢复 OK；推送无失败记录。真实库龄超限需等库过期，无法在不改库的前提下制造，以阈值覆盖演练代替；真实场景由「freshclam 未运行」与库龄两条共同覆盖。

## 落地方案：哪些免费、哪些要花钱

依据 2026-10-15 只读查询（`forcitis-production`，cn-hangzhou）：云监控只有一个联系人组「云账号报警联系人」、一条老系统 A8 的 RDS 探测规则，没有站点监控；ARMS 只有云监控 2.0 默认工作区自动建的三个 remote-write 实例，没有抓取 FORCITIS one 的任务；SLS 只有默认工作区、ARMS 托管、门户与操作审计四个 Project，没有 FORCITIS one 的 Project。新 ECS 未绑实例 RAM 角色。

| 告警 | 落在哪里 | 费用 | 状态 |
|---|---|---|---|
| 证书到期、续期失败 | 本机脚本 + 企业微信群机器人 | 免费 | 脚本已备好，待确认安装 |
| ECS CPU、内存、磁盘（/data 使用率 > 85 %）、实例状态 | 云监控基础监控阈值告警。2026-10-15 查询：云监控插件 `Status=running`，`OsMonitorStatus` 字段显示 stopped，但 2026-10-15 核对 `acs_ecs_dashboard` 的 `memory_usedutilization`、`diskusage_utilization`（含数据盘 `/dev/nvme1n1`）每分钟都有上报，以指标为准 | 免费；短信按云监控免费额度，超出另计 | 待 A-5 联系人后配置 |
| RDS CPU、连接数、存储使用率（共用实例，阈值须与 it_portal 商定） | 云监控 RDS 基础指标告警 | 免费 | 同上 |
| 应用不可达（`up == 0`） | ARMS Prometheus 抓取 | 按写入量计费（**付费项 P-1**） | 免费替代：本机 systemd `OnFailure=` + 群机器人，或下面的 SLS 规则 |
| 四类业务告警（登录失败率、未完成发布 / 死信、作业失败）与日历未维护 | ARMS Prometheus 抓 `/actuator/prometheus` 的自定义指标 | 按写入量计费（**付费项 P-1**）；指标约几十个时序，量很小，单价以费用中心为准 | 出口条件 D-6 必需 |
| 兜底：应用日志中的 `ALERT*` 行 | SLS Logstore + 日志告警 | SLS 写入、存储、索引按量计费（**付费项 P-2**）；Logstore 保留 30 天（E-17） | E-11 Logtail 安装后可配 |
| 外部可用性探测（从公网访问 `https://one.forcitis.com`） | 云监控云拨测 | 付费（**付费项 P-3**） | 建议先不做：本机检查 + `up` 已覆盖进程与证书；网络层出问题时用户会先发现 |

付费项（汇总到 one-coordinator，由用户决定；未决定前不开）：
- **P-1 ARMS Prometheus 抓取 FORCITIS one 指标**：出口条件 D-6 四类告警演练必需，没有免费替代能做比率类告警。建议：dev 部署后开，先看一周的实际写入量与费用再定生产。
- **P-2 SLS 日志 Project 与日志告警**：E-11 本就要求，日志检索也靠它。建议：与 P-1 同时开，Logstore 保留 30 天。
- **P-3 云拨测**：建议暂不开。

## 付费项：用户决定、月费估算与现状（2026-10-15）

用户决定（one-coordinator 转达）：现在开通「ECS 数据盘每日快照留 7 天」「ARMS 应用监控」「SLS 日志与日志告警」「RDS 日志备份超额部分」；P-3 云拨测不开。条件：任一项月费估算超过 300 元、或合计超过 800 元先停；ARMS / SLS 只开通与配置，应用未部署前不接业务数据；Logstore 保留 30 天；快照只作用于数据盘；RDS 日志备份按 RPO ≤ 5 分钟。

单价取自阿里云帮助中心（以控制台购买页为准）：SLS 按写入数据量 0.4 元/GB，含 30 天存储，告警等增值功能不另收费（[SLS 按写入数据量计费](https://help.aliyun.com/zh/sls/billing-per-amount-of-data-written)）；Prometheus 按写入量标准版 0.4 元/GB，每月 50 GB 免费额度按账号共享、分配到首次接入的地域（[Prometheus 计费](https://help.aliyun.com/zh/arms/prometheus-monitoring/product-overview/billing-description/)）。

| 项 | 用量依据（只读查询） | 月费估算 | 现状 |
|---|---|---|---|
| P-4 ECS 快照 | `/data` 已用约 4 GB；增量快照 | < 5 元 | **已存在**：自动快照策略 `forcitis-one-daily`（sp-bp1b4n9pvexztyhyfrof，2026-10-14 创建，每日 03:00，保留 7 天），绑定数据盘 d-bp122xlpar8w2uyjp4w8 **和系统盘** d-bp122xlpar8w2uyjp4w7；数据盘尚无快照（今晚 03:00 首次）。与「只作用于数据盘」不一致，未改，报 one-coordinator |
| P-5 RDS 日志备份 | 实例 pgm-bp13vak0sew3m003 已开日志备份、保留 7 天；当前备份总量约 36 GB（数据 4.4 GB、日志 31.8 GB），`PaidBackupSize=0`，尚未超出免费额度 | 当前 0 元；超额按量 | **无须开通**。RPO：本实例日志备份频率不可经接口设置（`LogBackupFrequency` 为空，`archive_timeout` 不在可改参数中）；7 天 71 个日志文件，平均约 2.4 小时一个，日志备份本身达不到 RPO ≤ 5 分钟。高可用备库同步复制覆盖单机故障；误操作恢复的 RPO 以日志备份为准。报 one-coordinator |
| P-1 ARMS | 一个 Spring Boot 应用约 1,500 个时序、15 秒抓一次，估每天 < 1 GB，每月 < 30 GB | 0–12 元（免费额度被门户占用时按 0.4 元/GB） | 未开：E-12 属 C-4（运维子代理） |
| P-2 SLS | dev 应用日志 + nginx 日志估每天 0.2–1 GB | 3–12 元 | 未开：E-11 属 C-4（运维子代理） |
| 合计 | | **约 40 元/月以内** | 远低于 300 / 800 元门槛 |

回退：P-4 解绑或删除自动快照策略（已生成的快照按保留期自动删除，或手工删除）；P-5 恢复原备份策略；P-1 删除抓取任务（Prometheus 实例停写即不再计费）；P-2 删除 Logtail 采集配置与 Logstore（数据随之删除）。

## P-1 / P-2 开通记录（2026-10-15，用户在 one-ops 会话中确认「两项都开 (Recommended)」）

| 资源 | 设置 |
|---|---|
| SLS Project | `forcitis-one-observability`（cn-hangzhou；数据冗余为接口默认的同城冗余） |
| Logstore | `app`（应用 JSON 日志 `/data/forcitis-one/logs/*.json`）、`nginx`（`/var/log/nginx/one.access.json` JSON 与 `one.error.log`）、`ops`（`/data/forcitis-one/logs/ops/*.json`，证书检查）；自动分裂；保留期 `nginx`、`ops` 180 天（2026-10-15 由 30 天改，见下），`app` 30 天；计费为按使用功能（本账号未开通按写入量计费模式，接口返回 `ServiceNotOpened`；one-coordinator 认可现状，不开） |
| 索引 | `app`、`ops`：全文加 `level`、`logger`、`message`、`correlationId`；`nginx`：全文 |
| 指标库 | `metrics`（Prometheus，保留 30 天）：LoongCollector `service_prometheus` 每 15 秒抓 `127.0.0.1:8081/actuator/prometheus`（job `forcitis-one`）。未用 ARMS Prometheus 实例：管理端口只听回环，VPC 内托管采集器抓不到；本机写 ARMS 需 AccessKey，不建 |
| 采集器 | ECS 安装 LoongCollector 3.3.10（阿里云杭州内网安装脚本，SHA256 652b42bc…f918），用户标识 `/etc/ilogtail/user_defined_id` = `forcitis-one-production`，账号文件 `/etc/ilogtail/users/<主账号 ID>`；机器组 `forcitis-one-production`（自定义标识）心跳正常 |
| 采集配置 | `one-app`、`one-ops`、`one-nginx-access`、`one-nginx-error`、`one-metrics`，均应用到机器组 |
| 验证 | 证书检查日志与 nginx 日志可在 SLS 查到；`app` 为空、指标抓不到目标（应用未部署，符合「未部署前不接业务数据」） |
| 告警 | 未配：应用未部署，规则无数据可判。部署后按「规则定义」在 SLS 上配 PromQL 告警与 `ALERT*` 日志告警，通知走 SLS 行动策略中的同一群机器人 Webhook（届时再经安全输入窗口录入，不经聊天） |

保留期变更（2026-10-15，用户决定经 one-coordinator 转达：「nginx 与运维日志保留改 180 天」，依据《网络安全法》第二十一条网络日志留存不少于六个月）：`UpdateLogStore` 只改 `nginx`、`ops` 的 `ttl` 30 → 180，分片与自动分裂不变，未开智能分层；`GetLogStore` 读回 nginx 180、ops 180、app 30。费用：本账号按使用功能计费，存储 0.0115 元/GB/天（压缩后日志加索引，2026-10 账单单价），按每天约 10 万请求估约 42 MB/天，稳态多存 150 天约 6.3 GB，月增约 2 元，按 10 倍流量约 22 元，仍在 C-7「月费超 300 元先停」之内。回退：`ttl` 改回 30（超出部分随即按新保留期删除，删前须确认不违反留存要求）。

回退：删除 5 个采集配置与机器组（`DeleteLogtailPipelineConfig`、`DeleteMachineGroup`）→ ECS 上 `loongcollector.sh uninstall`（或 `/etc/init.d/loongcollectord stop` 后删 `/usr/local/ilogtail`），删 `/etc/ilogtail/user_defined_id` 与账号文件 → 删除 Project（数据随之删除，不再计费）。

## 通知渠道变量（只在 ARMS / SLS 控制台中配置，值不进仓库）

| 变量 | 内容 | 来源 |
|---|---|---|
| `ONE_ALERT_WEBHOOK` | 「FORCITIS one 运维」企业微信群机器人 Webhook（含 key，**机密**；ECS `ops-alert.env` root 0600，C-8 后迁 KMS） | A-5 |
| `ONE_ALERT_ONCALL` | 值班表（谁、何时） | A-5 |

SLS 中建两个行动策略：`ONE_ALERT_P1`（群机器人，24 × 7）、`ONE_ALERT_P2`（群机器人，工作时间；夜间合并到次日 09:00）。P1 不加短信（A-5 变更，用户「只发应用消息 (Recommended)」）。

## 执行前确认点

1. A-5 已定：「FORCITIS one 运维」群机器人（已接通，Webhook 在 ECS `ops-alert.env`；C-8 后迁 KMS），值班安排待定。
2. E-12 已完成，ARMS 中能查询到 `forcitis_` 指标；规则中的指标名先在 ARMS 查询页逐个查到再导入。
3. 作业失败依赖 Spring 定时任务观测指标 `tasks_scheduled_execution_*`：先在 dev 的 prometheus 端点确认存在；不存在则回报代码会话补指标。Flowable 死信任务数目前没有指标（`CROSSCHECK.md` 第 13 条），本规则先不含。

## 演练（dev，逐类触发并记录）

| 类 | 故障注入 | 期望 | 恢复 |
|---|---|---|---|
| 登录失败率 | 用脚本对 `POST /api/v1/platform/sessions:wecom-login` 发 25 次无效 code（来源为测试机） | 5 分钟内运维群收到 P1 | 停止脚本 |
| 未完成发布 | 在 dev 临时把 `WECOM_BASE_URL` 指向不可达地址并触发一条审批通知，等待超过 10 分钟 | stale > 0 告警；用尽后 exhausted / 死信告警 | 改回地址并重启；按 INFRA_RUNBOOK §9 处理残留 |
| 作业失败 | dev 中撤销 `one_dev_app` 对某作业表的权限（由 DBA 执行）或停 RDS 白名单 1 分钟 | 作业失败告警 | 恢复 |
| 应用不可达 | `systemctl stop forcitis-one` | 2 分钟后 P1 | 启动 |

演练记录：每类一行（注入时间、告警到达时间、渠道、处理人、恢复时间），存公司内部，摘要交 one-coordinator 作为 D-6 证据。

## 回滚

停用或删除对应告警规则；通知策略保留。
