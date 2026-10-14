# 发布与部署（release / deploy 工作流）

整理：one-coordinator 子代理，2026-10-14。补 `PREDEPLOY_CHECKLIST.md` 遗漏项「CI 无发布产物」与 `CROSSCHECK.md` 第 15 条；按 PR #59 的用户决定（专用部署 runner）与 opus 安全复审修订；发布包到 ECS 的通路按用户决定「先测 GitHub 直拉，可行就用 (Recommended)」改为 ECS 自取 GitHub Release，不经 OSS 中转（§8）；组织 FORCITIS 为 **GitHub Team 计划**，各项 GitHub 功能是否可用见 §11，凡需 Enterprise Cloud 的都不假定可用。

**当前禁止部署任何环境**（`status/blockers/080`，带 `blocks: deploy` 标记）。`deploy` 工作流在该阻塞存在时直接失败；本文的一次性步骤只是准备，涉及 GitHub 设置、阿里云、ECS 的每一步仍须用户确认后执行（清单 B-10）。

| 文件 | 作用 |
|---|---|
| `.github/workflows/release.yml` | 由 main 上已通过 ci 的提交构建发布包、签署发布声明、附到 GitHub Release |
| `.github/workflows/deploy.yml` | 把已发布（非草稿）的版本部署到 dev / prod；只写好，未运行过 |
| `scripts/release/build.sh` | 构建发布包；本机可直接运行，工作流只是调用它 |
| `scripts/release/toolchain.sh` | release 构建（GitHub 托管机）与部署 runner 的工具链；部署 runner 上失败关闭：系统工具与 PATH 必须 root 所有、组与他人不可写（含全部上级目录），账号主目录中不得有会左右 python3 / git / aliyun / curl 的配置；Java 25、gh、Node 24、aliyun CLI 按版本与 SHA256 锁定下载；corepack 取锁定的 pnpm |
| `scripts/release/verify_bundle.sh` | 按部署时的方式校验发布包（归档 SHA256、`SHA256SUMS`、`manifest.json`、jar 可启动） |
| `scripts/release/ci_passed.py` | 证明提交是 main 的 first-parent 提交，且该提交 push 到 main 触发的 ci 运行成功、三项必需作业成功 |
| `scripts/release/deploy_gate.py` | 部署守护：阻塞条目、prod 的版本与前置条件 |
| `scripts/release/statement.sh` | 发布声明：写出（版本、提交、发布包与 manifest / SHA256SUMS 的 SHA256、构建工作流与运行号）、签名（`ssh-keygen -Y sign`）、校验 |
| `scripts/release/allowed_signers` | 发布签名公钥（`ssh-keygen` allowed_signers 格式）；空文件时部署一律失败 |
| `scripts/release/runner_job_started.sh` | 部署 runner 的 pre-job hook：只放行 main 上手动运行的 deploy.yml，并与 runner 写下的事件载荷交叉核对 |
| `docs/…/runbooks/prod-approvals.txt` | prod 部署批准行（经 PR 合入 main），代替 Team 计划下不可用的 required reviewers |
| `scripts/release/ship.sh` | 部署最后一段：经固定的云助手命令（或受限 SSH）触发 ECS 安装并等待结果；ECS 自行向 GitHub 核对部署记录、下载 Release 附件、验签、安装 |
| `scripts/release/remote_install.sh` | ECS 上的安装器（装为 `/usr/local/sbin/forcitis-one-release`） |
| `scripts/release/install_request_test.sh` | 安装器部署记录核对的本地测试：替身 curl 返回预置的 GitHub 应答，不联网、不需 root |
| `scripts/release/one_release_token_check.sh` | ECS 上 GitHub 只读令牌的到期检查（R-13；`files/one-release-token-check.*` 每日运行） |

## 1. 版本号

- 形如 `MAJOR.MINOR.PATCH`，预发布加 `-rc.N`（`0.1.0`、`0.1.0-rc.1`）；标签为 `v<版本>`。Phase 1a 期间用 `0.x`。预发布版本只能部署 dev。
- 一个版本只构建一次：同名 Release 已存在则 `release` 失败；仓库启用 immutable releases（R-9）后，发布后的附件与标签不可再改；附件即使被替换，也过不了签名发布声明的校验；ECS 上同一版本内容不同则拒绝安装。修复一律发新版本。
- `backend/pom.xml` 的版本为 `${revision}`（默认 `0.0.1`），`build.sh` 以 `-Drevision=<发布版本>` 打包，jar 的 build-info 与清单 `Implementation-Version` 都等于发布版本（不一致即构建失败）；部署核对仍以 `manifest.json` 的 `version` 与 `gitSha` 为准。

## 2. 发布（`release`）

触发：只能**在 main 上手动运行**（输入版本、可选提交 sha、是否草稿，默认草稿）。不设标签推送触发：发布声明要写明「由 main 上的 release.yml 构建」，签名私钥也只给 main 用，标签触发的运行两者都不满足；两个作业都有 `if: github.ref == 'refs/heads/main'`。release 的两个作业都跑在 **GitHub 托管的 `ubuntu-24.04`**（用户决定：「发布打包改到 GitHub 托管机器 (Recommended)」），每次都是全新虚拟机，本机 CI runner 上的任何作业都影响不到。`deploy` 只接受已发布的 Release，草稿经人核对后在 GitHub 上点「Publish」。

`build` 作业（GitHub 托管 `ubuntu-24.04`，权限 `contents: read`、`actions: read`；checkout 不保存凭据）。**证明、构建、签名用的脚本一律取自本工作流自己的提交**（`github.sha`，即 main 上的 release.yml 所在提交），检出到 `tools/`；要发布的提交另行检出到 `source/`，只作为构建输入，它自带的脚本不运行：

1. 输入校验（版本格式、sha 格式）后才写入作业输出；未给提交时取 `github.sha`。
2. `tools/` 的 `toolchain.sh build`：只在 GitHub 托管的 Linux 上运行，按版本与 SHA256 下载 Java（Temurin 25.0.4.1）、gh、Node 24；pnpm 由 corepack 按 `packageManager` 中的 `+sha512` 校验获取。
3. **证明这个提交测过**（`tools/` 的 `ci_passed.py`）：
   - 提交必须在 `git rev-list --first-parent origin/main` 中——即 main 上的合并提交或直接提交；被合并分支中间的提交（祖先但不在 first-parent 链上）一律拒绝；
   - 按工作流运行判定：`actions/runs?head_sha=<sha>&event=push&branch=main` 中 `path` 为 `.github/workflows/ci.yml` 的最新一次运行 `completed/success`，且其三项必需作业（backend verify、frontend verify、secret scan）都是 `success`；pull_request 事件的运行、其他分支或其他工作流的运行都不算。不重跑测试。作业名取自 `ci.yml`，改名而脚本未同步时直接失败。
4. 同名 Release 不存在；同名标签若已存在必须指向该提交。
5. 检出已证明的提交到 `source/`；**构建**（`tools/` 的 `build.sh --source source --commit <sha>`）：`source/` 必须干净且恰在该提交；全新的 `HOME`、Maven 仓库与 pnpm store（`--isolated-caches`）；`pnpm install --ignore-scripts`（依赖的安装脚本一律不跑，已验证构建不受影响）；Maven Wrapper 按 `distributionSha256Sum` 校验下载的 Maven；校验 Java 25、Node 24、锁定的 pnpm；
   - 后端：`./mvnw -B clean package -DskipTests -Drevision=<发布版本>`（POM 版本为 `${revision}`，默认 `0.0.1`）。POM 尚未声明 `spring-boot-maven-plugin` 时，脚本临时加 `repackage` 目标并指定启动类。`backend/target` 中匹配 `forcitis-one-[0-9]*.jar`（不含 `-sources`、`-javadoc`、`-tests`）的必须恰好一个、且名为 `forcitis-one-<发布版本>.jar`，多于一个或没有即失败（避免误选测试产物 `forcitis-one-arch-proof-*.jar`）。然后检查 jar 能被 `java -jar` 启动（Boot 可执行 jar、Start-Class 是 `OneApplication`、该类有 `main`），并断言 jar 内 `META-INF/build-info.properties` 的 `build.version` 与清单的 `Implementation-Version` 都等于发布版本，不满足即失败。
   - 前端：PC（web）与 H5 按 dev、prod 各构建一份（构建期变量不同，见 §6），H5 放在 PC 产物的 `h5/` 下。
6. 打包、`verify_bundle.sh` 复核，作为 Actions artifact（保留 1 天）交给下一作业。

