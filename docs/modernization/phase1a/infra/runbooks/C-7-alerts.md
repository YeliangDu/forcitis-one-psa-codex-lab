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
