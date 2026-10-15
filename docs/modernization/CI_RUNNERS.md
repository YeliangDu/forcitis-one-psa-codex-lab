# CI 自托管 runner 维护说明

CI（`.github/workflows/ci.yml`、`generated-docs.yml`）全部跑在本机自托管 runner 上（用户 2026-10-14 决定），不保留 GitHub 托管机器兜底：runner 离线时作业排队等待。

## 现状

| 项 | 值 |
|---|---|
| 数量与名称 | 4 个：`mac-one-1` 至 `mac-one-4` |
| 标签 | `self-hosted`、`macOS`、`ARM64`、`forcitis-one` |
| 位置 | `~/actions-runners/one-N`（N = 1..4），下载包在 `~/actions-runners/_dl` |
| 常驻方式 | launchd（`~/Library/LaunchAgents/actions.runner.FORCITIS-forcitis-one-psa.mac-one-N.plist`） |
| 机器 | 16 核 / 64GB，Docker Desktop；工具链来自机器本身（Java 25、Node 24、pnpm 12.4.2、gitleaks 8.30.1、python3 为 pyenv 3.12.13 并带 PyYAML 6.0.3（`build_all.py --check` 生成 terms.yaml 用，SUPPLY_CHAIN S1；gitleaks 作业核验版本）），PATH 记录在各 runner 的 `.path` |
| 工作目录 | `~/actions-runners/one-N/_work`，`actions/checkout` 每次清理上次产物 |

作业只检查工具版本，不安装：版本不符时作业失败并指出哪一项（gitleaks 例外：版本不符时下载 8.30.1 darwin_arm64 包并校验 SHA256，装到 `$RUNNER_TEMP`，不用 sudo）。

## 并发与缓存

- 一个 PR 有 4 个作业（backend、frontend、gitleaks、assertion drift），正好占满 4 个 runner；两个 PR 同时到会排队。不要把作业再拆细。
- Maven 用共享的 `~/.m2`，pnpm 用共享的 store（`pnpm install --prefer-offline`）。两者对并发读写安全；依赖变更后的首次运行会下载。不使用 `actions/cache`，本机上传下载更慢。
- backend 作业运行 `scripts/ci/backend_verify.sh`：先编译，再同时运行 surefire 与 failsafe，各 4 个 fork，不带 jacoco 代理（CI 不读覆盖率，本地 `./mvnw verify` 仍生成报告）（`backend/pom.xml` 的 `test.forks`，`-Dtest.forks=1` 恢复串行）。
- 测试容器（postgres:18）由 Testcontainers 的 ryuk（`testcontainers/ryuk:0.14.0`）在 JVM 退出时回收；作业开始前若本机没有这两个镜像会先拉取。查残留：`docker ps`，应只剩与 CI 无关的常驻容器。

## 日常操作

```
cd ~/actions-runners/one-1
./svc.sh status      # 查看
./svc.sh stop        # 停止（该 runner 不再接作业）
./svc.sh start       # 启动
```

其余 runner 同理（one-2 至 one-4）。日志在各目录 `_diag/`。

## 升级

runner 程序会在 GitHub 发布新版本后自动更新自己；需要手动升级时，对每个 runner：`./svc.sh stop`，在 `~/actions-runners/_dl` 放入新版本包并解压覆盖 `one-N`（保留 `.runner`、`.credentials*`、`.env`、`.path`、`_work`），再 `./svc.sh start`。升级 Java / Node / pnpm / gitleaks 时先更新机器上的安装，再同步更新 `ci.yml` 里的版本检查，同一个 PR 提交。

## 注册与移除

注册令牌在 GitHub 仓库 Settings → Actions → Runners 里生成（一小时有效，勿写入仓库）；`./config.sh remove --token <token>` 移除。

## 作业怎样判定可跳过