`publish` 作业（另一个 **GitHub 托管的 `ubuntu-24.04`** 作业——签名私钥不进运行过第三方构建代码的虚拟机；`environment: release`，权限只有 `contents: write`，只检出 `github.sha` 的 `scripts/release`，绝不检出构建的提交）：核对 artifact 的 SHA256 → `statement.sh write <发布包> <声明> <已证明的提交>` 写出**发布声明**（版本、提交、发布包与包内 `manifest.json`、`SHA256SUMS` 的 SHA256、`GITHUB_WORKFLOW_REF`、运行号），并断言 `manifest.json` 的 `gitSha` 等于该提交 → 用 environment `release` 的 secret `RELEASE_SIGNING_KEY` 以 `ssh-keygen -Y sign`（命名空间 `forcitis-one-release`）签名，签完立即删除私钥文件 → `gh release create v<版本> --target <sha>`，附发布包、`.sha256`、`.release`、`.release.sig`。`release` environment 的部署分支策略只允许 main（Team 私有仓库可用，§11），其他分支的运行拿不到私钥。签名与验签用系统自带的 OpenSSH（Ubuntu、macOS 与 Alibaba Cloud Linux 4 均自带，`-Y sign/verify` 自 OpenSSH 8.1 起提供），不另外下载。

发布包内容（顶层目录 `forcitis-one-<版本>/`）：

| 路径 | 内容 |
|---|---|
| `forcitis-one.jar` | 可执行 jar（E-6 的固定文件名） |
| `web-dev/`、`web-prod/` | 各环境的 PC 静态资源，内含 `h5/` |
| `deploy/` | `forcitis-one.service`、`nginx-one.conf`、`forcitis-one.logrotate`、`one.env.template`、`remote_install.sh`（取自本目录 `files/` 与 `scripts/release/`） |
| `migrations.txt` | 全部 Flyway 迁移文件的 SHA256 与路径 |
| `manifest.json` | 版本、git sha、提交时间、构建时间、JDK / Node / pnpm 版本、环境、构建期变量名（不含值）、迁移数与最新迁移、每个文件的 SHA256 与大小 |
| `SHA256SUMS` | 除自身外每个文件（含 `manifest.json`）的 SHA256，`sha256sum -c` 可直接校验 |

### 存放：GitHub Release（采用）与 Actions artifact 的取舍

| | GitHub Release 附件（采用） | Actions artifact |
|---|---|---|
| 保留期 | 永久，随标签；immutable releases 下不可改 | 最长 90 天（私有仓库默认 90），过期即无法回滚到该版本 |
| 寻址 | `v<版本>` 标签，`gh release download` | 运行 ID，需先查运行 |
| 草稿 / 人工发布 | 支持，`deploy` 只认已发布的 | 无 |
| 文件形式 | 原样 | 自动再打 zip |
| 成本 | 不计入 Actions 存储 | 计入 Actions 存储配额 |

Actions artifact 只用于同一次 release 运行中两个作业之间的传递（1 天）。

## 3. 部署（`deploy`）

只能在 main 上手动运行，输入环境（dev / prod）、版本；prod 另须在 `confirm` 再输一次版本。

1. `gate` 作业（`forcitis-one` runner，不进 environment，失败不必等审批）：版本格式；prod 的确认；`deploy_gate.py`：
   - `docs/modernization/status/blockers/` 中任一条目含 `` `blocks: deploy` `` 标记 → 失败。阻塞片段是单行（`build_records.py` 的格式），标记写在行内而非 front-matter。修复批合并后由调度会话删除该条目或标记。
   - prod：不接受预发布版本；`PREDEPLOY_CHECKLIST.md` D 节（Phase 1a 出口条件，**在 dev 上验收**）每一行状态格恰为 ✅（多一个字也不算），且行数不少于 9；C-9（生产库）为 ✅。表格改了形状时失败而不是放行。
   - prod：版本已列在 `runbooks/prod-approvals.txt`（批准行经 PR 合入 main）。Team 计划的私有仓库没有 environment 的 required reviewers（§11），由这一行代替人工批准。
2. `deploy` 作业（`environment: dev / prod`，两者的部署分支策略只允许 main）跑在**专用部署 runner**（标签 `forcitis-one-deploy`，macOS 用户 `forcitis-deploy`，只接这一个作业，见 `CI_RUNNERS.md`）；第一步把 `HOME` 与 git 全局配置换成本作业 `$RUNNER_TEMP` 下的新目录；所有 `python3` 都以 `-I` 运行；权限 `contents: read`、`actions: read`、`deployments: write`，checkout 不保存凭据：
   - 工具链（`toolchain.sh deploy`：先核验 PATH 与系统工具；Java 25、gh、aliyun CLI 按 SHA256 下载）；
   - 再跑一次 `deploy_gate.py`（gate 之后 main 可能新增阻塞）；
   - Release 非草稿；标签经 API 解析出的提交满足 `ci_passed.py`；
   - 下载附件 → `statement.sh verify`：用**仓库中**的 `scripts/release/allowed_signers`（main 上的公钥，不取自 Release）验签，声明的版本、提交（等于标签所指提交）、发布包名、构建工作流（`FORCITIS/forcitis-one-psa/.github/workflows/release.yml@refs/heads/main`）、发布包与 `manifest.json`、`SHA256SUMS` 的 SHA256 全部一致 → `verify_bundle.sh` → `manifest.json` 的 `gitSha` 必须等于标签所指提交 → prod：`deploy_gate.py approval` 核对批准行的提交与发布包 SHA256 恰为这一个构建；
   - **部署记录**：以 `GITHUB_TOKEN` 建一条 GitHub deployment：`ref` 为标签 `v<版本>`，`environment` 为目标环境，`auto_merge=false`、`required_contexts=[]`，`payload` 为 `{"version": "<版本>", "run_id": <本次运行号>, "commit": "<标签所指提交>"}`。GitHub 对绑定 environment 的作业本身也会建一条（`ref` 为 main），它早于这一条，ECS 只取最新的一条，不受影响；
   - `ship.sh <环境>`（`DEPLOY_TRIGGER`，§8）：调用**预建的、无参数的**云助手命令（`InvokeCommand`，命令内容固定为 `forcitis-one-release install-pending`），或受限 SSH 密钥的强制命令（`ssh -F /dev/null … -l <用户> -- <主机> install-pending`）。调用方不传任何内容；云助手方式轮询执行结果，把安装器输出打回 Actions 日志，失败、超时或退出码非 0 即该步失败；
   - **部署状态**：无论上一步成败（`always()`），本作业以 `GITHUB_TOKEN` 给这条 deployment 写最终状态 `success` 或 `failure`。ECS 的令牌只读，不写状态；
   - 阿里云 AccessKey 只来自 environment secret，经环境变量交给 `aliyun` CLI，不执行 `aliyun configure`（会在用户目录留下凭据），不打印；SSH 私钥写入 `ship.sh` 的临时目录，脚本退出时（含失败）由 `trap` 删除。deploy 作业不持有发布签名私钥，也不向 ECS 传送发布包。

### ECS 上的安装器（`remote_install.sh`）

云助手以 root 运行固定命令；SSH 方式下 deploy key 在 `authorized_keys` 中是强制命令，只能运行安装器的 `install-pending`、`rollback`、`status`，拿不到 shell，安装器以 sudo（仅此一个命令）切到 root。`install-pending` 依次：

1. **本机配置**：`/etc/forcitis-one/release-source`（root 0644）给出 `ENVIRONMENT`（dev / prod）、`GITHUB_REPOSITORY`（须为 `FORCITIS/forcitis-one-psa`，即签名声明中构建工作流所在的仓库）与可选的 `WECOM_VERIFY_FILE`；`install-pending` 不接受任何参数，环境只取自这里。`one.env` 的 `SPRING_PROFILES_ACTIVE` 须等于 `ENVIRONMENT`；`current` 若是手工留下的实目录则拒绝（C-4 E-10）；**断言 `/data/forcitis-one` 与 `releases` 为 root 所有、组和其他人不可写**（root 在其中操作，应用用户不得能替换任何东西，C-4 E-5）；取部署锁（`/run/forcitis-one-release.lock`）。
2. **GitHub 只读令牌**（R-13）：`/etc/forcitis-one/github-release-token` 须 root 0600，内容为 fine-grained 令牌（`github_pat_…`）；`github-release-token.expires`（root 0644，`YYYY-MM-DD`）为其到期日，**到期当日起拒绝**并提示轮换。令牌只写进 `/run/forcitis-one-release/` 下 root 专属的临时请求头文件，以 `curl -H @文件` 使用，不进命令行、进程列表与日志，脚本退出时删除。
3. **核对部署记录**（任一项不符即安装失败，不跳过）：
   - `GET /repos/{仓库}/deployments?environment=<ENVIRONMENT>&per_page=5` 中 id 最大的一条（最新）。其最新状态已是 `success` / `failure` / `error` / `inactive` → 「没有待部署」并失败（deploy 作业总是先建记录再触发，正常不会出现）；更早的未完成记录不理会；
   - `environment` 等于本机 `ENVIRONMENT`；`creator` 为 `github-actions[bot]`（类型 Bot）——这只说明记录由某个工作流运行以 `GITHUB_TOKEN` 建立，**单靠它不足以放行**；
   - `ref` 为 `v<版本>` 且版本合格式；`payload.version` 等于去掉 `v` 的 `ref`；`payload.run_id` 为正整数，且所取的记录中只有这一条指向该运行（一次运行只建一条；同一运行被「Re-run」或被别的工作流冒名再建一条时拒绝，失败后重新发起一次 deploy 运行，不用 Re-run）；`payload.commit` 为完整 sha 且等于记录的 `sha`；
   - **关键控制——运行核对**：`GET /repos/{仓库}/actions/runs/{run_id}` 的 `path` 为 `.github/workflows/deploy.yml`、`head_branch` 为 `main`、`event` 为 `workflow_dispatch`、`status` 为 `in_progress`，`repository` 与 `head_repository` 都是本仓库，`display_title` 恰为 `deploy <版本> to <ENVIRONMENT>`（deploy.yml 的 `run-name` 由运行的输入生成，把版本与环境绑到该运行上）。只有**正在运行的、在 main 上手动触发、正部署这一版本到这一环境的 deploy.yml** 所指的记录才会被安装；
   - 标签：`GET /repos/{仓库}/git/ref/tags/v<版本>` 解析出的提交（附注标签逐层解引用，最多 3 层）等于 `payload.commit`。
