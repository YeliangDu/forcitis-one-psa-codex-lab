# C-9 剧本：建生产库 `forcitis_one_prod` 与 `one_prod_*` 角色

依据：ADR-0025 第 2 条（生产沿用现有实例、另建库与 `one_prod_*` 角色）、INFRA_RUNBOOK D-1。时点：生产前。性质：对外变更，执行前须用户确认。

可执行脚本：`sql/c9_prod_db.sql` → `sql/c9_prod_db_verify.sql`；回滚 `sql/c9_prod_db_rollback.sql`。三者由 C-3 的 dev 脚本经 `sql/render_prod_sql.sh` 生成（只替换库名与角色名）；`render_prod_sql.sh --check` 证明两套脚本只差名称，改动一律改 dev 版再重新生成。

## 执行前确认点

1. C-3 已在 dev 执行并通过 V1–V8，且首次迁移、Flowable 安装在 dev 已验证（包括 C-3 中的 `currentSchema` 风险）。
2. 连接预算重算：it_portal + `one_dev_*`（31）+ `one_prod_*`（31）< `max_connections` − 保留。若 A-7 决定生产前停用 dev 库，先执行 C-3 回滚再建生产库。
3. 生产角色的超时与 `work_mem` 是否与 dev 相同：脚本沿用 dev 建议值；按 dev 压测结果由用户确认是否调整（调整改 dev 版再生成）。
4. ADR-0025 的分析侧 schema（`reporting`、`reporting_compat`、`reporting_archive_src`）与构建、重建、查询、镜像角色属 R1，不在本剧本。
5. 生产口令与 dev 不同；dev 凭据不能连生产库（角色名不同，库级 CONNECT 只授予本环境角色）。

## 步骤

> 慢语句日志不带参数值：实例级 `log_parameter_max_length = 0` 已设（2026-10-15，见 C-3 执行记录），库级无法设置，建生产库时无需另设；建库后 `SHOW log_parameter_max_length` 应为 0。


```bash
cd docs/ops/runbooks/sql && ./render_prod_sql.sh --check   # 必须通过
psql -v ON_ERROR_STOP=1 -h <RDS 内网地址> -U <高权限账号> -d forcitis_one -f c9_prod_db.sql
psql -v ON_ERROR_STOP=1 -h <RDS 内网地址> -U <高权限账号> -d forcitis_one_prod -f c9_prod_db_verify.sql | tee c9-verify-$(date +%F).txt
```

口令：RDS 控制台为 `one_prod_migrator`、`one_prod_app`、`one_prod_readonly` 设口令；生产直接写入 KMS（C-8），不再落 one.env 明文。

## 校验

同 C-3，角色名换成 `one_prod_*`。

## 回滚

`sql/c9_prod_db_rollback.sql`，只在生产尚无业务数据时使用；有数据后不删库，改为撤销 `one_prod_app` 的 CONNECT 并停应用。
