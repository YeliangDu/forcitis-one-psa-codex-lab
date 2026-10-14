# CI 自托管 runner 维护说明

CI（`.github/workflows/ci.yml`、`generated-docs.yml`）全部跑在本机自托管 runner 上（用户 2026-10-14 决定），不保留 GitHub 托管机器兜底：runner 离线时作业排队等待。

## 现状

| 项 | 值 |
|---|---|
| 数量与名称 | 4 个：`mac-one-1` 至 `mac-one-4` |
| 标签 | `self-hosted`、`macOS`、`ARM64`、`forcitis-one` |
| 位置 | `~/actions-runners/one-N`（N = 1..4），下载包在 `~/actions-runners/_dl` |
| 常驻方式 | launchd（`~/Library/LaunchAgents/actions.runner.FORCITIS-forcitis-one-psa.mac-one-N.plist`） |
| 机器 | 16 核 / 64GB，Docker Desktop；工具链来自机器本身（Java 25、Node 24、pnpm 12.4.2、gitleaks 8.30.1、python3），PATH 记录在各 runner 的 `.path` |
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

`scripts/ci/relevant_change.py` 比较 PR 与基线分支：只有 `docs/`（除 `glossary/`、`events/`、`phase1a/SEEYON_VIEWS.sql` 这三处被测试读取，以及发布与部署脚本读取的 `status/blockers/`、`phase1a/infra/PREDEPLOY_CHECKLIST.md`、`phase1a/infra/runbooks/files/`）和根目录 `*.md` 的改动才算无关；此时 backend、frontend 作业照常开始并成功结束，只是跳过构建步骤，三项必需检查仍然报告。push 到 main 总是全量运行。自检会扫描 `scripts/` 等目录中引用的 docs 路径，要求都列入「被读取」清单，`scripts/release/` 也不例外。

## 发布与部署：专用 runner

`release.yml` 与 `deploy.yml` 的 `deploy` 作业只跑在专用 runner 上（RELEASE.md）；`deploy.yml` 的 `gate` 作业不碰凭据，仍在上面 4 个 runner 上跑。专用 runner 是 release 与 deploy 的**必需前置条件**（清单 B-10）：它不在，这些作业就排队不动。

| 项 | 值 |
|---|---|
| 名称与标签 | `mac-one-deploy`；`self-hosted`、`macOS`、`ARM64`、`forcitis-one-deploy`（**不带** `forcitis-one`，不接 CI 作业） |
| 运行用户 | macOS 用户 `forcitis-deploy`，非管理员，主目录 700（`yeliang` 读不到） |
| 位置 | `/Users/forcitis-deploy/actions-runner` |
| 常驻方式 | LaunchDaemon `actions.runner.forcitis-one-deploy`（`/Library/LaunchDaemons/actions.runner.forcitis-one-deploy.plist`） |
| PATH | `/opt/homebrew/bin:/usr/local/bin:/usr/bin:/bin:/usr/sbin:/sbin` |
| 工具 | 机器自带：git、curl、unzip、ssh、python3（Homebrew）、gh（Homebrew）、Homebrew 的 `openjdk@25`（不在 PATH 上，`toolchain.sh` 设 `JAVA_HOME`）。作业自带：Node 24 与 aliyun CLI 按版本与 SHA256 锁定下载到 `$RUNNER_TEMP`，pnpm 经 corepack 按 `frontend/package.json` 获取；Maven 仓库与 pnpm store 每次 release 在 `$RUNNER_TEMP` 新建（`--isolated-caches`），不读 `yeliang` 的 `~/.m2` 与 pnpm store。该用户没有 gh 登录，作业用 `GITHUB_TOKEN` |

隔离的意义：PR 的作业可以在 4 个 CI runner 上执行任意代码，但读不到 `forcitis-deploy` 的主目录、`$RUNNER_TEMP` 与 environment secret，也无法往 release 构建的依赖缓存里投放东西。因此 `forcitis-deploy` 不得加入管理员组，也不得给它加 `forcitis-one` 标签。

日常操作（需要管理员权限）：

```
sudo launchctl print system/actions.runner.forcitis-one-deploy      # 查看
sudo launchctl kickstart -k system/actions.runner.forcitis-one-deploy  # 重启
sudo launchctl bootout system/actions.runner.forcitis-one-deploy     # 停止并卸载（不再接作业）
sudo launchctl bootstrap system /Library/LaunchDaemons/actions.runner.forcitis-one-deploy.plist  # 重新加载
```

日志在 `/Users/forcitis-deploy/actions-runner/_diag/`（以 `forcitis-deploy` 身份查看：`sudo -u forcitis-deploy ls …`）。

升级：runner 程序会自行更新；手动升级时先 `bootout`，以 `forcitis-deploy` 身份在 `/Users/forcitis-deploy/actions-runner` 解压新版本覆盖（保留 `.runner`、`.credentials*`、`.env`、`.path`、`_work`），再 `bootstrap`。升级 Java（Homebrew `openjdk@25`）时同步检查 `toolchain.sh` 的路径；升级 Node 或 aliyun CLI 改 `toolchain.sh` 中的版本与 SHA256，同一个 PR 提交。

重新注册：在仓库 Settings → Actions → Runners 生成注册令牌（一小时有效，勿写入仓库），`bootout` 后以 `forcitis-deploy` 身份 `./config.sh remove --token <token>`，再 `./config.sh --url https://github.com/FORCITIS/forcitis-one-psa --token <token> --name mac-one-deploy --labels forcitis-one-deploy --unattended`，然后重新安装 LaunchDaemon（plist 中 `UserName` 为 `forcitis-deploy`）并 `bootstrap`。

部署走法兰克福 OSS 中转，最后一跳推荐云助手（RELEASE.md §8）：不需要 ECS 开入方向端口；若改用 SSH 方式，runner 所在网络须有固定公网出口 IP，ECS 安全组 22 端口只对它开放，出口 IP 变化时同时改安全组与 deploy key 的 `from=`。