4. **每条记录只装一次**：`/var/lib/forcitis-one-release/processed-deployments`（目录 root 0700、文件 root 0600，只追加）中已有该 id 即拒绝；核对通过后**先记下 `started` 再开始安装**，安装中途崩溃也不会被同一条记录盲目重放；结束时在其后追加 `succeeded`，或 `failed` 与原因。`forcitis-one-release status` 显示最近几行。
5. prod 拒绝预发布版本；低于当前运行版本的拒绝（见「回滚」）；`release-source` 给出 `WECOM_VERIFY_FILE` 时要求该文件在 `static-pinned` 中存在。
6. **从 GitHub 取发布包**：`GET /repos/{仓库}/releases/tags/v<版本>`，须非草稿，`forcitis-one-<版本>.tar.gz`、`.tar.gz.sha256`、`.release`、`.release.sig` 各恰好一个、已上传、≤ 1 GiB、地址是本仓库的附件 API；逐个以 `Accept: application/octet-stream` 经附件 API 下载并跟随重定向到 GitHub 的短期签名地址（curl 只把 `Authorization` 头发给 `api.github.com`，不带到重定向目标；只许 HTTPS；20 分钟超时），放在 `releases/.incoming.*`；发布包 SHA256 与 `.sha256` 附件一致；归档条目只能在 `forcitis-one-<版本>/` 下，无链接、无 `..`；**用 ECS 上 root 所有的 `/etc/forcitis-one/release-signers` 验发布声明的签名**，并核对声明中的版本、提交、构建工作流与发布包、`manifest.json`、`SHA256SUMS` 的 SHA256、声明中的发布包名（公钥不取自发布包，GitHub 侧被攻破也换不掉）；解包后 `sha256sum --strict -c SHA256SUMS`，不允许有清单外的文件；`manifest.json` 的 `version` 与 `gitSha` 都与部署记录一致。
7. **服务器文件一致**（新装的版本）：已安装的安装器、systemd 单元、nginx 配置、logrotate 与发布包中 `deploy/` 的同名文件逐字节相同，`one.env` 含模板中的每个键；不一致即失败并列出（§5）。
8. 放到 `/data/forcitis-one/releases/<版本>/`（root:forcitis，目录 750、文件 640），`web` 软链指向 `web-<环境>`，给 nginx 用户只读 ACL。已装过且内容一致的版本直接复用，不改 `web` 软链；装给另一环境的同版本拒绝。
9. 迁移（§4），**每次部署都运行**：有新迁移时应用它们，没有时由 Flyway 校验已应用的迁移与本版本一致。
10. 单元若尚未 enabled（首次安装），先 `systemctl enable forcitis-one`，失败即安装失败；再原子切换 `current` 软链，`systemctl restart forcitis-one`（此后每次安装只重启），经本机回环取 `http://127.0.0.1:<MANAGEMENT_SERVER_PORT>/actuator/health`，解析 JSON 顶层 `status` 为 `UP`；并且 `/data/forcitis-one/static-pinned/` 下每个企业微信校验文件经本机 nginx（`https://one.forcitis.com/<文件名>`）返回原文（最长 180 秒）。`release-source` 给出 `WECOM_VERIFY_FILE` 时，切换前还要求该文件存在（第 5 步）。
11. 不健康：自动切回原来的 `current` 并重启，原版本健康则以「已回切」报错退出，原版本也不健康则报「需人工处理」；首次部署没有上一版本时停止服务并报错。
12. 健康：上一版本记为 `previous`；保留最近 5 个版本（`current`、`previous` 之外），更旧的删除；每次结果追加到 `releases/HISTORY`。

### 回滚

- **降级只走运维的显式回滚**：`install-pending` 拒绝低于当前版本的部署记录（按 `MAJOR.MINOR.PATCH`，`-rc.N` 低于对应正式版），因此不能用 `deploy` 部署旧版本号；持有部署凭据的人也就无法借部署记录把系统退回旧版本。
- 回滚：运维经云助手执行 `forcitis-one-release rollback`（回到 `previous`）或 `forcitis-one-release rollback <已安装的版本>`；旧版本的文件仍在 `releases/` 中，已在首次安装时校验过；回滚不跑迁移（旧版本不认识的新迁移被 Flyway 默认的 `*:future` 忽略）。修复问题后发更高的版本号再部署。
- `forcitis-one-release status` 查看当前、上一版本、历史与最近的部署记录处理结果；回滚只用本机已装的版本，不访问 GitHub，GitHub 不可用时照样可做。
- 回滚只换应用，不回滚数据库（§4）。

## 4. 数据库迁移

**现状（核对代码与剧本）**：迁移只有 Spring Boot 启动时自动运行的 Flyway 一条路，代码中没有单独的迁移入口；未设 `spring.flyway.user`，所以 Flyway 用的是运行连接（`one_dev_app`）。而 C-3 的 `one_dev_app` 只有 DML、对 `one_migration` 没有 USAGE、`search_path` 为 `platform_approval`：常驻进程若照默认跑 Flyway，会在 `platform_approval` 找不到历史表、发现非空 schema 而拒绝启动。剧本（INFRA_RUNBOOK §2、§5）的意图是「迁移以 `one_dev_migrator` 单独执行，常驻进程不持有迁移凭据」。本流水线按该意图落地：

- `one.env` 设 `SPRING_FLYWAY_ENABLED=false`（已加入 `files/one.env.template`，`ecs_check.sh` 检查）：常驻进程不跑 Flyway。
- **迁移进程**：安装器每次部署起一个临时 systemd 单元：
  - 以独立用户 **`forcitis-migrate`**（主组 forcitis，只为读取发布目录；C-4 E-3）运行，`PrivateTmp`、`NoNewPrivileges`；
  - **凭据存放**：`/etc/forcitis-one/migrate/`（root 700），每个 Spring 属性一个文件（root 600，文件名即属性名），常规为 `spring.flyway.user`、`spring.flyway.password`。安装器逐个以 systemd `LoadCredential=` 交给该单元，进程在 `/run/credentials/<单元>/` 读到（只有该单元可读，单元结束即清除），Spring 以 `configtree:` 读取——**不经环境变量、不进命令行与进程列表**。同名设置若也在 `one.env` 中，以 `UnsetEnvironment=` 去掉，保证凭据目录的值生效。
  - **生命周期**：运维在 C-3 设口令后写入该目录；每 90 天随口令轮换更新文件；dev 停用（A-7）时删除目录。常驻进程（`forcitis` 用户）读不到该目录。
  - 命令行打开 `spring.flyway.enabled`、指定 `default-schema=one_migration`；关掉所有定时作业与外发：组织同步、短名刷新、企业微信投递、审批时限、事件重投与清理（各自的开关）、会话清理、凭据清理、号段容量检查（cron 设为 `-`）、流程引擎异步执行器。`ApplicationReadyEvent` 上的 `EngineWiring` 只校验流程定义并注册监听器，无外部副作用。
  - **迁移模式**：发布包中的 jar 含 `application-migrate.properties`（1a 正在做的 migrate profile，已排入 B2）时，安装器以 `--spring.profiles.include=migrate` 运行并等待进程退出码（`systemd-run --wait`，超时 15 分钟）；没有该模式时，以「日志出现 `Started OneApplication`」判定 Flyway 已完成（Flyway 出错会中止启动）随即停止该单元，并提示到 `one_migration.flyway_schema_history` 确认；单元在此之前退出或超时 → 报「迁移结果未知，查 flyway_schema_history」，不切换版本。
  - 迁移日志写在 root 专属目录 `/var/log/forcitis-one-release/`（root 700），不写进应用用户可写的 `logs/`；SLS 采集需另加该路径（Logtail 以 root 运行）。
