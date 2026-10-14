# 发布与部署（release / deploy 工作流）

整理：one-coordinator 子代理，2026-10-14。补 `PREDEPLOY_CHECKLIST.md` 遗漏项「CI 无发布产物」与 `CROSSCHECK.md` 第 15 条；按 PR #59 的用户决定（法兰克福 OSS 中转、专用部署 runner）与 opus 安全复审修订。

**当前禁止部署任何环境**（`status/blockers/080`，带 `blocks: deploy` 标记）。`deploy` 工作流在该阻塞存在时直接失败；本文的一次性步骤只是准备，涉及 GitHub 设置、阿里云、ECS 的每一步仍须用户确认后执行（清单 B-10）。

| 文件 | 作用 |
|---|---|
| `.github/workflows/release.yml` | 由 main 上已通过 ci 的提交构建发布包、生成出处证明、附到 GitHub Release |
| `.github/workflows/deploy.yml` | 把已发布（非草稿）的版本部署到 dev / prod；只写好，未运行过 |
| `scripts/release/build.sh` | 构建发布包；本机可直接运行，工作流只是调用它 |
| `scripts/release/toolchain.sh` | 专用 runner 上的工具链：Homebrew 的 Java 25；按版本与 SHA256 锁定下载的 Node 24、aliyun CLI；corepack 取锁定的 pnpm |
| `scripts/release/verify_bundle.sh` | 按部署时的方式校验发布包（归档 SHA256、`SHA256SUMS`、`manifest.json`、jar 可启动） |
| `scripts/release/ci_passed.py` | 证明提交是 main 的 first-parent 提交，且该提交 push 到 main 触发的 ci 运行成功、三项必需作业成功 |
| `scripts/release/deploy_gate.py` | 部署守护：阻塞条目、prod 的版本与前置条件 |
| `scripts/release/ship.sh` | 部署最后一段：发布包传到法兰克福 OSS、签发短时下载地址、经云助手或 SSH 让 ECS 拉取安装 |
| `scripts/release/remote_install.sh` | ECS 上的安装器（装为 `/usr/local/sbin/forcitis-one-release`） |

## 1. 版本号

- 形如 `MAJOR.MINOR.PATCH`，预发布加 `-rc.N`（`0.1.0`、`0.1.0-rc.1`）；标签为 `v<版本>`。Phase 1a 期间用 `0.x`。预发布版本只能部署 dev。
- 一个版本只构建一次：同名 Release 已存在则 `release` 失败；仓库启用 immutable releases（R-9）后，发布后的附件与标签不可再改；ECS 上同一版本内容不同则拒绝安装。修复一律发新版本。
- `backend/pom.xml` 的 `<version>` 目前固定为 `0.0.1`，jar 内的 `Implementation-Version` 与发布版本不一致；以 `manifest.json` 的 `version` 与 `gitSha` 为准（给 1a 的建议见 PR）。

## 2. 发布（`release`）

触发：只能**在 main 上手动运行**（输入版本、可选提交 sha、是否草稿，默认草稿）。不设标签推送触发：出处证明要求签发工作流运行在 `refs/heads/main`，标签触发的运行不满足；两个作业都有 `if: github.ref == 'refs/heads/main'`。`deploy` 只接受已发布的 Release，草稿经人核对后在 GitHub 上点「Publish」。

`build` 作业（专用 runner `forcitis-one-deploy`，权限 `contents: read`、`actions: read`；checkout 不保存凭据）：

1. 输入校验（版本格式、sha 格式）后才写入作业输出。
2. **证明这个提交测过**（`ci_passed.py`）：
   - 提交必须在 `git rev-list --first-parent origin/main` 中——即 main 上的合并提交或直接提交；被合并分支中间的提交（祖先但不在 first-parent 链上）一律拒绝；
   - 按工作流运行判定：`actions/runs?head_sha=<sha>&event=push&branch=main` 中 `path` 为 `.github/workflows/ci.yml` 的最新一次运行 `completed/success`，且其三项必需作业（backend verify、frontend verify、secret scan）都是 `success`；pull_request 事件的运行、其他分支或其他工作流的运行都不算。不重跑测试。作业名取自 `ci.yml`，改名而脚本未同步时直接失败。
