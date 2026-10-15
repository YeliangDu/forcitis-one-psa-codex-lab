# dev 库重建剧本（迁移基线开放期内基线变化后）

依据：用户 2026-10-15 决定 dev 部署与结构重构解耦，迁移基线在 dev 期间保持开放、生产前才关闭，并接受「每次重构后 dev 库要重建」（intent 2026-10-15-124901，PR #173）；PREDEPLOY_CHECKLIST C-15。基线关闭时点改到生产前的 ADR-0038 仍在起草，**用户批准前 dev 不因基线变化而重建**；批准后按本剧本执行不再逐次请示，每次执行在文末执行记录登一行，完成后报 one-coordinator。

## 何时触发

- 基线开放期内（`backend/architecture/migration-baseline-open` 存在），自上次 dev 部署以来任一基线迁移文件内容有改动：Flyway `validate` 会因历史表中的校验和不符而失败，部署在迁移阶段失败（安装器原因 `migration`），`deploy_gate` 对 dev 也会给出告警并指向本剧本。
- 只是新增迁移文件、没有改动已有文件时不必重建。
- 判断：`git diff --name-only <上次 dev 部署的提交> <本次提交> -- backend/src/main/resources/db/migration` 中有已存在文件被修改即触发。上次部署的提交见 ECS `forcitis-one-release status` 当前版本的 manifest `gitSha`。

## 数据不保留

- 删库即删除 dev 库全部数据（业务数据、会话、审计、Flowable 引擎表、事件发布记录），**不备份、不迁移**。
- 执行前在运维群告知用户：「dev 库将重建，数据清空，约 30 分钟内不可用」；dev 只有项目组在用（A-8 白名单）。

## 步骤

所有步骤经云助手在新 ECS 上以 root 执行；数据库操作以 `one_admin`（`/etc/forcitis-one/one-admin.pgpass`）连接；口令只在 ECS 生成，不经会话。

1. **停应用与拉取**：`systemctl stop forcitis-one`；`systemctl stop forcitis-one-release-pull.timer`（重建期间不受理部署请求）。
2. **确认无残留连接**：`SELECT count(*) FROM pg_stat_activity WHERE datname = 'forcitis_one_dev'` 为 0；不为 0 时查明来源，不强行终止他人连接。
3. **删库与角色**：`sql/c3_dev_db_rollback.sql`（在 `forcitis_one` 库上执行；不触碰 IT 服务门户的库与角色）。回读 `pg_roles` 无 `one_dev_%`、`pg_database` 无 `forcitis_one_dev`。
4. **建库建角色**：`sql/c3_dev_db.sql` → `sql/c3_dev_db_verify.sql`（期望值见 C-3「校验」）。
5. **设口令**：`scripts/c3_set_role_passwords.py dev <RDS 内网地址> --rotate`（新口令写入 `one.env` 的 `SPRING_DATASOURCE_PASSWORD` 与 `/etc/forcitis-one/migrate/`；旧口令随角色删除已失效）。
6. **重置服务器的发布状态**（库重建后必须同时做；只删库不够）：
   ```bash
   ln -sfn /data/forcitis-one/bootstrap /data/forcitis-one/current     # 没有当前版本：下一次部署按首次安装处理
   rm -f /data/forcitis-one/previous                                   # 不留回退目标
   find /data/forcitis-one/releases -mindepth 1 -maxdepth 1 -exec rm -rf {} +   # 重置前的版本目录全部移除
   forcitis-one-release status                                         # 期望 current: bootstrap，previous 为空，installed releases 为空
   ```
   - 为什么：安装器在迁移前用当前版本目录里的 `migrations.txt` 与新版本比对（`remote_install.sh` 的 `new_migrations`，「a migration file changed between releases」），基线文件被改动时**第二次 dev 部署会在这里失败**，与库是否已重建无关；current 指回 bootstrap 后没有比对基准，全部迁移按新库首次执行。安装器也只在没有当前版本时接受 Flowable 首装与迁移账号数据源（`FIRST_INSTALL_ONLY`）。
   - previous 必须删：新版本健康后，安装器只在切换前有当前版本时才改写 previous，否则 previous 仍指向重置前的版本，`forcitis-one-release rollback`（不带参数）会切回与新库结构不符的旧版本。
   - 版本目录必须移除：带版本号的 `rollback` 只要求该版本目录存在；移除后**重置前的版本一律不能回退**。需要某个旧功能状态时，重新部署（发布包从签名的 GitHub Release 重新下载）。
   - **重建后旧发布包不能再回滚**；回滚只能靠重新部署新版本。
7. **Flowable 首装凭据**：按 RELEASE.md §4「首次部署」在 `/etc/forcitis-one/migrate/` 放 `flowable.database-schema-update`（`true`）、`spring.datasource.username`、`spring.datasource.password`（迁移账号）、`spring.datasource.url`（带 `&currentSchema=platform_approval`），root 600。
8. **首次部署前检查**：`platform_integration.event_publication` 不存在或为空（新库应为空，C-3 第 0.1 步）；ECS `ecs_check.sh` 通过。
9. **恢复拉取并部署**：`systemctl start forcitis-one-release-pull.timer`；由 one-coordinator 触发 `deploy.yml`（dev，目标版本）。
10. **部署后**：`sql/c3_dev_db_after_migrations.sql`（版本表只追加）；C-3 `c3_dev_db_verify.sql` 的 V8（表属主、ACT_* 位于 `platform_approval`）；删除第 7 步放入的四个首装文件（非首次安装时这些文件存在，安装器拒绝运行）；首任超管与流程管理员按 C-5、B-12 重新授予（用户本人）。

## 回退

重建过程中任何一步失败：停在该步、不部署，报 one-coordinator。库已删除时没有回退到旧数据的路径（数据不保留）；可重复第 3–8 步。

## 执行记录

| 日期 | 触发原因（改动的基线文件） | 执行会话 | 结果 |
|---|---|---|---|