- 两个版本中同名迁移文件内容不同 → 失败（迁移只追加，CI 的 `migrations_immutable.py` 已守护，部署再守一次）。
- **只前进不回滚**：不写 undo 迁移，回切应用版本时数据库保持新结构。因此每个迁移必须让**上一个版本的应用**照样能跑（只扩不缩）：
  - 允许：新表、新列（可空或有默认值）、新索引、新视图、放宽约束；
  - 不允许在同一版本里：删除或改名表 / 列、收紧约束、改列类型、改既有数据的含义。确需收缩时分两步：先发不再使用旧结构的版本，等它成为回滚基线（至少一个版本之后）再发删除旧结构的迁移；
  - 「只增不删不改名」的守护由 1a 在迁移基线 PR 中实现（复审 M-6），在那之前靠审查，PR 中写明「上一版本兼容」；
  - 回切到不认识新迁移的旧版本时，安装器打警告并照常切换；Flyway 默认的 `ignoreMigrationPatterns=*:future` 容忍未来版本的迁移。
- **首次部署**另需建 Flowable 引擎表：首次在凭据目录再放 `flowable.database-schema-update`（内容 `true`）、`spring.datasource.username`、`spring.datasource.password`（迁移账号）与 `spring.datasource.url`（带 `&currentSchema=platform_approval`，C-3 风险项）；**非首次安装时目录中出现任何 `spring.datasource.*` 或 `flowable.database-schema-update` 文件，安装器拒绝运行**。首次部署成功并跑完 C-3 的 V8 后删除这几项。

## 5. 服务器文件的变更

systemd 单元、nginx 配置、logrotate、`one.env` 的键、安装器本身都随仓库版本管理（本目录 `files/` 与 `scripts/release/remote_install.sh`）。这些文件变了的版本，部署会因「服务器文件不一致」失败；须先由运维按 C-4 的方式（经用户确认）从**该版本的发布包** `deploy/` 中安装新文件（`nginx -t` 后 reload、`systemctl daemon-reload`），再部署。安装器的更新同理（§7 R-7）。

## 6. 构建期变量

前端目前在构建时把企业微信与单点登录参数编进静态文件，只能按环境各构建一份。值不是机密（会出现在浏览器里），放在**仓库级** Actions variables，以环境名作前缀：

| 变量 | 用途 |
|---|---|
| `DEV_VITE_WECOM_CORP_ID`、`PROD_VITE_WECOM_CORP_ID` | PC 扫码登录的企业 ID（B-2 新应用） |
| `DEV_VITE_WECOM_AGENT_ID`、`PROD_VITE_WECOM_AGENT_ID` | PC 扫码登录的应用 AgentId（B-2） |
| `DEV_VITE_SSO_PORTAL_URL`、`PROD_VITE_SSO_PORTAL_URL` | 致远门户地址（B-3） |
| `DEV_TARO_APP_WECOM_CORP_ID`、`PROD_TARO_APP_WECOM_CORP_ID` | H5 的企业 ID |

任一为空，`build.sh` 失败（不静默构建出不能登录的包）。不放在 GitHub environment 里的原因：一个作业只能绑定一个 environment，且绑定 `prod` 会触发 prod 的审批与部署记录，发布时同时构建两个环境做不到。长期做法是前端改为一次构建、运行时读取配置（例如 nginx 提供的 `/config.json`），届时这些变量移到 `one.env`，发布包只含一份静态资源（给 1a 的建议）。

## 7. 一次性步骤（用户或运维执行，执行前确认）

本文不建 RAM 账号、AccessKey、GitHub 令牌、environment、secret 或云助手命令，下列各项由用户或运维执行。缺任何一项，相应作业失败关闭，不会降级执行。

### R-0 专用部署 runner 重建（必需；只有 deploy 作业依赖它，release 在 GitHub 托管机上）

**已完成（2026-10-15，one-ops 引导、用户输入口令）**：脚本 `scripts/release/runner_rebuild.sh`（参数：发行包、GitHub 公布的 SHA256、注册令牌文件）。runner 2.338.0（osx-arm64，SHA256 与发布页一致），`mac-one-deploy` 在线，标签 `self-hosted, macOS, ARM64, forcitis-one-deploy`，进程以 `forcitis-deploy` 运行。

**第 8 步演练结果（2026-10-15，临时分支 `drill/r0-runner-refusal`，演练后分支与运行记录已删除）**：
| 情形 | 结果 |
|---|---|
| 分支上新增工作流，作业 `runs-on: [self-hosted, forcitis-one-deploy]`，push 触发 | 在「Set up runner」被 hook 拒绝：`workflow '…/r0-drill.yml@refs/heads/drill/r0-runner-refusal' is not deploy.yml on main` |
| 同一工作流在工作流级与作业级 `env` 伪造 `GITHUB_REF`、`GITHUB_EVENT_NAME`、`GITHUB_WORKFLOW_REF` | 同样被拒绝，拒绝理由显示的是真实的工作流来源（伪造不生效） |
| 分支上手动运行 release.yml | 两个作业因 `if: github.ref == 'refs/heads/main'` 跳过，未进入任何 runner |
| 分支上手动运行 deploy.yml | 两个作业同样跳过 |
| main 上手动运行 release.yml 并把作业指到部署 runner | 不做：须改 main 上的工作流，等于真实变更 |
| main 上手动运行 deploy.yml（放行路径） | 不做：部署仍被 blockers/080 挡住，gate 先失败、到不了部署 runner；首次 dev 部署时验证放行 |

原 `mac-one-deploy` 已从 GitHub 注销（从未接过作业），本机 LaunchDaemon 仍在。按下列方案重建，使作业改不了 runner 本身、改不了 hook、也用不到任何用户可写的程序：