3. 同名 Release 不存在；同名标签若已存在必须指向该提交。
4. 工具链（`toolchain.sh build`）与**隔离缓存**：Maven 仓库与 pnpm store 都放在本次运行的 `$RUNNER_TEMP`（`--isolated-caches`），不复用任何其他作业留下的依赖。
5. **构建**（`build.sh`）：工作区必须干净；校验 Java 25、Node 24、锁定的 pnpm；
   - 后端：`./mvnw -B clean package -DskipTests`。POM 尚未声明 `spring-boot-maven-plugin` 时，脚本临时加 `repackage` 目标并指定启动类，然后检查 jar 能被 `java -jar` 启动（Boot 可执行 jar、Start-Class 是 `OneApplication`、该类有 `main`），不满足即失败。
   - 前端：PC（web）与 H5 按 dev、prod 各构建一份（构建期变量不同，见 §6），H5 放在 PC 产物的 `h5/` 下。
6. 打包、`verify_bundle.sh` 复核，作为 Actions artifact（保留 1 天）交给下一作业。

`publish` 作业（同一专用 runner，权限 `contents: write`、`id-token: write`、`attestations: write`，不 checkout 代码）：核对 artifact 的 SHA256 → `actions/attest-build-provenance`（锁定提交 SHA）为发布包生成出处证明 → `gh release create v<版本> --target <sha>`，附发布包与 `.sha256`。

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
2. `deploy` 作业（`environment: dev / prod`；prod 须 required reviewers 批准后才开始）跑在**专用部署 runner**（标签 `forcitis-one-deploy`，macOS 用户 `forcitis-deploy`，见 `CI_RUNNERS.md`），权限 `contents: read`、`actions: read`、`attestations: read`，checkout 不保存凭据：
   - 再跑一次 `deploy_gate.py`（等待审批期间 main 可能新增阻塞）；
   - 工具链（`toolchain.sh deploy`：Java 25、锁定版本与 SHA256 的 aliyun CLI）；
   - Release 非草稿；标签经 API 解析出的提交满足 `ci_passed.py`；
   - 下载附件 → `gh attestation verify --signer-workflow FORCITIS/forcitis-one-psa/.github/workflows/release.yml --source-ref refs/heads/main`（证明发布包由 main 上的 release 工作流构建）→ `verify_bundle.sh` → `manifest.json` 的 `gitSha` 必须等于标签所指提交；
   - `ship.sh`：
     1. 已校验的 `forcitis-one-<版本>.tar.gz`、`.sha256`、包内 `manifest.json` 与 `SHA256SUMS` 上传到 `oss://<OSS_BUCKET>/<OSS_PREFIX>/<版本>/`（法兰克福 `eu-central-1`），回读 `.sha256` 核对；
     2. 为发布包签发 30 分钟有效的只读下载地址（可走传输加速域名，§8.3），在 Actions 日志中屏蔽；
     3. 最后一跳（`DEPLOY_TRIGGER`，§8）：云助手 `RunCommand` 在 ECS 上以 root 运行，或受限 SSH 密钥的强制命令（`ssh -F /dev/null … -l <用户> -- <主机>`），二者都执行 `forcitis-one-release install <版本> <环境> <发布包 sha256> <提交 sha>`，下载地址经标准输入传入；
     4. 云助手方式轮询执行结果，把安装器输出打回 Actions 日志，退出码非 0 即作业失败。
   - 凭据只来自 environment secret，经环境变量交给 `aliyun` CLI，不执行 `aliyun configure`（会在用户目录留下凭据），不打印；SSH 私钥写入 `ship.sh` 的临时目录，脚本退出时（含失败）由 `trap` 删除。

### ECS 上的安装器（`remote_install.sh`）

云助手以 root 直接运行安装器；SSH 方式下 deploy key 在 `authorized_keys` 中是强制命令，只能运行安装器的 `install`、`rollback`、`status`，拿不到 shell，安装器以 sudo（仅此一个命令）切到 root。`install` 依次：

