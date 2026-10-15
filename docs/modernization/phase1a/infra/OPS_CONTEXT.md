# one-ops 速查（2026-10-15 19:30）

规矩：持久资源先报再做、回读再报 one-coordinator；不部署（门禁 090 / 100）；不打印密钥；取证登记 [LEGACY_READ_REGISTER](LEGACY_READ_REGISTER.md)。

## 资源

| 名称 | 标识 | 状态 | 依据 |
|---|---|---|---|
| 本机 Python（SDK） | ~/.modernize/psa/ops-venv，按 [ops-venv-requirements.txt](ops-venv-requirements.txt) 安装；B-5 工具用它的 python 运行，不借其他会话的 scratchpad | 用 | — |
| 阿里云账号 / 本机凭据 | 1980847222980247 / profile forcitis-production | 用 | — |
| 新 ECS | i-bp122xlpar8w2uylmal2，实例角色 forcitis-one-ecs-archive | 用 | C-4 |
| 老系统应用服务器 | i-bp1b34hcl0g1cnkdtrb9 | 只读探针 | B-5 |
| RDS PG | pgm-bp13vak0sew3m003 / forcitis_one_dev / one_admin | 用 | [C-3](runbooks/C-3-dev-db.md)、#131 |
| RDS SQL Server 2016 | rm-bp1690ep53t9778dp；one_a8_views_ro、psa_one_legacy_ro、one_legacy_names_ro、one_evidence_ro；u8c = RDS 高权限（只用于授权变更，破例读须用户批准） | 全库只读 | #135、#143、#167 |
| SLS | forcitis-one-observability（nginx / ops 180 天） | 用 | [C-7](runbooks/C-7-alerts.md)、#123 |
| OSS 归档 | forcitis-one-archive（WORM 未锁） | 待锁 | B-13 |
| OIDC 提供商 | acs:ram::1980847222980247:oidc-provider/github-actions | ✅ | [RELEASE](runbooks/RELEASE.md) R-4、#144 |
| 部署角色 / 策略 | acs:ram::1980847222980247:role/forcitis-one-deploy-dev；forcitis-one-deploy-dev | ✅ | R-4 |
| 拉取策略 | forcitis-one-deploy-pull（挂 forcitis-one-ecs-archive） | ✅ | R-4 |
| OOS 参数 | forcitis-one-deploy-request-dev（+ -bundle / -bundle-sha256 / -statement / -statement-sig）、forcitis-one-deploy-result-dev | ✅ | R-7 |
| 时间同步检查 | one-time-sync-check.timer（每 5 分钟，100 ms → P2、1 s → P1） | ✅ 运行 | INFRA_RUNBOOK §8 |
| ECS 拉取 | /usr/local/sbin/forcitis-one-release、forcitis-one-release-pull.timer | ✅ 运行 | R-7 |
| Release | v0.1.0-rc.1（b0827667，run 38697278764，已验签，deploy/ 与 ECS 一致） | ✅ | #151 |
| GitHub | sub 定制（不可变）；environments 只允许 main；dev 变量 3 个；仓库级构建变量 8 个（SSO 门户与 PROD_* 待替换） | ✅ | R-1、R-4、R-6、§6 |

## 在途

| 步骤 | 下一步 | 卡在 |
|---|---|---|
| 首次 dev 部署 | deploy.yml dev 0.1.0-rc.1（R-11 ①⑦ 随此验证）；部署后 C-5 超管、B-12 流程管理员（用户本人） | 门禁 090（ADR-0026 数据范围）、100 / 101（#163） |
| C-14 只读校验真实登录 | 按 [C-14](runbooks/C-14-readonly-probe.md) 执行（已批，intent #166；第 17 项追加批准，第 16 项不做） | #163 + 只读核实命令合并 → rc.2；#171 合并 |
| dev 库重建剧本 | [dev-db-rebuild](runbooks/dev-db-rebuild.md)：删库重建 + 重置发布状态（current、previous、旧版本目录） | #174 合并；ADR-0038 用户批准前不触发 |
| 审计分区维护角色 | 写角色、授权、建库脚本 | DDL-24 卡与 PR |
| 迁入 ID 对照 | 抽取 | ADR-0037 实现 |

## 待批 / 待办

- 首次真实换 STS：核对实际 sub（job_workflow_ref 段）
- OIDC 首部署成功后：提醒用户删 R-13 后备令牌
- 首次 dev 部署通过后：删 /root/one-ops-backup-20261015020632；白天测 GitHub 直拉速度
- 构建变量待替换：SSO 门户地址（B-3 回传后）、PROD_*（生产前确认）
- 拉取单元沙箱收紧：首次 dev 部署跑通后、下次改安装器或单元时（RELEASE §10，#157）
- #228 合并后复核：迁移进程（migrate 模式参数未覆盖 FORCITIS_JOBS_ENABLED，沿用 one.env 的 true）不挂定时作业——migrate 模式在建上下文前退出，预期不挂；只有无 migrate 模式的旧包走整应用启动（#227 复审第 5 条）
- pg_stat_statements：Phase 2 前请示

## 坑

- SQL Server 2016 + RDS：服务器级权限不能 GRANT / DENY（4631 / 4613 / 4621）；u8c 服务器权限 = 只读基线，看不到服务器触发器 → B-5 README、#143、#167
- PG 共享实例：superuser 级参数只能改实例级 → C-3、#131
- OIDC 证书链会变 → RELEASE §10
- 只读探针拒绝含 ALTER / KILL / DELETE 的字面量 → 用 `K_LL%`
- 云助手：命令内容有长度上限、输出约 24 KB → 分块写入、gzip + base64
- OOS 参数值 ≤ 4096 字符 → RELEASE §10
- 安装器比对 current 的 migrations.txt：基线文件被改后第二次部署必失败 → [dev-db-rebuild](runbooks/dev-db-rebuild.md) 第 6 步
- corepack 按启动目录选 pnpm 版本 → 进 frontend 再调（#151、#156）
- CI 偶发 postgres:18 测试容器起不来 → `gh run rerun --failed`

## 剧本

[RELEASE](runbooks/RELEASE.md) · [C-3](runbooks/C-3-dev-db.md) · [C-14](runbooks/C-14-readonly-probe.md) · [dev-db-rebuild](runbooks/dev-db-rebuild.md) · [C-7](runbooks/C-7-alerts.md) · [C-8](runbooks/C-8-kms.md) · [B-5](handoff/B-5-dba/README.md) · [PREDEPLOY](PREDEPLOY_CHECKLIST.md) · [台账](../../COORDINATOR_LEDGER.md)