1. 停掉旧服务：`sudo launchctl bootout system/actions.runner.forcitis-one-deploy`；删除旧 runner 目录与 plist，清空 `forcitis-deploy` 主目录后重建为空目录（属主 `forcitis-deploy`，700）。`forcitis-deploy` 不在 admin 组。
2. **runner 目录放到用户主目录之外**（主目录归该用户，其下的 root 目录仍可被它改名替换）：`/opt/forcitis-one-runner`，root:wheel 755。另建只含 `forcitis-deploy` 的组 `forcitis-deploy`（`dseditgroup -o create forcitis-deploy`，加入该用户）。
3. 在该目录解压 runner 发行包（核对 GitHub 公布的 SHA256），临时把目录交给 `forcitis-deploy` 完成注册：`./config.sh --url https://github.com/FORCITIS/forcitis-one-psa --token <一次性令牌> --name mac-one-deploy --labels forcitis-one-deploy --work _work --disableupdate --unattended`。
4. **收回所有权**：除 `_work`、`_diag` 外全部改回 root:wheel、只读（`bin/`、`externals/`、`run.sh`、`runsvc.sh`、`env.sh` 等）；`.runner`、`.credentials`、`.credentials_rsaparams` 改为 root:forcitis-deploy 0640（runner 要读，作业改不了）；`.env` 与 `.path` 改为 root:wheel 0644，`.path` 内容只写 `/usr/bin:/bin:/usr/sbin:/sbin`；`_work`、`_diag` 归 `forcitis-deploy`。`--disableupdate` 使 runner 不自行改写程序文件，升级按 `CI_RUNNERS.md` 手工做。
5. **hook**：`sudo install -o root -g wheel -m 0755 scripts/release/runner_job_started.sh /usr/local/libexec/forcitis-one/runner-job-started.sh`（目录 root 755）。
6. **LaunchDaemon**：`/Library/LaunchDaemons/actions.runner.forcitis-one-deploy.plist`（root:wheel 0644）：`UserName` 为 `forcitis-deploy`，`ProgramArguments` 为 `/opt/forcitis-one-runner/run-helper.sh`（`run.sh` 每次启动都把 `run-helper.sh.template` 复制成 `run-helper.sh` 写进 runner 目录；该目录归 root、对 runner 用户只读，复制失败、服务反复重启，所以由 root 预先生成 `run-helper.sh` 并直接启动它），`EnvironmentVariables` 中写 `ACTIONS_RUNNER_HOOK_JOB_STARTED=/usr/local/libexec/forcitis-one/runner-job-started.sh`、`PATH=/usr/bin:/bin:/usr/sbin:/sbin`、`PYTHONNOUSERSITE=1`、`GIT_CONFIG_GLOBAL=/dev/null`、`HOME=/opt/forcitis-one-runner-home`（root:wheel 555 的空目录：runner 进程本身没有可写、可被投放配置的主目录；每个作业第一步改用 `$RUNNER_TEMP/home`）。hook 变量放在 root 所有的 plist，而非 runner 可写的位置。账号主目录 `/Users/forcitis-deploy` 中不得有 `Library/Python`、`.gitconfig`、`.config/git`、`.aliyun`、`.curlrc`（`toolchain.sh` 断言，存在即失败）。`sudo launchctl bootstrap system <plist>`。
7. **程序可信**（复核 B-2）：部署 runner 只运行 root 所有、组与他人不可写（含全部上级目录）的程序，或按 SHA256 锁定下载到 `$RUNNER_TEMP` 的程序。`toolchain.sh` 每个作业开头检查 PATH 各项与 `bash python3 git curl tar shasum unzip ssh ssh-keygen …` 的真实路径，不满足即失败。`/opt/homebrew` 归 `yeliang:admin`，CI 作业可改写其中的 python3 / gh / java，因此不在 PATH 中、也不被使用：python3、git 用 Command Line Tools（`/usr/bin`，root 所有）；Java 25、gh、Node、aliyun CLI 每个作业按 SHA256 下载（可选：以 root 安装 Temurin 25 的 .pkg 到 `/Library/Java/JavaVirtualMachines/temurin-25.jdk`，`toolchain.sh` 核验后直接使用，省去每次下载约 200 MB）。
8. **重新注册后演练**（全部应被拒，在第一步之前失败）：
   - 在一个分支上手动运行 release.yml；
   - 一个分支新增工作流，`runs-on: [self-hosted, forcitis-one-deploy]`，push 触发；
   - 在 main 上手动运行 release.yml 但把某个作业指到 `forcitis-one-deploy`（不应出现；hook 只放行 deploy.yml）；
   - 同一工作流在**工作流级与作业级 `env`** 分别写 `GITHUB_REF: refs/heads/main`、`GITHUB_EVENT_NAME: workflow_dispatch`、`GITHUB_WORKFLOW_REF: FORCITIS/forcitis-one-psa/.github/workflows/release.yml@refs/heads/main`（hook 另与 runner 写在自身 `_work/_temp` 的事件载荷交叉核对 `ref`、`workflow`、仓库，伪造的变量对不上载荷）；
   - 在 main 上手动运行 deploy.yml，应被放行（之后会被 `blocks: deploy` 阻塞或缺少 environment 设置而失败，属预期）。
   本机已用模拟载荷验证这些情形的判定（PR 正文）。

**推荐**：现在采用上面的「root 所有的常驻 runner + `--disableupdate` + plist 中的 hook」。备选为 ephemeral / JIT：由 root 启动器在每个作业前，从 root 所有的原始副本重建 runner 目录与 `HOME`，用 JIT 配置注册一次性 runner。它能清除作业留下的一切（含后台进程与主目录文件），但需要在本机保存能签发 JIT 配置的凭据（GitHub App 私钥或管理员令牌，root 专用），并多一个启动器要维护。发布构建与签名都已移到 GitHub 托管机器，部署 runner 只跑 main 上的 deploy 作业；该作业不执行第三方构建代码，只运行仓库中已评审的脚本与按 SHA256 下载的工具，且每个作业使用全新的 `HOME`。常驻方案的剩余风险是某个被放行的 deploy 作业被攻破后留下进程或文件影响下一个，可能性低于构建作业。若日后部署 runner 要接别的作业，或要消除这一残余，再改 JIT。

### 其余步骤

| # | 步骤 | 位置 / 做法 |
|---|---|---|
| R-1 | 建 GitHub environments `release`、`dev`、`prod`；三者「Deployment branches and tags」都只允许 `main`（Team 私有仓库可用）。不设 required reviewers / wait timer（Team 私有仓库不可用，§11）；prod 的人工批准改为 `prod-approvals.txt` 批准行 **✅ 2026-10-15**：三个 environment 已建，部署分支策略均只有 `main`。 | 仓库 Settings → Environments |
| R-2 | 仓库级 Actions variables：§6 的 8 个构建期变量（B-2、B-3 完成后填） | Settings → Secrets and variables → Actions → Variables |
| R-3 | ~~OSS bucket~~ **不再需要**：发布包改由 ECS 自取 GitHub Release（§8，用户「先测 GitHub 直拉，可行就用 (Recommended)」），不建发布用 bucket；B-11 归档 bucket 与此无关 | — |
| R-4 | RAM：每环境一个部署用子账号（只开 OpenAPI 访问），绑 §8.2 的策略：只允许对**该命令与该实例**的 `ecs:InvokeCommand`，以及 `ecs:DescribeInvocationResults`、`ecs:DescribeInvocations`；生成 AccessKey，每 90 天轮换。**发布不需要 ECS 实例 RAM 角色**（不授任何 OSS 权限）；SSH 方式不需要该子账号 | 阿里云 RAM 控制台 |
| R-5 | 每个 environment 的 secrets：`ALIBABA_CLOUD_ACCESS_KEY_ID`、`ALIBABA_CLOUD_ACCESS_KEY_SECRET`；SSH 方式另加 `DEPLOY_SSH_KEY`、`DEPLOY_KNOWN_HOSTS`（主机公钥由运维经云助手读 `/etc/ssh/ssh_host_ed25519_key.pub` 交来，不用 `ssh-keyscan`）；environment `release` 的 `RELEASE_SIGNING_KEY`（R-12） | Environments |
| R-6 | 每个 environment 的 variables：`DEPLOY_TRIGGER`（`cloud-assistant`；`ssh` 仅当选用）；云助手方式 `ECS_REGION=cn-hangzhou`、`ECS_INSTANCE_ID=i-bp122xlpar8w2uylmal2`、`ECS_COMMAND_ID`（R-7 建的固定命令）；SSH 方式 `DEPLOY_HOST`、`DEPLOY_USER=forcitis-deploy`、可选 `DEPLOY_PORT`。原 `OSS_BUCKET`、`OSS_REGION`、`OSS_PREFIX` 不再使用；企业微信校验文件名改为 ECS 本机 `release-source` 的 `WECOM_VERIFY_FILE`（不再是 GitHub 变量） | Environments → dev / prod |
| R-7 | ECS（运维经云助手，root）：C-4 E-3、E-5、E-9、E-10 的新做法；安装器、签名公钥、`release-source`（`ENVIRONMENT`、`GITHUB_REPOSITORY`、可选 `WECOM_VERIFY_FILE`）、R-13 的令牌文件与到期日、令牌到期检查与定时器、云助手固定命令（下方命令块）；SSH 方式另建部署用户与受限密钥。ECS 上不再需要 aliyun CLI 与实例角色配置 | ECS；云助手控制台 |
| R-8 | 标签保护：Rules → Rulesets 新建标签规则 `v*`，限制创建、更新、删除（仅管理员与 release 工作流） **✅ 2026-10-15（偏差）**：仓库级规则不允许把 GitHub Actions 设为例外（须组织级规则），若限制创建，release 工作流打不了新标签；因此规则「release tags v*」只限制**更新与删除**（例外：仓库管理员），不限制创建。残余：有写权限者可新建 `v*` 标签，但产生不了签名的发布声明，部署验签不过。 | Settings → Rules |
| R-9 | 启用 immutable releases（本仓库 API 返回 `enabled:false`：功能存在、未启用；只对启用后的新 Release 生效）；确认 Actions 允许工作流声明 `contents: write`，且允许使用 GitHub 托管 runner 运行 `publish`（Team 计划含托管分钟数） **✅ 2026-10-15**：immutable releases 已启用；Actions 允许全部、默认令牌只读（工作流按需声明 `contents: write`）、托管 runner 可用。 | Settings → General（Releases）/ Actions |
| R-10 | 首次部署前 `/etc/forcitis-one/one.env` 补 `SPRING_FLYWAY_ENABLED=false`；按 §4 在 `/etc/forcitis-one/migrate/` 放迁移凭据文件（首次另加 Flowable 安装项） | ECS |
| R-11 | 连通演练（不部署，R-4、R-7、R-13 之后）：① ECS 上用令牌请求头文件 `curl -fsS -H @<头文件> https://api.github.com/repos/FORCITIS/forcitis-one-psa/deployments?per_page=1` 返回 200；② 下载一个已发布 Release 的附件到 `/tmp` 并记录耗时与速度（§8.3，白天测一次）后删除；③ 部署 runner 上用 dev 凭据调云助手固定命令：此时没有待部署记录，安装器应以「no deployment」或「nothing to deploy」失败，证明触发、权限、令牌与输出回传都通；④ 经云助手执行 `forcitis-one-release status` | 运维 |
| R-12 | **发布签名密钥**：在可信机器上 `ssh-keygen -t ed25519 -N '' -C forcitis-one-release -f release_signing`；私钥存为 environment `release` 的 secret `RELEASE_SIGNING_KEY`，随后删除本地私钥文件；公钥写成一行 `forcitis-one-release namespaces="forcitis-one-release" ssh-ed25519 AAAA… forcitis-one-release`，经 PR 写入 `scripts/release/allowed_signers`，并由运维装到 ECS `/etc/forcitis-one/release-signers`（root 0644）。轮换：两处同时替换；旧版本的声明随之不可验证，回滚到旧版本需用新钥重签或临时并列两把公钥 **✅ 2026-10-15**：在用户本机生成，私钥直接存为 environment `release` 的 secret `RELEASE_SIGNING_KEY` 后删除（未显示、未落盘他处）；公钥写入 `scripts/release/allowed_signers`；ECS 侧 `/etc/forcitis-one/release-signers` 随 R-7 安装。 | 可信机器；GitHub；ECS |
| R-13 | **用户步骤：GitHub 只读令牌**（每环境一个，见下方「R-13」）：用户在 github.com 生成 fine-grained 令牌，只经 one-ops 会话的安全输入窗口交给运维；运维写入 ECS 的 `/etc/forcitis-one/github-release-token`（root 0600）与到期日文件，并装到期检查 | 用户（github.com）；运维（ECS） |