1. 检查 `/etc/forcitis-one/one.env` 的 `SPRING_PROFILES_ACTIVE` 等于目标环境；**断言 `/data/forcitis-one` 与 `releases` 为 root 所有、组和其他人不可写**（root 在其中操作，应用用户不得能替换任何东西，C-4 E-5）；取部署锁（`/run/forcitis-one-release.lock`）。
2. 从 OSS 下载发布包（只接受 `https://…aliyuncs.com/` 地址，≤ 1 GiB，重试 5 次，20 分钟超时；地址不写日志），SHA256 与部署作业传入的一致；归档条目只能在 `forcitis-one-<版本>/` 下，无链接、无 `..`；解包后 `sha256sum --strict -c SHA256SUMS`，不允许有清单外的文件；`manifest.json` 的 `version` 与 `gitSha` 都与传入的一致。
3. **服务器文件一致**（新装的版本）：已安装的安装器、systemd 单元、nginx 配置、logrotate 与发布包中 `deploy/` 的同名文件逐字节相同，`one.env` 含模板中的每个键；不一致即失败并列出（§5）。
4. 放到 `/data/forcitis-one/releases/<版本>/`（root:forcitis，目录 750、文件 640），`web` 软链指向 `web-<环境>`，给 nginx 用户只读 ACL。已装过且内容一致的版本直接复用，不改 `web` 软链；装给另一环境的同版本拒绝。
5. 迁移（§4），**每次部署都运行**：有新迁移时应用它们，没有时由 Flyway 校验已应用的迁移与本版本一致。
6. 原子切换 `current` 软链，`systemctl restart forcitis-one`，经本机回环取 `http://127.0.0.1:<MANAGEMENT_SERVER_PORT>/actuator/health`，解析 JSON 顶层 `status` 为 `UP`（最长 180 秒）。
7. 不健康：自动切回原来的 `current` 并重启，原版本健康则以「已回切」报错退出，原版本也不健康则报「需人工处理」；首次部署没有上一版本时停止服务并报错。
8. 健康：上一版本记为 `previous`；保留最近 5 个版本（`current`、`previous` 之外），更旧的删除；每次结果追加到 `releases/HISTORY`。

### 回滚

- 首选：用 `deploy` 部署上一个版本号。发布包照样经 OSS 中转、校验；ECS 上已装过且内容一致的版本直接复用，不再比对服务器文件（首次安装时已比对过），因此回到旧版本不会被「服务器文件不一致」挡住。迁移步骤照常运行，对旧版本只做校验（旧版本不认识的新迁移被 Flyway 默认的 `*:future` 忽略）。
- 应急（GitHub 不可用）：运维经云助手执行 `forcitis-one-release rollback`（回到 `previous`）或 `forcitis-one-release rollback <版本>`；`forcitis-one-release status` 查看当前、上一版本与历史。
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

本文不建 bucket、RAM 账号、AccessKey、environment 或 secret，下列各项由用户或运维执行。**R-0 是必需前置条件**：没有专用 runner，release 与 deploy 作业不会被任何 runner 接走。

| # | 步骤 | 位置 / 做法 |
|---|---|---|
| R-0 | **专用 runner（必需）**：已由调度会话注册（`mac-one-deploy`，标签 `forcitis-one-deploy`，macOS 用户 `forcitis-deploy`，主目录 700，见 `CI_RUNNERS.md`）。该用户不得加入管理员组；Homebrew 的 `openjdk@25` 须存在（`toolchain.sh` 检查） | 本机 |
| R-1 | 建 GitHub environments `dev`、`prod`；两者「Deployment branches and tags」只允许 `main`；`prod` 设 required reviewers（至少 1 人，建议 2 人，勾选禁止自己批准自己发起的部署） | 仓库 Settings → Environments |
| R-2 | 仓库级 Actions variables：§6 的 8 个构建期变量（B-2、B-3 完成后填） | Settings → Secrets and variables → Actions → Variables |
| R-3 | OSS：在**法兰克福（eu-central-1）**建私有 bucket（阻止公共访问、服务端加密、生命周期规则：前缀下对象 30 天后删除——GitHub Release 才是正本，OSS 只作中转）；按 §8.3 决定是否开传输加速 | 阿里云 OSS 控制台 |
| R-4 | RAM：建 `forcitis-one-deploy-dev`、`forcitis-one-deploy-prod` 两个子账号（只开 OpenAPI 访问），各绑 §8.2 的策略（前缀与实例按环境填）；各生成一对 AccessKey；每 90 天轮换 | 阿里云 RAM 控制台 |
| R-5 | 每个 environment 的 secrets：`ALIBABA_CLOUD_ACCESS_KEY_ID`、`ALIBABA_CLOUD_ACCESS_KEY_SECRET`（R-4 对应环境的那一对）；选 SSH 方式时另加 `DEPLOY_SSH_KEY`（该环境专用 ed25519 私钥）、`DEPLOY_KNOWN_HOSTS`（ECS 主机公钥一行，由运维经云助手读 `/etc/ssh/ssh_host_ed25519_key.pub` 交来，不用 `ssh-keyscan`） | Environments → dev / prod |
| R-6 | 每个 environment 的 variables：`OSS_BUCKET`、`OSS_REGION=eu-central-1`、`OSS_PREFIX`（如 `forcitis-one/dev`）、可选 `OSS_DOWNLOAD_ENDPOINT`（传输加速时 `oss-accelerate.aliyuncs.com`）、`DEPLOY_TRIGGER`（`cloud-assistant` 或 `ssh`）；云助手方式 `ECS_REGION=cn-hangzhou`、`ECS_INSTANCE_ID=i-bp122xlpar8w2uylmal2`；SSH 方式 `DEPLOY_HOST`、`DEPLOY_USER=forcitis-deploy`、可选 `DEPLOY_PORT` | Environments → dev / prod |
| R-7 | ECS（运维经云助手，root）：C-4 E-3、E-5 的新做法（`forcitis-migrate` 用户；`/data/forcitis-one`、`releases` 归 root；nginx 穿过 ACL；`/etc/forcitis-one/migrate`）；安装器（下方命令块）；SSH 方式另建部署用户与受限密钥 | ECS |
| R-8 | 标签保护：Rules → Rulesets 新建标签规则 `v*`，限制创建、更新、删除（仅管理员与 release 工作流） | Settings → Rules |
| R-9 | **启用 immutable releases**（发布后附件与标签不可改）；确认 Actions 允许工作流声明 `contents: write`、`id-token: write`、`attestations: write`；确认组织套餐支持**私有仓库的 artifact attestations**（GitHub 文档：需 GitHub Enterprise Cloud），不支持时 `publish` 的出处证明一步失败，须先解决再发布 | Settings → General / Actions；组织设置 |
| R-10 | 首次部署前 `/etc/forcitis-one/one.env` 补 `SPRING_FLYWAY_ENABLED=false`；按 §4 在 `/etc/forcitis-one/migrate/` 放迁移凭据文件（首次另加 Flowable 安装项） | ECS |
| R-11 | 设置完成后的连通演练（不部署）：部署 runner 上用 dev 凭据 `aliyun oss ls oss://<bucket>/<前缀>/`；云助手对 ECS 执行 `forcitis-one-release status`；ECS 上 `curl` 一个签名地址并记录下载速度（§8.3） | 运维 |

