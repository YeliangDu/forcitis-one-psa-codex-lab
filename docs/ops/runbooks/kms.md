# C-8 剧本：配置与密钥从 `one.env` 迁到 KMS 凭据管家，轮换周期

依据：`proposals/PHASE1A_PLATFORM_CONVENTIONS.md` §6（凭据命名 `forcitis-one/<env>/<name>`、ECS RAM 角色、启动拉取、轮换表）、INFRA_RUNBOOK §5。时点：生产前。性质：开通 KMS 实例与凭据属购买 / 对外变更，执行前须用户确认。

## 迁移范围与轮换（建议，按平台约定 §6）

| 凭据名（`forcitis-one/<env>/…`） | 对应变量 | 轮换 | 方式 |
|---|---|---|---|
| `pg-app` | `SPRING_DATASOURCE_PASSWORD` | 90 天 | KMS 托管 RDS 凭据（RDS 账号自动轮换），或手工 |
| `pg-migrator` | `SPRING_FLYWAY_PASSWORD` | 90 天 | 同上；只在部署时读取 |
| `wecom-app-secret` | `WECOM_APP_SECRET` | 180 天；泄露立即 | 通用凭据，手工：企业微信后台重置 → 写 KMS 新版本 → 滚动重启 |
| `emergency-totp-key` | `EMERGENCY_TOTP_ENCRYPTION_KEY` | 365 天 | 手工；须先用新钥重新加密已存 TOTP 密钥（需代码提供重加密命令） |
| `ticket-key-<kid>` | `FORCITIS_TICKET_KEY_<KID>` | 180 天 | 双钥过渡：新 kid 上线 → 签发方切换 → 旧 kid 下线 |
| `a8-views-ro` | `A8_DB_PASSWORD` | 90 天 | 手工，由 A8 DBA 改口令 |
| `legacy-inbox-ro` | `FORCITIS_PLATFORM_INTEGRATION_LEGACY_INBOX_PASSWORD` | 90 天 | 手工，老系统 DBA |
| `legacy-names-ro` | `LEGACY_DB_RO_PASSWORD` | 90 天 | 手工，老系统 DBA |
| `evidence-ro` | `EVIDENCE_DB_PASSWORD`（`/etc/forcitis-one/evidence.env`，不在 `one.env`） | 90 天 | 手工：以 RDS 高权限账号 `ALTER LOGIN [one_evidence_ro] WITH PASSWORD`，口令在服务器端生成、只经密文（同 `handoff/B-5-dba/tools/b5_evidence_run.py` 的做法；该脚本 #742 已删，原文见 `git show 7d3f2d2ea:docs/modernization/phase1a/infra/handoff/B-5-dba/tools/b5_evidence_run.py`）；应用不用此口令，轮换无须重启 |
| `alert-webhooks` | C-7 的群机器人 Webhook | 泄露时 | 手工 |

非机密配置（地址、开关、ID）留在 one.env，不进 KMS。

## 方案

1. **KMS**：开通凭据管家（KMS 实例规格与费用以控制台为准，由用户确认）；按上表建凭据，dev 与 prod 分开（`forcitis-one/dev/…`、`forcitis-one/prod/…`）。
2. **权限**：ECS 绑定 RAM 角色 `forcitis-one-ecs`，策略只允许 `kms:GetSecretValue` 读取 `acs:kms:cn-hangzhou:<账号>:secret/forcitis-one/<env>/*`，不给列举、写入、删除。
3. **应用读取**（需代码改动，交代码会话；属「推迟的工作」，理由：1a dev 阶段 one.env 已满足 0640 与不入仓，KMS 依赖客户端库与 RAM 角色，在生产前一并上更稳）：
   - 首选：启动时由 Spring 配置导入阿里云凭据客户端，按变量名映射到凭据名；one.env 只保留凭据名；
   - 备选（不改代码）：systemd `ExecStartPre` 以 RAM 角色调用 `aliyun kms GetSecretValue` 生成 `/run/forcitis-one/secrets.env`（tmpfs，0640 root:forcitis），作为第二个 `EnvironmentFile`；进程停止后删除。
4. **切换**：逐项切换并重启验证，确认后删除 one.env 中对应明文；`grep -cE '_(PASSWORD|SECRET|KEY)=.+' /etc/forcitis-one/one.env` 期望 0。
5. **轮换演练**：生产前对 `pg-app` 演练一次（新口令 → KMS 新版本 → 滚动重启 → 旧口令失效）。

## 执行前确认点

- 用户确认 KMS 开通与规格；确认采用首选（改代码）还是备选（ExecStartPre）。
- 代码会话已实现读取方式并在 dev 验证。

## 校验

- 应用在 one.env 无明文机密的情况下正常启动；RAM 角色无法读取其他前缀的凭据（控制台模拟）。
- 守护：`ProductionConfigSanityTest` 与「prod 中密钥类配置必须是占位符」检查通过。

## 回滚

把对应变量的明文临时写回 one.env（从 KMS 读出，不经聊天），去掉 KMS 读取配置，重启。