R-7 命令（`<version>` 用将要部署的发布包；先在可信机器上 `verify_bundle.sh` 校验，再经云助手上传其中的 `deploy/remote_install.sh`；令牌检查脚本与单元取自本仓库 `scripts/release/` 与本目录 `files/`）：

```bash
install -o root -g root -m 0755 remote_install.sh /usr/local/sbin/forcitis-one-release
install -o root -g root -m 0644 release-signers /etc/forcitis-one/release-signers      # R-12 的公钥行

cat > /etc/forcitis-one/release-source <<'EOF'
ENVIRONMENT=dev
GITHUB_REPOSITORY=FORCITIS/forcitis-one-psa
WECOM_VERIFY_FILE=<static-pinned 中现有的 WW_verify_*.txt；没有则删去本行>
EOF
chown root:root /etc/forcitis-one/release-source && chmod 0644 /etc/forcitis-one/release-source

# R-13：先建空文件，令牌由运维经安全输入窗口写入（一行，不经聊天、不进命令行）；到期日为 GitHub 页面显示的日期
install -o root -g root -m 0600 /dev/null /etc/forcitis-one/github-release-token
echo '<YYYY-MM-DD>' > /etc/forcitis-one/github-release-token.expires
chown root:root /etc/forcitis-one/github-release-token.expires && chmod 0644 /etc/forcitis-one/github-release-token.expires

# 令牌到期检查：每日；不足 14 天 P2，不足 3 天、已过期或文件缺失 P1（经 one_alert_send）
install -o root -g root -m 0755 one_release_token_check.sh /usr/local/sbin/one_release_token_check.sh
install -o root -g root -m 0644 one-release-token-check.service one-release-token-check.timer /etc/systemd/system/
systemctl daemon-reload && systemctl enable --now one-release-token-check.timer
systemctl start one-release-token-check.service; journalctl -u one-release-token-check.service -n 3 --no-pager

forcitis-one-release status
```

云助手固定命令（每台 ECS 一条；一台只承载一个环境，环境取自 `release-source`；控制台「创建命令」或 `CreateCommand`；**不启用自定义参数**，内容即下面一行，执行用户 root，超时 2700 秒）：`/usr/local/sbin/forcitis-one-release install-pending`；命令 ID 填入该环境的 `ECS_COMMAND_ID`。安装器拒绝带参数的 `install-pending`，按旧写法（`install-pending dev`）建过的命令须重建。

SSH 方式（仅当选用）：

```bash
useradd --system --create-home --home-dir /var/lib/forcitis-deploy --shell /bin/bash forcitis-deploy
install -d -o forcitis-deploy -g forcitis-deploy -m 0700 /var/lib/forcitis-deploy/.ssh
echo 'restrict,from="<部署 runner 出口 IP>",command="/usr/local/sbin/forcitis-one-release" ssh-ed25519 AAAA… forcitis-one-deploy-dev' \
  > /var/lib/forcitis-deploy/.ssh/authorized_keys
chown forcitis-deploy:forcitis-deploy /var/lib/forcitis-deploy/.ssh/authorized_keys && chmod 0600 /var/lib/forcitis-deploy/.ssh/authorized_keys
echo 'forcitis-deploy ALL=(root) NOPASSWD: /usr/local/sbin/forcitis-one-release' > /etc/sudoers.d/forcitis-deploy
chmod 0440 /etc/sudoers.d/forcitis-deploy && visudo -cf /etc/sudoers.d/forcitis-deploy
```

安装器更新：新版本的 `deploy/remote_install.sh` 与已装的不同，部署会因「服务器文件不一致」失败；运维以同样的 `install` 命令替换后再部署。

### R-13 GitHub 只读令牌（用户步骤）

ECS 用它读部署记录、工作流运行、标签与 Release 附件；它只读，写不了任何东西、建不了部署记录、触发不了安装。**每个环境一个令牌**（dev、prod 各一，只装在该环境的 ECS 上）。

1. 用户在 github.com 右上角头像 → Settings → Developer settings → Personal access tokens → **Fine-grained tokens** → Generate new token：
   - Token name：`forcitis-one-ecs-release-dev`（prod 为 `forcitis-one-ecs-release-prod`）；
   - Resource owner：**FORCITIS**；
   - Expiration：不超过 90 天（可选 Custom）；记下页面显示的到期日；
   - Repository access：**Only select repositories** → `forcitis-one-psa`；
   - Permissions → Repository permissions：**Contents: Read-only**、**Deployments: Read-only**、**Actions: Read-only**；**Metadata: Read-only** 为必选（自动勾上）；其余一律 No access，不给任何 Organization / Account 权限。
   - 组织若要求审批 fine-grained 令牌，生成后由组织所有者在组织 Settings → Personal access tokens → Pending requests 批准，批准前令牌读不到仓库。
2. 交付：令牌**只经 one-ops 会话的安全输入窗口**交给运维（不在聊天里粘贴、不截图、不发消息）；同时告知到期日（`YYYY-MM-DD`，不是机密，可在聊天中说）。运维写入 `/etc/forcitis-one/github-release-token`（root 0600，一行）与 `github-release-token.expires`（root 0644），然后做 R-11 的 ①②。
3. 轮换（到期前 14 天收到 P2 告警，或计划内每 90 天）：用户按第 1 步生成新令牌（名称可加日期）→ 按第 2 步交付，运维覆盖两个文件 → 运维执行 R-11 ① 确认新令牌可用 → 用户在 GitHub 删除旧令牌。令牌过期后部署失败关闭（安装器拒绝），回滚不受影响。
4. 疑似泄露：用户立即在 GitHub 删除该令牌，再按第 3 步换新。泄露的后果：能读本仓库的代码、部署记录与 Actions 运行（只读）。

## 8. 发布包到 ECS 的通路

用户决定：「先测 GitHub 直拉，可行就用 (Recommended)」。在 ECS 上实测（北京时间夜间）从 `release-assets.githubusercontent.com` 下载约 **9 MB/s**，可行；因此不经 OSS 中转、不建发布用 bucket（原 R-3 取消）。部署作业只建一条 GitHub deployment，再触发 ECS 上**固定的**安装入口；ECS 用本机的只读令牌向 GitHub 核对这条记录与建它的运行，自取 Release 附件，再用本机的公钥验签。调用方传不进任何命令、地址或文件。

```
部署 runner ──(1) GITHUB_TOKEN 建 deployment：ref v<版本>，payload 版本 / 运行号 / 提交──────────────▶ GitHub
     │                                                                                              ▲
     │ (2a) 云助手 InvokeCommand（固定命令，无参数）                                                  │ (3) 只读令牌：部署记录、运行、标签、
     │ (2b) SSH 受限密钥（强制命令）                                                                   │     Release 附件（HTTPS 直拉）
     └───────────────▶ 杭州 ECS：forcitis-one-release install-pending ──────────────────────────────┘
部署 runner ──(4) 按执行结果写 deployment 状态 success / failure──────────────────────────────────▶ GitHub
```

### 8.1 最后一跳对比