R-7 命令（`<version>` 用将要部署的发布包；先在可信机器上 `verify_bundle.sh` 校验，再经云助手上传其中的 `deploy/remote_install.sh`）：

```bash
install -o root -g root -m 0755 remote_install.sh /usr/local/sbin/forcitis-one-release
forcitis-one-release status

# 仅 SSH 方式：
useradd --system --create-home --home-dir /var/lib/forcitis-deploy --shell /bin/bash forcitis-deploy
install -d -o forcitis-deploy -g forcitis-deploy -m 0700 /var/lib/forcitis-deploy/.ssh
echo 'restrict,from="<部署 runner 出口 IP>",command="/usr/local/sbin/forcitis-one-release" ssh-ed25519 AAAA… forcitis-one-deploy-dev' \
  > /var/lib/forcitis-deploy/.ssh/authorized_keys
chown forcitis-deploy:forcitis-deploy /var/lib/forcitis-deploy/.ssh/authorized_keys && chmod 0600 /var/lib/forcitis-deploy/.ssh/authorized_keys
echo 'forcitis-deploy ALL=(root) NOPASSWD: /usr/local/sbin/forcitis-one-release' > /etc/sudoers.d/forcitis-deploy
chmod 0440 /etc/sudoers.d/forcitis-deploy && visudo -cf /etc/sudoers.d/forcitis-deploy
```

安装器更新：新版本的 `deploy/remote_install.sh` 与已装的不同，部署会因「服务器文件不一致」失败；运维以同样的 `install` 命令替换后再部署。

## 8. 发布包到 ECS 的通路

用户决定：「通过法兰克福 OSS 中转，下一步云助手还是 SSH 拟定」。前两段（runner → 法兰克福 OSS，ECS ← OSS）两种方案相同，差别只在最后一跳由谁触发 ECS 去拉取并安装。`ship.sh` 两种都已实现，以 environment variable `DEPLOY_TRIGGER` 选择；安装器不区分。

```
部署 runner ──HTTPS 上传──▶ 法兰克福 OSS（私有、30 天生命周期）
     │                              ▲
     │ (a) 云助手 RunCommand          │ HTTPS 下载（30 分钟签名地址，可走传输加速）
     │ (b) SSH 受限密钥               │
     └──────────────▶ 杭州 ECS ───────┘ → forcitis-one-release install
```

### 8.1 最后一跳对比

