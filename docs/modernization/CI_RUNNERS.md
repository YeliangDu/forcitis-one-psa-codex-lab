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

`scripts/ci/relevant_change.py` 比较 PR 与基线分支：只有 `docs/`（除 `glossary/`、`events/`、`phase1a/SEEYON_VIEWS.sql`，这三处被测试读取）和根目录 `*.md` 的改动才算无关；此时 backend、frontend 作业照常开始并成功结束，只是跳过构建步骤，三项必需检查仍然报告。push 到 main 总是全量运行。自检会扫描 `scripts/` 等目录中引用的 docs 路径，要求都列入「被读取」清单；`scripts/release/` 例外（ci 不运行这些脚本，自检同时断言 `ci.yml` 不引用它们）。

## 发布与部署

`release.yml`（发布包）与 `deploy.yml`（部署，被 `status/blockers/080` 拦住）也跑在这 4 个 runner 上，说明见 `docs/modernization/phase1a/infra/runbooks/RELEASE.md`。

- `release` 占 1 个 runner 约 1–2 分钟（不跑测试，只用 CI 已通过的结论）；`deploy` 先占 1 个 runner 跑 `gate`，审批后再占 1 个。两者都按工作流串行（`concurrency`），不取消进行中的运行。
- 构建工具链与 CI 相同（Java 25、Node 24、锁定的 pnpm），`build.sh` 只检查不安装。产物写在 `$RUNNER_TEMP`，作业结束删除。
- 部署作业把 environment secret 中的 deploy key 写到 `$RUNNER_TEMP`（权限 600），结束即删。4 个 runner 是同一 macOS 用户，同机的其他作业理论上能读到；建议另注册一个独立用户、标签 `forcitis-one-deploy` 的 runner 专跑 `deploy`（RELEASE.md §10）。
- runner 所在网络须有固定公网出口 IP，ECS 安全组的 22 端口只对它开放（RELEASE.md §8）；出口 IP 变化时同时改安全组与 ECS 上 deploy key 的 `from=`。