| 对比项 | (a) 云助手固定命令（**推荐**） | (b) SSH 受限密钥 |
|---|---|---|
| 所需账号与权限 | 部署用 RAM 子账号：对**该命令 ID 与该实例**的 `ecs:InvokeCommand`，及 `ecs:DescribeInvocationResults`、`ecs:DescribeInvocations`。不授 `RunCommand`、`CreateCommand` | 不需要 RAM 子账号；ECS 上部署用户、sudoers 只放行安装器、强制命令 + `from=` |
| 密钥存放 | GitHub environment secret：AccessKey 一对；ECS 上 GitHub 只读令牌（R-13） | SSH 私钥 + 主机公钥；服务器上一份授权公钥；ECS 上 GitHub 只读令牌 |
| 网络与安全组 | 无需任何入方向端口；ECS 出方向 443 到 GitHub（实测已通） | 另需入方向 TCP 22 只对部署 runner 固定出口 IP/32；无固定出口 IP 时不可用 |
| 与现有约定 | 与「ECS 不设登录密钥，运维经云助手操作」一致 | 改变该约定 |
| 审计 | 云助手执行记录（命令、输出、退出码）；ActionTrail 记 RAM 调用；GitHub deployment 及其状态；ECS 的 `processed-deployments` | GitHub deployment + ECS 的 sshd / sudo 日志与 `processed-deployments`；云端无记录 |
| 失败与回滚 | 安装器自动回切；输出（最多约 24 KB 尾部）打回 Actions；命令超时 45 分钟由云助手终止——此时 `current` 未切换，重新运行 deploy 即可（新的部署记录） | 安装器自动回切；输出实时回传；断线时以 `status` 与 `HISTORY` 判断 |
| 触发凭据泄露的后果 | AccessKey 能触发该环境的固定命令、读该实例该命令的执行结果（安装器输出，不含凭据）。没有「正在运行的、main 上手动触发的 deploy.yml」所指且未处理过的最新部署记录时，安装器拒绝，所以 AccessKey **单独装不上任何东西**；不能执行任意命令 | SSH 私钥泄露同样只能触发 `install-pending`，后果同左 |
| 只读令牌泄露的后果 | 能读本仓库代码、部署记录、Actions 运行；不能写、不能建部署记录、不能触发安装 | 同左 |

能让 ECS 装上一个版本的前提：在 main 上运行 deploy.yml 并通过 gate、environment 分支策略与 runner hook；所装的是 release 工作流签名的 Release；不低于当前版本；prod 不是预发布。推荐 (a)。

### 8.2 RAM 策略（草稿）

**部署用户**（以 dev 为例；prod 换命令 ID 与实例）。`<account-id>`、`<command-id>` 由用户填；保存前用 RAM 控制台校验，资源格式以控制台提示为准。SSH 方式不需要该子账号。**发布不需要 ECS 实例 RAM 角色**，原「实例角色」策略删除。

```json
{
  "Version": "1",
  "Statement": [
    {
      "Effect": "Allow",
      "Action": ["ecs:InvokeCommand"],
      "Resource": [
        "acs:ecs:cn-hangzhou:<account-id>:command/<command-id>",
        "acs:ecs:cn-hangzhou:<account-id>:instance/i-bp122xlpar8w2uylmal2"
      ]
    },
    {
      "Effect": "Allow",
      "Action": ["ecs:DescribeInvocationResults", "ecs:DescribeInvocations"],
      "Resource": [
        "acs:ecs:cn-hangzhou:<account-id>:command/<command-id>",
        "acs:ecs:cn-hangzhou:<account-id>:instance/i-bp122xlpar8w2uylmal2"
      ]
    }
  ]
}
```

两个查询接口限定到该命令与该实例；若 RAM 控制台校验提示不支持资源级授权，只能退回 `*`，此时泄露者还能读到本账号其他云助手执行的输出，须在策略说明中记下这一点。可选：各条加 `"Condition": {"IpAddress": {"acs:SourceIp": ["<部署 runner 出口 IP>/32"]}}`。

### 8.3 GitHub 直拉的链路（杭州 ECS）

| 项 | 情况 | 处理 |
|---|---|---|
| 速度 | 北京时间夜间在 ECS 上实测 `release-assets.githubusercontent.com` 约 9 MB/s；60 MB 左右的发布包约 7 秒 | 白天未测：**首次部署时在白天再测一次**（R-11 ②，或首次部署安装器日志中「fetched … bytes」前后的时间），结果记入本节 |
| 可用性 | 经公网跨境，偶有抖动或中断 | 安装器下载 20 分钟超时；失败即本次部署失败，重新运行 deploy（每次运行建新的部署记录） |
| 访问的地址 | `api.github.com`（部署记录、运行、标签、Release 元数据与附件 API）；附件经重定向从 GitHub 的下载域名取（实测为 `release-assets.githubusercontent.com`） | ECS 出方向 443 已放行（E-14），实测可达 |
| API 用量 | 每次部署约 6–10 次请求，远低于令牌的每小时限额 | 无需处理 |
| GitHub 不可用 | 期间无法部署 | 回滚只用本机已装版本，不受影响 |

## 9. 首次部署（dev）的先后

1. C-1（扩容，可后置）→ C-3（建库、角色、口令；RDS SSL 与 CA）→ C-4 E-1..E-10（ECS 初始化，含 E-3、E-5、E-9、E-10 新做法）→ B-4（证书，nginx 启用）。
2. 本文 R-0..R-13（R-3 不再需要；R-13 的令牌由用户生成）；B-2、B-3 完成后填 R-2 的构建期变量。
3. 1a 修复批合并、opus 复审通过，调度会话删除 `status/blockers/080-*`（或其 `blocks: deploy` 标记）；1a 的「可执行 jar」修复已合并，否则 `build.sh` 拒绝出包。
4. `release` 发 `0.1.0`（或 `-rc.1`），核对后发布 Release。
5. `deploy` dev：首次的迁移凭据目录含 Flowable 安装项（§4）；部署成功后跑 C-3 的 V8，删除 Flowable 安装项。
6. C-5（首任超管与应急账号；运维命令未实现，阻塞于 blockers/070）。
7. INFRA_RUNBOOK §7 第 5 步冒烟、`ecs_check.sh --public-check`；D 节出口条件在 dev 上逐项验收。

prod 的部署还要求：正式版本（非 `-rc`）、清单 D 节全部 ✅、C-9 ✅、`prod-approvals.txt` 中有该构建的批准行（版本、提交、发布包 SHA256 都对上）。

## 10. 风险与已知限制

- **部署 runner 的隔离靠 R-0 落实**：runner 程序、hook 与其配置归 root；PATH 与所用程序均为 root 所有或按 SHA256 下载；进程主目录为 root 所有的空目录、作业改用新 `HOME`；`forcitis-deploy` 非管理员。任何一项被放松，CI 作业就可能影响部署。
- release 构建在 GitHub 托管机上执行第三方依赖中的代码（Maven 插件、前端构建工具；依赖的安装脚本已用 `--ignore-scripts` 关掉），它们能影响本次构建产物，这一点任何套餐都不能消除；签名在另一个托管作业中，私钥不与之同机。
- AccessKey、SSH 私钥、只读令牌泄露的后果见 §8.1：单独都装不上任何东西；怀疑泄露时立即在 RAM 控制台禁用 AccessKey、在 GitHub 删除令牌（R-13）。
- **部署记录与运行的绑定是「引用」而非签名**：安装器核对记录所指的运行是 main 上手动触发、正在运行、`run-name` 为同一版本与环境的 deploy.yml，并要求只有一条记录指向该运行，但不能证明记录就是该运行建的。另一个持有 `deployments: write` 的工作流运行最多只能在某个 deploy 运行进行时，抢先建一条指向它的记录：若两条都在，安装器拒绝；若只有它的，所装的也只能是该 deploy 运行正在部署的同一版本、同一提交（`run-name`、标签与签名声明都已绑定）。需要更强绑定时再加由部署作业签名的部署声明。
- deploy 作业以标签 `v<版本>` 为 `ref` 建部署记录，而 dev / prod 的「Deployment branches and tags」只允许 main。GitHub 文档未写明经 REST 建记录是否受该策略约束；若首次部署时建记录被拒，经用户确认后在 dev / prod 加标签规则 `v*`（作业仍只在 main 上运行，由 `if:` 与 runner hook 保证）。
- `remote_install.sh`、`ship.sh`、`toolchain.sh` 依赖 Linux / 阿里云接口 / GitHub API / 网络下载，本机只做了语法、静态检查与可本地运行部分的验证（部署记录核对由 `install_request_test.sh` 以预置应答覆盖）；`aliyun ecs` 的参数与 GitHub 附件下载在 R-11 演练时核对；首次在 dev 部署时验证。迁移以 `Started OneApplication` 判定是在 migrate profile 合入前的过渡做法（§4）。
- GitHub 直拉的白天速度未测（§8.3），首次部署时补测。
- 前端按环境各构建一份，同一版本 dev 与 prod 的静态文件不是同一份产物（§6）。