`scripts/ci/relevant_change.py` 比较 PR 与基线分支：只有 `docs/`（除 `glossary/`、`events/`、`phase1a/SEEYON_VIEWS.sql` 这三处被测试读取，以及发布与部署脚本读取的 `status/blockers/`、`phase1a/infra/PREDEPLOY_CHECKLIST.md`、`phase1a/infra/runbooks/files/`）和根目录 `*.md` 的改动才算无关；此时 backend、frontend 作业照常开始并成功结束，只是跳过构建步骤，三项必需检查仍然报告。push 到 main 总是全量运行（SQL Server 集成测试例外，它按改动路径判定，见下节）。自检会扫描 `scripts/` 等目录中引用的 docs 路径，要求都列入「被读取」清单，`scripts/release/` 也不例外。

## SQL Server 集成测试（profile legacy-it）

`*MssqlIT` 只在 `-Plegacy-it` 下编译和运行（ADR-0037 守护第 5 组）。`relevant_change.py` 另输出 `legacy_it`：改到 `platform/integration`、`platform/approval/infrastructure/legacy` 的源码与测试、`acceptance/platform/integration`、`*MssqlIT` 直接引用的类与文件（`TestImages.java`、`kernel/WecomUserId.java`、`SEEYON_VIEWS.sql` 等，自检扫描 `*MssqlIT.java` 的 import 与相对路径，漏列即失败）、`backend/architecture/` 的 `test-images.txt` 与 `seeyon-view-columns.yaml`、`backend/pom.xml`、B-5 交接包的 `.sql`、`scripts/ci/legacy_it.sh`、`ci.yml` 与 `nightly-legacy-it.yml` 时，backend 作业末尾多跑 `scripts/ci/legacy_it.sh`（超时 30 分钟）。push 到 main 与前一次推送比较，比较不了时照跑；因 `ci.yml` 的并发组会取消同一分支上未完成的运行，连续合并时中间一次可能漏跑，由 PR 门禁与每夜全量兜底。

只跑 `*MssqlIT` 与 ADR 原文的 `verify -Plegacy-it` 等价：该 profile 只比默认构建多编译并运行 `*MssqlIT`，其余测试与同一作业里的默认全量运行相同，不必重跑。

每夜另有 `nightly-legacy-it.yml`：北京时间 03:23（cron `23 19 * * *`，UTC 19:23）在同一批 runner 上全量运行 `backend_verify.sh -Plegacy-it`，随后核实有 `TEST-*MssqlIT.xml` 报告（没有即失败）；也可手动触发。它占一台 runner，最长 90 分钟。

**怎样发现每夜失败**：不另设通知。调度会话开场执行 `gh run list --workflow nightly-legacy-it.yml -L 1`，最近一次不是 success 就开卡处理。

## 发布与部署：专用 runner

`release.yml` 的 `build`（打包）与 `publish`（签名）都跑在 GitHub 托管的 `ubuntu-24.04`（用户决定：「签名步骤放 GitHub 托管机器」「发布打包改到 GitHub 托管机器」；这是对「CI 不用托管机器」的例外，只限发布）。`deploy.yml` 的 `deploy` 作业只跑在专用 runner 上，专用 runner 也**只**接这一个作业；`deploy.yml` 的 `gate` 不碰凭据，仍在上面 4 个 runner 上跑。专用 runner 是部署的**必需前置条件**（清单 B-10），按 RELEASE.md §7 R-0 重建。