| 对比项 | (a) 云助手 RunCommand（**推荐**） | (b) SSH 受限密钥 |
|---|---|---|
| 所需账号与权限 | RAM 子账号（每环境一个）：该 bucket 前缀写入与读取（读取用于签发下载地址），对**指定实例**执行云助手命令、查询执行结果（§8.2） | 同一 RAM 子账号去掉云助手权限，只留 OSS 前缀读写；ECS 上部署用户 `forcitis-deploy`、sudoers 只放行安装器、`authorized_keys` 强制命令 + `from=` |
| 密钥存放 | GitHub environment secret：AccessKey 一对 | GitHub environment secret：AccessKey 一对（仍要上传 OSS）+ SSH 私钥 + 主机公钥；服务器上多一份授权公钥 |
| 网络与安全组 | 无需任何入方向端口，ECS 安全组保持只开 80 / 443；云助手经阿里云内部通道 | 入方向放行 TCP 22，源地址只填部署 runner 的固定出口 IP/32；出口 IP 变化要同时改安全组与 `from=`；本机网络没有固定出口 IP 时不可用 |
| 与现有约定 | 与「ECS 不设登录密钥，运维经云助手操作」一致 | 改变该约定，须用户认可 |
| 审计 | 每次执行在云助手执行记录中留命令内容、输出、退出码；RAM 调用记入操作审计（ActionTrail）；GitHub 有部署记录与出处证明 | GitHub 部署记录 + ECS 的 sshd / sudo 日志（`/var/log/secure`，经 SLS 采集）；云端无记录 |
| 失败与回滚 | 安装器的自动回切相同；云助手输出（最多约 24 KB 尾部）打回 Actions 日志；命令超时 45 分钟后云助手终止进程，可能停在迁移中途——此时 `current` 未切换，重跑即可 | 安装器的自动回切相同；输出实时回传；SSH 断线时以 `forcitis-one-release status` 与 `HISTORY` 判断 |
| 主要风险 | AccessKey 泄露 → 可对该实例执行**任意** root 命令（策略只限实例，不能限命令内容） | 私钥泄露 → 只能运行安装器，但安装器只认传入的 SHA256，攻击者可传自己的包与 SHA256；AccessKey 泄露 → 只能改写 OSS 中转对象 |
| 签名下载地址的暴露 | 地址写在命令内容里，留在云助手执行记录中（有 `ecs:DescribeInvocations` 权限者可见，30 分钟后失效） | 地址经 SSH 标准输入传入，不落盘 |

推荐 (a)：不开入方向端口、不依赖固定出口 IP、与现行运维方式一致、云端有完整审计。它的代价是 AccessKey 的权限面比 SSH 强制命令大，靠以下措施收住：凭据只在专用部署 runner 上出现（R-0）、每环境独立的 RAM 子账号与最小策略（§8.2）、策略加 `acs:SourceIp` 限定 runner 出口 IP（有固定 IP 时）、AccessKey 每 90 天轮换、在 ActionTrail 对该子账号的 `RunCommand` 配告警（C-7 增一条）。

### 8.2 RAM 子账号最小权限策略（草稿）

以 dev 为例；prod 子账号把前缀换成 `forcitis-one/prod`。`<bucket>`、`<account-id>` 由用户填；保存前在 RAM 控制台用策略校验，资源格式以控制台提示为准。SSH 方式删除第二、三条。

```json
{
  "Version": "1",
  "Statement": [
    {
      "Effect": "Allow",
      "Action": ["oss:PutObject", "oss:GetObject"],
      "Resource": ["acs:oss:*:<account-id>:<bucket>/forcitis-one/dev/*"]
    },
    {
      "Effect": "Allow",
      "Action": ["ecs:RunCommand"],
      "Resource": [
        "acs:ecs:cn-hangzhou:<account-id>:instance/i-bp122xlpar8w2uylmal2",
        "acs:ecs:cn-hangzhou:<account-id>:command/*"
      ]
    },
    {
      "Effect": "Allow",
      "Action": ["ecs:DescribeInvocationResults"],
      "Resource": ["acs:ecs:cn-hangzhou:<account-id>:*"]
    }
  ]
}
```

可选：每条 Statement 加 `"Condition": {"IpAddress": {"acs:SourceIp": ["<部署 runner 出口 IP>/32"]}}`（有固定出口 IP 时）。不授予 `oss:ListObjects`、`oss:DeleteObject`（过期由生命周期规则删除）、`ecs:InvokeCommand`、`ecs:CreateCommand`。R-11 的 `aliyun oss ls` 演练若因缺 `oss:ListObjects` 被拒，属预期，改用 `aliyun oss stat` 一个已上传对象。