## 11. GitHub Team 计划下的可用性与替代（2026-10-14 查官方文档）

组织 FORCITIS 为 GitHub Team 计划（`gh api orgs/FORCITIS` → `plan: team`），仓库为私有。下表逐项依据 docs.github.com（只读查阅）；文档未写明而由只读 API 观察到的，单独注明。

### 11.1 逐项结论

| 功能 | Team 私有仓库 | 依据 | 本流水线的用法 |
|---|---|---|---|
| Artifact attestations（出处证明） | **不可用**：「If you are on a GitHub Free, GitHub Pro, or GitHub Team plan, artifact attestations are only available for public repositories. To use artifact attestations in private or internal repositories, you must be on a GitHub Enterprise Cloud plan.」 | [Using artifact attestations](https://docs.github.com/en/actions/how-tos/secure-your-work/use-artifact-attestations/use-artifact-attestations) | 不用；改为签名发布声明（§2、§3） |
| Environments | 可用：「Organizations with GitHub Team and users with GitHub Pro can configure environments for private repositories.」 | [Managing environments for deployment](https://docs.github.com/en/actions/managing-workflow-runs-and-deployments/managing-deployments/managing-environments-for-deployment) | `release`、`dev`、`prod` |
| Required reviewers | **不可用**：「If you are on a GitHub Free, GitHub Pro, or GitHub Team plan, required reviewers are only available for public repositories.」 | [Deployments and environments](https://docs.github.com/en/actions/reference/workflows-and-actions/deployments-and-environments) | 不用；prod 改为 `prod-approvals.txt` 批准行 |
| Wait timer | **不可用**：「… wait timers are only available for public repositories.」 | 同上 | 不用 |
| Custom deployment protection rules | **不可用**：「Custom deployment protection rules are only available for public repositories for users on GitHub Free, GitHub Pro, and GitHub Team plans.」 | 同上 | 不用 |
| Deployment branches and tags | 可用：「For users on GitHub Pro or GitHub Team plans, deployment branches and tags are also available for private repositories.」 | 同上 | `release`、`dev`、`prod` 只允许 main |
| Environment secrets | 可用：私有仓库须 GitHub Pro、Team 或 Enterprise | 同上 | `RELEASE_SIGNING_KEY`、阿里云 AccessKey、SSH 密钥 |
| Runner groups | 可用（组织级）：「Organization owners using the GitHub Team plan can create additional organization-level runner groups.」 | [Managing access to self-hosted runners using groups](https://docs.github.com/en/actions/how-tos/manage-runners/self-hosted-runners/manage-access) | 专用 runner 注册在仓库级 |
| Runner group 只限指定工作流（Workflow access → Selected workflows） | **视为不可用**：该节只出现在 Enterprise Cloud 版文档，Free / Pro / Team 版同一页没有；组织现有 runner group 的 `restricted_to_workflows` 均为 `false` | [Enterprise Cloud 版同页](https://docs.github.com/en/enterprise-cloud@latest/actions/how-tos/manage-runners/self-hosted-runners/manage-access) 对照 [Team 版](https://docs.github.com/en/actions/how-tos/manage-runners/self-hosted-runners/manage-access) | 改由 runner 侧 pre-job hook 实现 |
| Pre-job hook（`ACTIONS_RUNNER_HOOK_JOB_STARTED`） | 可用（runner 功能，不分套餐）：「If there is any other exit code, the job will not run and will be marked as failed.」；「Scripts have access to the default variables. The full webhook event payload can be found in `GITHUB_EVENT_PATH`.」 | [Running scripts before or after a job](https://docs.github.com/en/actions/how-tos/manage-runners/self-hosted-runners/run-scripts) | `runner_job_started.sh` |
| Immutable releases | 文档未写套餐限制；本仓库 `GET /repos/FORCITIS/forcitis-one-psa/immutable-releases` 返回 `{"enabled":false}`，判断为可用、未启用；「immutability will only apply to future releases」 | [Immutable releases](https://docs.github.com/en/code-security/supply-chain-security/understanding-your-software-supply-chain/immutable-releases)、[Preventing changes to your releases](https://docs.github.com/en/code-security/how-tos/secure-your-supply-chain/establish-provenance-and-integrity/prevent-release-changes) | R-9 启用；与签名声明互补 |
| Rulesets（含 tag rulesets） | 可用：「Rulesets are available in public repositories with GitHub Free and GitHub Free for organizations, and in public and private repositories with GitHub Pro, GitHub Team, and GitHub Enterprise Cloud.」 | [About rulesets](https://docs.github.com/en/repositories/configuring-branches-and-merges-in-your-repository/managing-rulesets/about-rulesets) | R-8 `v*` 标签保护 |
| Push rulesets（按文件路径限制推送） | 可用：「Push rulesets are available for the GitHub Team plan in internal and private repositories …」 | 同上 | 可选，见 U-3 |

### 11.2 H-2 的 Team 方案：签名发布声明

目标不变：只部署由 main 上 release 工作流构建的包，Release 附件被替换能发现。

- **签名**：私钥只存在 environment `release` 的 secret 中，该 environment 只允许 main；`publish` 在 GitHub 托管的 `ubuntu-24.04` 上运行，只检出工作流自身提交的 `scripts/release`，签完立即删除私钥文件。声明写明 `GITHUB_WORKFLOW_REF` 与已证明的提交，并断言等于 `manifest.json` 的 `gitSha`。
- **校验两处**：deploy 作业用仓库 main 上的 `allowed_signers` 验签；ECS 安装器用本机 root 所有的 `/etc/forcitis-one/release-signers` 再验一次，且发布包、声明、签名都由 ECS 以只读令牌从 GitHub 自取。两处都核对构建工作流必须是 `FORCITIS/forcitis-one-psa/.github/workflows/release.yml@refs/heads/main`、提交、发布包名，以及发布包与 `manifest.json`、`SHA256SUMS` 的 SHA256。
- 本机验证：用运行时生成的测试密钥，正常声明通过；空公钥、改提交、改声明、换密钥、分支工作流签出的声明、替换发布包、写声明时提交与 manifest 不符，全部被拒。
- **专用 runner 被任意分支利用的封堵**：pre-job hook（R-0）只放行 main 上手动运行的 deploy.yml，并把 `GITHUB_*` 变量与 runner 写在 `_work/_temp` 的事件载荷交叉核对；hook、其环境变量（在 root 所有的 plist 中）与 runner 程序都归 root。hook 不再做残留进程检查：那只能报告、不能阻止，不算控制。

### 11.3 用户决定（升级 / 接受）

| # | 事项 | 需要 Enterprise Cloud 才能做到的 | Team 下现状 | 选项 / 结论 |
|---|---|---|---|---|
| U-1 | 出处由 GitHub 背书 | 私有仓库的 artifact attestations | 签名发布声明 | **接受**（建议） / 升级后改回 attestations |
| U-2 | prod 部署前由指定的人在 GitHub 上批准 | environment required reviewers、wait timer | `prod-approvals.txt` 批准行（版本、提交、发布包 SHA256）经 PR 合入 main；所有会话以同一 GitHub 账号推送与合并，批准行证明「经过 main 的评审流程」，不能证明「由某个特定的人批准」 | **接受**并约定批准行只由用户本人发起的 PR 加入 / 升级 |
| U-3 | runner 只接指定工作流 | runner group 的 Selected workflows | runner 侧 pre-job hook + R-0 的 root 所有权 | **接受**（建议）/ 升级 / 另加 push ruleset 限制 `.github/workflows/**`（须先让代理改用单独账号才有意义） |
| U-4 | 签名私钥与构建依赖代码同机 | — | **已决定**：签名放 GitHub 托管机器（用户：「签名步骤放 GitHub 托管机器 (Recommended)」）；随后发布打包也移到托管机器（用户：「发布打包改到 GitHub 托管机器 (Recommended)」），部署 runner 只跑 deploy | 已实施 |
| U-5 | prod 只装已批准的构建，即使部署凭据泄露 | — | 批准行在仓库中、由 deploy 作业核对，ECS 不知道批准；改为 GitHub 直拉后，持有 prod AccessKey 者单独已装不上任何东西（§8.1）；ECS 只装正在运行的 deploy.yml 所部署的那个版本（`run-name` 绑定，§3），而该运行在建记录前已核对批准行。残余：批准仍由 deploy 作业而非 ECS 验证（§10）。未做「批准签名由 ECS 验证」：immutable releases 下 Release 附件发布后不可追加，批准签名需要另一套签发与存放流程（例如单独的批准工作流、由 `release` environment 的密钥签批准声明并另存），成本与本阶段收益不相称 | **接受**（prod AccessKey 只在 prod environment、仅 main、仅部署 runner 上出现）/ 生产前再加批准签名 |