| 项 | 值 |
|---|---|
| 名称与标签 | `mac-one-deploy`；`self-hosted`、`macOS`、`ARM64`、`forcitis-one-deploy`（**不带** `forcitis-one`，不接 CI 作业） |
| 运行用户 | macOS 用户 `forcitis-deploy`，非管理员；主目录 `/Users/forcitis-deploy`，700 |
| 位置 | `/opt/forcitis-one-runner`：程序与配置归 root、只读；只有 `_work`、`_diag` 归 `forcitis-deploy`；`.runner`、`.credentials*` 为 root:forcitis-deploy 0640 |
| 常驻方式 | LaunchDaemon `actions.runner.forcitis-one-deploy`（`/Library/LaunchDaemons/actions.runner.forcitis-one-deploy.plist`，root 所有），`--disableupdate` 注册 |
| pre-job hook | plist 的 `EnvironmentVariables` 设 `ACTIONS_RUNNER_HOOK_JOB_STARTED=/usr/local/libexec/forcitis-one/runner-job-started.sh`（root 所有，来自 `scripts/release/runner_job_started.sh`）；只放行 main 上手动运行的 deploy.yml，并与事件载荷交叉核对 |
| 环境 | plist 中 `PATH=/usr/bin:/bin:/usr/sbin:/sbin`（`.path` 相同；**不含 `/opt/homebrew`**：它归 `yeliang:admin`，CI 作业可改写其中的程序）、`PYTHONNOUSERSITE=1`、`GIT_CONFIG_GLOBAL=/dev/null`、`HOME=/opt/forcitis-one-runner-home`（root 所有的空目录）；每个作业第一步改用 `$RUNNER_TEMP/home`，所有 `python3` 以 `-I` 运行；账号主目录中不得有 `Library/Python`、`.gitconfig`、`.config/git`、`.aliyun`、`.curlrc` |
| 工具 | 系统自带且 root 所有：bash、git 与 python3（Command Line Tools）、curl、tar、shasum、unzip、ssh、ssh-keygen 等；`toolchain.sh` 每个作业开头核验它们与 PATH 各项的真实路径及全部上级目录归 root、组与他人不可写，以及主目录中没有上述配置，否则失败。Java 25（`javap` 校验 jar 用）、gh、aliyun CLI 每个作业按版本与 SHA256 下载到 `$RUNNER_TEMP`（可选以 root 安装 Temurin 25 .pkg 免下载 Java） |

日常操作（需要管理员权限）：

```
sudo launchctl print system/actions.runner.forcitis-one-deploy      # 查看
sudo launchctl kickstart -k system/actions.runner.forcitis-one-deploy  # 重启
sudo launchctl bootout system/actions.runner.forcitis-one-deploy     # 停止并卸载（不再接作业）
sudo launchctl bootstrap system /Library/LaunchDaemons/actions.runner.forcitis-one-deploy.plist  # 重新加载
```

日志在 `/opt/forcitis-one-runner/_diag/`。

升级（`--disableupdate` 下 runner 不自行更新）：`bootout`；下载新版本 runner 包并核对 GitHub 公布的 SHA256，以 root 解压覆盖 `/opt/forcitis-one-runner` 的程序文件（保留 `.runner`、`.credentials*`、`.env`、`.path`、`_work`、`_diag`），重新按 R-0 第 4 步设所有权；`bootstrap`。GitHub 对过旧的 runner 版本会停止派发作业，需定期升级。升级 Node、Java、gh 或 aliyun CLI 改 `scripts/release/toolchain.sh` 中的版本与 SHA256，同一个 PR 提交。hook 更新时以 root 重新安装并 `kickstart`。

重新注册：按 RELEASE.md §7 R-0 第 1–8 步（令牌在 Settings → Actions → Runners 生成，一小时有效，勿写入仓库），注册后做第 8 步的演练。

部署作业经 GitHub OIDC 换取阿里云临时凭据（runner 上不存任何阿里云或 GitHub 长期凭据），把安装请求与四个 Release 附件的短期签名地址写进该环境的 OOS 参数，再轮询结果参数；ECS 上的拉取定时器以实例角色读回请求，直接从 GitHub 下载附件并验签（RELEASE.md §3、§8）。部署作业没有任何让 ECS 执行命令的接口。不需要 ECS 开入方向端口，也不需要 runner 有固定出口 IP；SSH 触发方式已删除。