### 8.3 跨境链路（杭州 ECS 拉取法兰克福 OSS）

| 项 | 情况 | 处理 |
|---|---|---|
| 延迟 | 杭州到法兰克福往返通常 200 ms 以上，经公网跨境 | 对 60 MB 左右的发布包影响在秒级；安装器重试下载 |
| 带宽与可用性 | 跨境公网带宽不稳定，高峰期可能低到每秒数百 KB、偶有中断；ECS 公网带宽规格也是上限 | 安装器重试 5 次、20 分钟超时；R-11 先实测一次 |
| 费用 | 法兰克福 OSS 的外网流出流量按 GB 计费（每次部署约 0.06 GB），上传免费；传输加速另按 GB 计费；价格以阿里云官网为准 | 每次部署量很小，费用可忽略；生命周期规则控制存储费用 |
| 传输加速 | OSS 传输加速走阿里云全球网络，适合跨境下载；在 bucket 上开启后，把 `OSS_DOWNLOAD_ENDPOINT` 设为 `oss-accelerate.aliyuncs.com`，签名地址即走加速域名 | **建议**：先不开，R-11 实测；下载低于每秒 1 MB 或失败过，再开 |
| 同城 / 跨区域复制 | 把法兰克福 bucket 复制到杭州 bucket，ECS 走内网地址下载（快、内网流量免费） | 不建议：多一个 bucket 与复制延迟（分钟级，部署要等复制完成），且需要 ECS 读杭州 bucket 的另一套权限；只有加速仍不稳定时才考虑 |
| 出站放行 | ECS 出方向 443 已放行（E-14） | 无需改动 |

## 9. 首次部署（dev）的先后

1. C-1（扩容，可后置）→ C-3（建库、角色、口令；RDS SSL 与 CA）→ C-4 E-1..E-10（ECS 初始化，含 E-3、E-5 新做法）→ B-4（证书，nginx 启用）。
2. 本文 R-0..R-11；B-2、B-3 完成后填 R-2 的构建期变量。
3. 1a 修复批合并、opus 复审通过，调度会话删除 `status/blockers/080-*`（或其 `blocks: deploy` 标记）；1a 的「可执行 jar」修复已合并，否则 `build.sh` 拒绝出包。
4. `release` 发 `0.1.0`（或 `-rc.1`），核对后发布 Release。
5. `deploy` dev：首次的迁移凭据目录含 Flowable 安装项（§4）；部署成功后跑 C-3 的 V8，删除 Flowable 安装项。
6. C-5（首任超管与应急账号，用已部署的 jar 执行运维命令；运维命令未实现，阻塞于 blockers/070）。
7. INFRA_RUNBOOK §7 第 5 步冒烟、`ecs_check.sh --public-check`；D 节出口条件在 dev 上逐项验收。

prod 的部署还要求：正式版本（非 `-rc`）、清单 D 节全部 ✅、C-9 ✅、prod environment 的 reviewers 批准。

## 10. 风险与已知限制

- **专用 runner 是隔离的前提**：release 与 deploy 作业只在 `forcitis-one-deploy` 上运行，CI 作业所在的 4 个 runner（`yeliang` 用户）读不到它的主目录、`$RUNNER_TEMP` 与 environment secret；release 用本次运行内的全新 Maven / pnpm 缓存，PR 作业无法预先投放依赖。`forcitis-deploy` 用户不得加入管理员组；Maven Wrapper 下载的 Maven 发行包留在该用户主目录，`maven-wrapper.properties` 尚无 `distributionSha256Sum`（建议补上）。
- **AccessKey 的权限面**：方案 (a) 下 AccessKey 能对该实例执行任意 root 命令（§8.1）；怀疑泄露时立即在 RAM 控制台禁用。
- **私有仓库的出处证明**需要组织套餐支持（R-9），未确认前 `publish` 可能失败。
- `remote_install.sh`、`ship.sh`、`toolchain.sh` 依赖 Linux / 阿里云接口 / 网络下载，本机只做了语法与静态检查；`aliyun oss` / `aliyun ecs` 的参数在 R-11 演练时核对；首次在 dev 部署时验证。迁移以 `Started OneApplication` 判定是在 migrate profile 合入前的过渡做法（§4）。
- 跨境下载的速度与可用性未实测（§8.3）。
- 前端按环境各构建一份，同一版本 dev 与 prod 的静态文件不是同一份产物（§6）。
