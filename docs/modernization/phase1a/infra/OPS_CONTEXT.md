# one-ops 速查（2026-10-15）

规矩：持久资源先报再做、回读再报 one-coordinator；不部署（门禁 090 / 100）；不打印密钥；取证登记 [LEGACY_READ_REGISTER](LEGACY_READ_REGISTER.md)。

## 资源

| 名称 | 标识 | 状态 | 依据 |
|---|---|---|---|
| 阿里云账号 / 本机凭据 | 1980847222980247 / profile forcitis-production | 用 | — |
| 新 ECS | i-bp122xlpar8w2uylmal2，实例角色 forcitis-one-ecs-archive | 用 | C-4 |
| 老系统应用服务器 | i-bp1b34hcl0g1cnkdtrb9 | 只读探针 | B-5 |
| RDS PG | pgm-bp13vak0sew3m003 / forcitis_one_dev / one_admin | 用 | [C-3](runbooks/C-3-dev-db.md)、#131 |
| RDS SQL Server | rm-bp1690ep53t9778dp；one_a8_views_ro、psa_one_legacy_ro、one_legacy_names_ro、one_evidence_ro | 全库只读 | #135、#143 |
| SLS | forcitis-one-observability（nginx / ops 180 天） | 用 | [C-7](runbooks/C-7-alerts.md)、#123 |
| OSS 归档 | forcitis-one-archive（WORM 未锁） | 待锁 | B-13 |
| OIDC 提供商 | acs:ram::1980847222980247:oidc-provider/github-actions | ✅ | [RELEASE](runbooks/RELEASE.md) R-4、#144 |
| 部署角色 / 策略 | acs:ram::1980847222980247:role/forcitis-one-deploy-dev；forcitis-one-deploy-dev | ✅ | R-4 |
| 拉取策略 | forcitis-one-deploy-pull（挂 forcitis-one-ecs-archive） | ✅ | R-4 |
| OOS 参数 | forcitis-one-deploy-request-dev（+ -bundle / -bundle-sha256 / -statement / -statement-sig）、forcitis-one-deploy-result-dev | ✅ | R-7 |
| ECS 拉取 | /usr/local/sbin/forcitis-one-release、forcitis-one-release-pull.timer | ✅ 运行 | R-7 |
| GitHub | sub 定制（不可变）；environments 只允许 main；dev 变量 3 个 | ✅ | R-1、R-4、R-6 |

## 在途

| 步骤 | 下一步 | 卡在 |
|---|---|---|
| OIDC 部署 8 项 | one-coordinator 触发 Release 0.1.0-rc.1 → R-11 演练（先报） | 门禁 090 / 100 |
| 审计分区维护角色 | 写角色、授权、建库脚本 | DDL-24 卡与 PR |
| 迁入 ID 对照 | 抽取 | ADR-0037 实现 |

## 待批 / 待办

- 首次真实换 STS：核对实际 sub（job_workflow_ref 段）
- OIDC 首部署成功后：提醒用户删 R-13 后备令牌
- 首次 dev 部署通过后：删 /root/one-ops-backup-20261015020632；白天测 GitHub 直拉速度
- pg_stat_statements：Phase 2 前请示

## 坑

- SQL Server 2016 + RDS：服务器级权限不能 GRANT / DENY → B-5 README、#143
- PG 共享实例：superuser 级参数只能改实例级 → C-3、#131
- OIDC 证书链会变 → RELEASE §10
- 只读探针拒绝含 ALTER / KILL / DELETE 的字面量 → 用 `K_LL%`
- 云助手：命令内容有长度上限、输出约 24 KB → 分块写入、gzip + base64
- OOS 参数值 ≤ 4096 字符 → RELEASE §10

## 剧本

[RELEASE](runbooks/RELEASE.md) · [C-3](runbooks/C-3-dev-db.md) · [C-7](runbooks/C-7-alerts.md) · [C-8](runbooks/C-8-kms.md) · [B-5](handoff/B-5-dba/README.md) · [PREDEPLOY](PREDEPLOY_CHECKLIST.md) · [台账](../../COORDINATOR_LEDGER.md)
