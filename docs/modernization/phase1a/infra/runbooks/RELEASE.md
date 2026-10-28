# 发布与部署（release / deploy 工作流）

整理：one-coordinator 子代理，2026-10-14。补 `PREDEPLOY_CHECKLIST.md` 遗漏项「CI 无发布产物」与 `CROSSCHECK.md` 第 15 条；按 PR #59 的用户决定（专用部署 runner）与 opus 安全复审修订；发布包到 ECS 的通路按用户决定「先测 GitHub 直拉，可行就用 (Recommended)」改为 ECS 自取 GitHub Release，不经 OSS 中转（§8）；组织 FORCITIS 为 **GitHub Team 计划**，各项 GitHub 功能是否可用见 §11，凡需 Enterprise Cloud 的都不假定可用。2026-10-15 按用户决定「需要我做的，都让ops 会话自己做」改为 GitHub OIDC 换阿里云临时凭据、安装请求写入 OOS 参数、ECS 上的定时器以实例角色拉取请求（部署作业没有任何执行接口），ECS 不再持有 GitHub 令牌（§3、§7、§8；one-coordinator 批准）。

**当前禁止部署任何环境**（`status/blockers/080`，带 `blocks: deploy` 标记）。`deploy` 工作流在该阻塞存在时直接失败；本文的一次性步骤只是准备，涉及 GitHub 设置、阿里云、ECS 的每一步仍须用户确认后执行（清单 B-10）。

| 文件 | 作用 |
|---|---|
| `.github/workflows/release.yml` | 由 main 上已通过 ci 的提交构建发布包、签署发布声明、附到 GitHub Release |
| `.github/workflows/deploy.yml` | 把已发布（非草稿）的版本部署到 dev / prod；只写好，未运行过 |
| `scripts/release/build.sh` | 构建发布包；本机可直接运行，工作流只是调用它 |
| `scripts/release/toolchain.sh` | release 构建（GitHub 托管机）与部署 runner 的工具链；部署 runner 上失败关闭：系统工具与 PATH 必须 root 所有、组与他人不可写（含全部上级目录），账号主目录中不得有会左右 python3 / git / aliyun / curl 的配置；Java 25、gh、Node 24、aliyun CLI 按版本与 SHA256 锁定下载；corepack 取锁定的 pnpm |
| `scripts/release/verify_bundle.sh` | 按部署时的方式校验发布包（归档 SHA256、`SHA256SUMS`、`manifest.json`、jar 可启动） |
| `scripts/release/ci_passed.py` | 证明提交是 main 的 first-parent 提交；该提交 push 到 main 触发的 ci 运行（只跑轻量）成功、三项必需作业成功；**重步骤另证**：合并它的 PR 的头提交上，pull_request 事件的 ci 运行以完整作业名（不带 ` (light)` 后缀）三项成功，且合并提交与 PR 头只差纯文档路径（`relevant_change.py` 判定）；无此 PR 运行时兜底为对该提交本身的 schedule 或 workflow_dispatch 全量 ci 运行；直接推到 main 的提交不放行 |
| `scripts/release/deploy_gate.py` | 部署守护：阻塞条目、prod 的版本与前置条件 |
| `scripts/release/statement.sh` | 发布声明：写出（版本、提交、发布包与 manifest / SHA256SUMS 的 SHA256、构建工作流与运行号）、签名（`ssh-keygen -Y sign`）、校验 |
| `scripts/release/allowed_signers` | 发布签名公钥（`ssh-keygen` allowed_signers 格式）；空文件时部署一律失败 |
| `scripts/release/runner_job_started.sh` | 部署 runner 的 pre-job hook：只放行 main 上手动运行的 deploy.yml，并与 runner 写下的事件载荷交叉核对 |
| `docs/…/runbooks/prod-approvals.txt` | prod 部署批准行（经 PR 合入 main），代替 Team 计划下不可用的 required reviewers |
| `scripts/release/ship.sh` | 部署最后一段：把四个 Release 附件解析为 GitHub 的短期签名下载地址，经 GitHub OIDC 换取阿里云临时凭据，把安装请求写进该环境固定的 OOS 参数，轮询结果参数等待 ECS 回报 |
| `scripts/release/remote_install.sh` | ECS 上的安装器（装为 `/usr/local/sbin/forcitis-one-release`）：`pull` 由定时器每分钟运行，以实例角色从 OOS 读回并严格校验安装请求、下载附件、验签、安装、写结果参数；另有 `rollback`、`status` |
| `docs/…/runbooks/files/forcitis-one-release-pull.{service,timer}` | ECS 上的拉取服务与定时器（root，`Type=oneshot`，上一次结束 1 分钟后再运行），随发布包 `deploy/` 分发并纳入「服务器文件一致」检查 |
| `scripts/release/install_request_test.sh` | `pull` 的本地测试：本机替身服务扮演元数据服务（加固模式）与 OOS（核对签名、记录结果参数），运行时生成签名密钥、构建并签名测试附件（含篡改与混搭的变体），替身 curl 与告警命令；覆盖静默跳过、告警限频、单实例与签名的已知答案测试；只连 127.0.0.1、不需 root（ci 运行） |
| `scripts/release/ship_test.sh` | `ship.sh` 的本地测试：替身 gh / curl / aliyun，覆盖 Location 解析、OIDC 与 STS 应答、屏蔽、OOS 参数写入、按 `request_id` 轮询结果与超时、不调用任何 ECS 接口（ci 运行） |

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
   - 按工作流运行判定（轻量）：`actions/runs?head_sha=<sha>&event=push&branch=main` 中 `path` 为 `.github/workflows/ci.yml` 的最新一次运行 `completed/success`，且其三项必需作业（backend verify、frontend verify、secret scan）都是 `success`；其他分支或其他工作流的运行不算。不重跑测试。作业名取自 `ci.yml`，改名而脚本未同步时直接失败。
   - **重步骤另证**（main 上 push 只跑轻量，重步骤只在 PR 带 `queued` 时跑，#406）：从该提交 M 起沿第一父链往回走（至多 30 步），第一个「被证明」的提交 X 即止。X 被证明须同时满足：① `commits/<X>/pulls` 找到以 X 为合并提交、base 为 main 的已合并 PR，取其头提交 H；② `git diff --no-renames --name-only -z H X` 的路径经 `scripts/ci/relevant_change.py` 判定全是纯文档（改名不折叠，含非常规行分隔符的路径一律按代码）；③ H 上最新一次 `event=pull_request`、`path` 为 `ci.yml`、同仓库（非 fork）、头分支等于 PR 头分支（运行若列出 PR，则须含该 PR 且 base=main）、作业名为完整名（light 运行的名字带 ` (light)` 后缀，不算）的运行 `success`，且 backend 作业的 `surefire and failsafe, side by side` 步骤与 frontend 作业的 `Run pnpm verify` 步骤都是 `success`（`skipped` 不算；纯文档 PR 的运行这些步骤是跳过的，所以证明不了代码）；④ H 不得同时是指向别的 base 的其他 PR 的头。若 X 相对其第一父提交只有纯文档差异（PR 没带入代码），则代码等同第一父提交，继续向前走。无合并 PR 的提交，以及任一拒绝点之前，都可以由该提交本身当晚（21:03 UTC）`schedule` 的 ci 运行兜底（含同样的重步骤；ci.yml 没有 `workflow_dispatch` 触发器）。皆无则拒绝（含直接推到 main 的提交）。nightly 只作监测，不作逐提交的门。**残余风险**（待 one-coordinator / 用户确认接受）：有写权限者可建 base 为别的分支、ci.yml 空转、头相同的 PR 来伪造完整作业名的成功运行；④ 能发现留有痕迹的此类 PR，但不能防止在门禁之前删除它；M 仍须在受保护的 main 上，且 prod 部署另有审批。
4. 同名 Release 不存在；同名标签若已存在必须指向该提交。
5. 检出已证明的提交到 `source/`；**构建**（`tools/` 的 `build.sh --source source --commit <sha>`）：`source/` 必须干净且恰在该提交；全新的 `HOME`、Maven 仓库与 pnpm store（`--isolated-caches`）；`pnpm install --ignore-scripts`（依赖的安装脚本一律不跑，已验证构建不受影响）；Maven Wrapper 按 `distributionSha256Sum` 校验下载的 Maven；校验 Java 25、Node 24、锁定的 pnpm； 前端构建前先从该源码导出 OpenAPI（`./mvnw test-compile surefire:test@export-openapi`，Testcontainers 起 PostgreSQL）再 `pnpm run generate:api`：发布包构建机从此需要 Docker 与 PostgreSQL 测试镜像（`backend/architecture/test-images.txt`），无 Docker 则构建失败；`frontend/packages/api` 下的 OpenAPI 与生成代码不再入库（GOV-1023-QUA-02 第二段，部署相关变更，按 TEAM-91 报用户知悉）。
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

只能在 main 上手动运行，输入环境（dev / prod）、版本；prod 另须在 `confirm` 再输一次版本。**部署一律从 main 触发 `deploy.yml`，以 `version` 输入选择发布包**；`v*` 标签只用于发布（`release.yml` 打标签、`gh release download` 寻址），不用于触发部署。从标签触发的运行：`gate` 与 `deploy` 两个作业的 `if: github.ref == 'refs/heads/main'` 不执行；即使改了标签所指提交里的 deploy.yml，OIDC 令牌的 `job_workflow_ref` 为 `…@refs/tags/…`，与 §8.2 信任条件不符，STS 拒绝（失败关闭）。

1. `gate` 作业（`forcitis-one` runner，不进 environment，失败不必等审批）：版本格式；prod 的确认；`deploy_gate.py`：
   - 门的输入只有一个机读文件 `phase1a/infra/deploy_gate.json`（K02）：缺失或格式不对 → dev、prod 一律失败。其中 `blockers` 有任一条 → 失败；修复批合并后由调度会话删掉该条。
   - 迁移基线开放标记 `backend/architecture/migration-baseline-open`：prod 存在即失败；dev 放行，但在部署输出中告警（Actions 中为 `::warning::`）——开放期内基线文件可能在上次 dev 部署后改过，改过就先按 dev 库重建剧本（[`runbooks/dev-db-rebuild.md`](dev-db-rebuild.md)，one-ops）把 dev 库与服务器上的发布状态一起重置再部署：安装器比对两版的 `migrations.txt`，迁移文件内容变了即拒绝安装（「a migration file changed between releases」），只删库不够；prod 另由 `deploy_gate.py approval` 核对发布包所在提交里也没有该标记（用户 2026-10-15，intent `2026-10-15-124901-one-coordinator-部署前开发编排三项都采纳-加一个.md`）。
   - prod：不接受预发布版本；`deploy_gate.json` 的 `exit_conditions`（Phase 1a 出口条件 D 节，**在 dev 上验收**）每一项恰为 ✅（多一个字也不算），且不少于 10 项；`prod_prerequisites` 的 C-9（生产库）、C-15（基线关闭并演练）为 ✅。`PREDEPLOY_CHECKLIST.md` 里这些状态格是 `deploy_gate.py --render` 从该文件写入的视图，手改表格不改变门的判断，且 CI 的 `deploy_gate.py --self-test` 会因不一致失败。
   - prod：版本已列在 `runbooks/prod-approvals.txt`（批准行经 PR 合入 main）。Team 计划的私有仓库没有 environment 的 required reviewers（§11），由这一行代替人工批准。
2. `deploy` 作业（`environment: dev / prod`，两者的部署分支策略只允许 main；OIDC 令牌的 `sub` 含该 environment，阿里云角色的信任策略据此只认这一环境）跑在**专用部署 runner**（标签 `forcitis-one-deploy`，macOS 用户 `forcitis-deploy`，只接这一个作业，见 `CI_RUNNERS.md`）；第一步把 `HOME` 与 git 全局配置换成本作业 `$RUNNER_TEMP` 下的新目录；所有 `python3` 都以 `-I` 运行；权限 `contents: read`、`actions: read`、`id-token: write`（不再有 `deployments: write`），checkout 不保存凭据：
   - 工具链（`toolchain.sh deploy`：先核验 PATH 与系统工具；Java 25、gh、aliyun CLI 按 SHA256 下载）；
   - 再跑一次 `deploy_gate.py`（gate 之后 main 可能新增阻塞）；
   - Release 非草稿；标签经 API 解析出的提交满足 `ci_passed.py`；
   - 下载附件 → `statement.sh verify`：用**仓库中**的 `scripts/release/allowed_signers`（main 上的公钥，不取自 Release）验签，声明的版本、提交（等于标签所指提交）、发布包名、构建工作流（`FORCITIS/forcitis-one-psa/.github/workflows/release.yml@refs/heads/main`）、发布包与 `manifest.json`、`SHA256SUMS` 的 SHA256 全部一致 → `verify_bundle.sh` → `manifest.json` 的 `gitSha` 必须等于标签所指提交 → prod：`deploy_gate.py approval` 核对批准行的提交与发布包 SHA256 恰为这一个构建；
   - `ship.sh <环境> <版本>`（§8）：
     1. **签名下载地址**：以 `GITHUB_TOKEN`（`contents: read`）读 `v<版本>` 的 Release，四个附件 `forcitis-one-<版本>.tar.gz`、`.tar.gz.sha256`、`.release`、`.release.sig` 各恰好一个且已上传；逐个以 `Accept: application/octet-stream`、`--max-redirs 0` 请求附件 API，必须得到 302，读 `Location`：须为 https、主机恰为 `objects.githubusercontent.com` 或 `release-assets.githubusercontent.com`、无用户信息与端口、不超过 4096 字符且不含 `" \ { } [ ]`，否则失败；**从不跟随该地址**，也不打印它（日志只写主机、长度与地址中的到期时间 `se`）。令牌经管道（`curl -H @-`）交给 curl，不进命令行、不落盘。
     2. **安装请求**：UTF-8 JSON 对象，恰好六个键——`version`（字符串，`MAJOR.MINOR.PATCH[-rc.N]`）、`environment`（`dev` / `prod`）、`run_id`（本次运行号，正整数）、`run_attempt`（本次尝试号，正整数）、`request_id`（字符串 `"<run_id>:<run_attempt>"`）、`asset_url_sha256`（对象，键恰为 `bundle`、`bundle-sha256`、`statement`、`statement-sig`，值为写进对应参数的那个值按 UTF-8 字节算的 SHA-256 小写十六进制），紧凑写法、不 base64，约 470 字符（`ship.sh` 超过 4096 即失败）。四个签名地址各放一个参数（见第 4 步）：实测一个 GitHub 附件签名地址约 930 字符（2026-10-15，公开仓库 `cli/cli` 的附件），四个合计约 3.7 KB，加上 JSON 已贴近 OOS 普通参数值 4096 字符的上限，放进一个参数没有余量。
     3. **阿里云临时凭据**：向 GitHub 取本作业的 OIDC 令牌（audience `sts.aliyuncs.com`），以 STS `AssumeRoleWithOIDC`（`https://sts.aliyuncs.com`，匿名接口）换取 3600 秒的临时凭据，角色与 OIDC 提供商取自 environment 变量 `ALIBABA_CLOUD_ROLE_ARN`、`ALIBABA_CLOUD_OIDC_PROVIDER_ARN`。取令牌、换凭据在一条管道中完成（OIDC 令牌经 `--data-urlencode OIDCToken@-` 从标准输入读入），令牌与凭据只在管道与 `ship.sh` 进程的变量 / 环境中，不落盘、不进命令行，进入日志前已以 `::add-mask::` 屏蔽；STS 拒绝时只显示错误码与说明。`ship.sh` 以 `umask 077` 运行。不执行 `aliyun configure`（会把凭据写进用户目录）。
     4. **写入 OOS 参数**：以 `aliyun oos UpdateParameter` 依次写 `forcitis-one-deploy-request-<环境>-bundle`、`-bundle-sha256`、`-statement`、`-statement-sig`（各一个签名地址），最后写 `forcitis-one-deploy-request-<环境>`（请求 JSON）。五个参数不是原子写入，请求中的 `asset_url_sha256` 让安装器认出别的运行写下的地址（安装器第 6 步、§10）。日志只打印请求 JSON（含哈希，不含地址）。参数由运维预建（R-7），部署角色只能更新这五个参数。
     5. **等待结果**：轮询结果参数 `forcitis-one-deploy-result-<环境>`（每 15 秒），**只按 `request_id` 核对**：是本次的 `succeeded` 即成功，`failed` 即失败并给出原因代码，`started` 继续等，`retrying`（非终态，安装器下一分钟会再试）报一次原因后继续等；是别的 `request_id`（或占位值、无法解析）按「还没轮到」继续等。等待上限 4320 秒（安装服务超时 3000 + 拉取周期 60 + 一次正在进行的运行的余量；deploy.yml 的作业上限 80 分钟大于它加上前面各步），超时即失败，输出 `request_id`、「检查 ECS 上 `journalctl -u forcitis-one-release-pull.service`」与撤回办法（把请求参数改回 `{}`）；读结果参数偶发失败时重试，连续 6 次失败同样带这些提示失败。**结果参数只用于回报，`ship.sh` 与安装器都不据此做任何安全判断**；安装器的详细输出在 ECS 的日志里，不再回传到 Actions。所有 `aliyun` 调用都带 `--region` 与 `--RegionId`，并在 `ALIBABA_CLOUD_IGNORE_PROFILE=TRUE` 下运行（不读任何配置文件中的身份）。部署作业**没有任何让 ECS 执行命令的接口**。
   - deploy 作业不持有发布签名私钥，不持有任何长期密钥，也不向 ECS 传送发布包；不再建 GitHub deployment、不再写部署状态（GitHub 对绑定 environment 的作业自建的那条记录照旧，与安装无关）。

### ECS 上的安装器（`remote_install.sh`）

**拉取模式**：ECS 上 root 的 `forcitis-one-release-pull.timer` 每 1 分钟（上一次运行结束后 1 分钟，`OnUnitInactiveSec`，不重叠）启动 `forcitis-one-release-pull.service`（`Type=oneshot`，`TimeoutStartSec=3000`，`ExecStopPost` 兜底，沙箱见 R-7），执行 `/usr/local/sbin/forcitis-one-release pull`（R-7）。子命令原名 `install-pending`（ADR-0030 第 6 项「安装器（漂移发现）」仍按旧名书写，正文不改），拉取模式下更名为 `pull`，F-12 卡已注明。外部没有任何办法让 ECS 执行命令：部署角色只能写该环境的五个请求参数、读结果参数（§8.2）；请求只是数据，安装器以实例 RAM 角色读回后严格校验。安装器不访问 GitHub API，ECS 上没有任何 GitHub 令牌。`pull` 依次：

1. **单实例**：取部署锁（`/run/forcitis-one-release/lock`，`flock -n`；该目录是单元的 `RuntimeDirectory`，运行之间保留，单元外的 `rollback` 不存在时自建）；另一次 `pull` 或运维的 `rollback` 正在进行时**立即静默退出**——安装进行中不读新请求。持锁后先删除 `releases/` 下残留的 `.incoming.*`（被杀的运行留下的下载目录）。
   **时间预算**：安装器 2700 秒后不再开始任何步骤（`PULL_BUDGET_SECONDS`）。各步上限：三个小附件各 60 秒；发布包取「剩余时间 − 迁移 900 − 切换预留 570」与 1200 秒的较小值；迁移 900 秒（兜底迁移单元同样 `RuntimeMaxSec=900`）；切换预留 570 秒 = 两次重启各 60 秒加各自 180 秒的健康检查（新版本、回切后的版本）再加 90 秒的 OOS 调用。剩余时间不够「迁移 + 570」就不迁移，不够 570 就不切换。单元 `TimeoutStartSec=3000` 大于这些之和。
   **被停止时**：TERM / INT 走失败路径——先停掉残留的迁移单元（`systemctl stop 'forcitis-one-migrate-*'`），若本次已切换而健康检查未完成，先回切到原版本并检查（首次安装没有原版本时不回切，告警写明「首次安装切换未确认，服务仍在运行」，标记保留；同版本重装未确认时说明「当前版本仍在运行」并删除标记）；再记 `failed`（原因 `terminated`）、写结果、告警。处理不了信号的情形（SIGKILL）由 `ExecStopPost=forcitis-one-release finish-interrupted` 兜底：停残留迁移单元；**只有**切换标记 `/var/lib/forcitis-one-release/activation` 的 request_id 不是 `-`、且该请求在 `processed-requests` 中的最后状态仍为 `started` 时才回切；其他标记（例如运维 `rollback` 留下的 request_id `-`，或所属请求已记终态的）不动、只告警「发现未确认的切换标记」（按标记内容只告警一次），留给人工处置；首次安装（原版本 `-`）不回切、标记保留供 status 显示；同版本重装（原版本等于目标）不回切、说明当前版本仍在运行并删除标记；最后把仍为 `started` 的请求记 `failed`（`terminated`）、写结果、告警。切换标记在记下 `succeeded` 并写完结果之后才删除；标记写不进时不切换（原因 `activation`）。
2. **本机配置**：`/etc/forcitis-one/release-source`（root 0644）给出 `ENVIRONMENT`（dev / prod）与可选的 `WECOM_VERIFY_FILE`；环境只取自这里，决定读哪组参数。`one.env` 的 `SPRING_PROFILES_ACTIVE` 须等于 `ENVIRONMENT`；`current` 若是手工留下的实目录则拒绝（C-4 E-10）；**断言 `/data/forcitis-one` 与 `releases` 为 root 所有、组和其他人不可写**（C-4 E-5）；`/var/lib/forcitis-one-release/`（root 0700）与其中的记录文件（root 0600）。`pull` 不接受任何参数。
3. **读请求参数**（`python3 -I`，只用标准库，不装 SDK；不走代理、不跟随重定向，每次 15 秒超时）：元数据服务按加固模式访问——先 `PUT http://100.100.100.200/latest/api/token`（`X-aliyun-ecs-metadata-token-ttl-seconds: 60`），之后每次 `GET` 带 `X-aliyun-ecs-metadata-token`；读地域、实例角色名（必须恰好一个）与该角色的 STS 临时凭据（`Code` 须为 `Success`，应答须为 JSON 对象）；再以 OOS RPC 签名（HMAC-SHA1，`SignatureVersion=1.0`，带 `SecurityToken`；签名实现以阿里云 SDK 文档「RPC 机制」中的签名示例做已知答案测试）向 `https://oos.<地域>.aliyuncs.com` 调 `GetParameter`。元数据令牌、角色凭据与签名地址只在该进程与其输出管道中，不打印、不落盘、不进命令行。
4. **没有新请求就静默结束**：请求参数是占位值 `{}`，或其 `request_id` 已在 `processed-requests` 中（无论 `started`、`succeeded` 还是 `failed`）——不告警、不写日志、不写结果。失败过的请求不会自动重试：重新运行 deploy（新的尝试号）。
5. **校验安装请求**（任一项不符即失败，不跳过）：每个参数值 1..4096 个可打印 ASCII 字符；请求为 JSON（重复键、`NaN` 等非 JSON 常量拒绝）、对象恰好上述六个键（多键、缺键都拒绝）；`version` 合格式；`environment` 为 dev / prod 且**等于本机 `ENVIRONMENT`**；`run_id`、`run_attempt` 为正整数（字符串、小数、布尔都拒绝）；`request_id` 恰为字符串 `"<run_id>:<run_attempt>"`；`asset_url_sha256` 的键集合恰为四个后缀、每个值为 64 位小写十六进制。
6. **核对地址哈希**：读回四个地址参数，参数值的 SHA-256 必须等于请求中对应的哈希；任一不符即拒绝（原因 `link-mismatch`，说明含 `request_id` 与不符的参数名，不含地址）。这一核对在防重放记录与任何下载之前，**不消耗防重放记录**：混搭是两次运行写参数的竞态，不是重放，同一请求在参数一致后（Re-run 或运维把参数改回一致）下一轮即被受理。核对通过后，每个签名地址不含空白与 `" \ { } [ ]`，scheme 为 `https`，**整个主机部分恰为** `objects.githubusercontent.com` 或 `release-assets.githubusercontent.com`（因而用户信息、任何显式端口——包括 `:443`——、末尾的点、大小写变体、后缀伪装如 `…githubusercontent.com.evil.com` 都拒绝），有路径、无片段。
7. **每个请求只装一次**：以 `request_id` 为键记在 `/var/lib/forcitis-one-release/processed-requests`（只追加）；**先记下 `started` 再开始安装——记不下就不安装**，安装中途崩溃也不会被同一请求重放；结束时追加 `succeeded`，或 `failed` 与原因代码（这两条记不下只记日志、不改变结果）。同一运行的「Re-run」是新的尝试号，可以再装。
8. **结果参数**：开始时、结束时把 `{"request_id", "status": "started" | "succeeded" | "failed" | "retrying", "reason": <原因代码或 null>, "time"}` 写入 `forcitis-one-deploy-result-<环境>`（不含地址与凭据）。**终态与可重试**：记下 `started` 之前、可能自行恢复的拒绝不消耗防重放记录，下一分钟会再试，写 `retrying`——原因 `read-parameters`（读地址参数失败）、`link-mismatch`、`replay-record`；其余都是终态，写 `failed`——`invalid-link`（地址与请求的哈希一致却不合格式，不会自行变好，记入防重放记录、不再受理），以及记下 `started` 之后的 `version-rejected`、`configuration`、`download`、`verification`、`placement`、`budget`、`migration`、`activation`、`terminated`。读不出 `request_id` 的拒绝（`invalid-request`、读请求参数失败、配置）不写结果。写入失败只记日志并告警（每小时至多一次），不改变安装结果。
9. **失败与告警**：读参数失败、校验失败、哈希不一致、安装失败，都以 `/usr/local/sbin/one_alert_send P2 'one.release-pull <环境>: request <request_id> <原因代码>: <说明>'` 告警，说明由安装器拼出，不含地址与凭据；告警发送失败只记日志。**限频**：同一 `request_id` 的同一原因只告警一次（之后的轮次静默结束，也不再写日志；请求进行中时限频只省掉告警，照样记 `failed`、写结果）；与请求无关的原因（配置、元数据、读参数）每小时至多一次；无法解析出 `request_id` 的请求按参数值的摘要计；**全局上限**每小时 6 条，超出的只计数，随下一条发出的告警附「另有 N 条被抑制」。限频状态在 `/var/lib/forcitis-one-release/alerts-sent`（root 0600，只追加，属主与权限每轮核对）；它写不进时视为不发并记日志；状态目录本身建不出来时，以 `/run/forcitis-one-release/state-alert` 记时，每小时至多告警一次后退出。原因代码见第 8 步，另有 `invalid-request`、`result-write`。
10. prod 拒绝预发布版本；低于当前运行版本的拒绝（见「回滚」；当前版本名不合格式时无法比较，直接失败）；**与当前运行版本相同且服务健康**（第 15 步的检查看一次）时直接记 `succeeded`，不下载、不迁移、不重启；相同但不健康时照常重装，若仍不健康，有 `previous` 就回到 `previous`，没有就只报错，**不停服务**；`release-source` 给出 `WECOM_VERIFY_FILE` 时要求该文件在 `static-pinned` 中存在。
11. **下载与验证**：逐个以 `curl -q -g --proto '=https' --proto-redir '=https' -L --max-redirs 0` 下载签名地址（地址经 `-K -` 以标准输入的配置交给 curl，不进命令行；`-q` 不读任何 curlrc，`-g` 不做通配展开；遇到任何重定向即失败；不带 `Authorization` 或其他凭据；≤ 1 GiB；20 分钟超时），放在 `releases/.incoming.*`；发布包 SHA256 与 `.sha256` 附件一致；**用 ECS 上 root 所有的 `/etc/forcitis-one/release-signers` 验发布声明的签名**（公钥不取自发布包，GitHub 侧被攻破也换不掉），声明格式、**声明中的版本等于请求的版本**、发布包名、构建工作流、发布包 SHA256 都核对，提交取自已验签的声明（须为完整 sha）；归档条目只能在 `forcitis-one-<版本>/` 下，无链接、无 `..`；声明中的 `manifest.json`、`SHA256SUMS` 的 SHA256 一致；解包后 `sha256sum --strict -c SHA256SUMS`，不允许有清单外的文件；**`manifest.json` 的 `version` 等于请求的版本**，`gitSha` 等于声明中的提交。
12. **服务器文件一致**（新装的版本）：已安装的安装器、systemd 单元（应用与拉取的 service / timer）、nginx 配置、logrotate 与发布包中 `deploy/` 的同名文件逐字节相同，`one.env` 含模板中的每个键；不一致即失败并列出（§5）。
13. 放到 `/data/forcitis-one/releases/<版本>/`（root:forcitis，目录 750、文件 640），`web` 软链指向 `web-<环境>`，给 nginx 用户只读 ACL。已装过且内容一致的版本直接复用，不改 `web` 软链；装给另一环境的同版本拒绝。
14. 迁移（§4），**每次部署都运行**：有新迁移时应用它们，没有时由 Flyway 校验已应用的迁移与本版本一致。
15. 单元若尚未 enabled（首次安装），先 `systemctl enable forcitis-one`，失败即安装失败；再原子切换 `current` 软链，`systemctl restart forcitis-one`（此后每次安装只重启），经本机回环取 `http://127.0.0.1:<MANAGEMENT_SERVER_PORT>/actuator/health`，解析 JSON 顶层 `status` 为 `UP`；经本机回环取 `http://127.0.0.1:<MANAGEMENT_SERVER_PORT>/actuator/prometheus`，必须含以 `forcitis_` 开头的指标行（应用自身的指标在导出；只做匹配，不打印内容）；并且 `/data/forcitis-one/static-pinned/` 下每个企业微信校验文件经本机 nginx（`https://one.forcitis.com/<文件名>`）返回原文（三项在同一个最长 180 秒的窗口内反复检查，这一窗口计入时间预算）。`release-source` 给出 `WECOM_VERIFY_FILE` 时，切换前还要求该文件存在（第 10 步）。
16. 不健康（含缺少 `forcitis_` 指标）：自动切回原来的 `current` 并重启，原版本健康则以「已回切」报错退出，原版本也不健康则报「需人工处理」；首次部署没有上一版本时停止服务并报错。「服务器文件一致」检查同时要求 `one.env` 的 `MANAGEMENT_ENDPOINTS_WEB_EXPOSURE_INCLUDE` 含 `prometheus`（否则指标检查必然失败）。

**半切换状态的人工处置**（自动回切做不到时：首次部署没有上一版本、回切后原版本也不健康，或 `finish-interrupted` 本身没能运行）：
- **判断**：经云助手执行 `forcitis-one-release status`，看 `current`、`previous` 与 `recent history`；输出中有 `UNCONFIRMED SWITCH` 一行（即 `/var/lib/forcitis-one-release/activation` 存在，内容为「原版本 目标版本 request_id」，`-` 表示首次安装没有原版本或运维的 rollback）表示切换未确认；`systemctl list-units 'forcitis-one-migrate-*'` 看有无残留的迁移单元；`journalctl -u forcitis-one-release-pull.service` 看最后的 `FAILED` 行；`curl -s 127.0.0.1:<MANAGEMENT_SERVER_PORT>/actuator/health`。
- **执行**：先 `systemctl stop 'forcitis-one-migrate-*'` 停掉残留的迁移单元（迁移结果以 `one_migration.flyway_schema_history` 为准）；有上一版本时 `forcitis-one-release rollback <原版本>`（或不带参数回到 `previous`）——**经云助手执行 rollback 时 `--Timeout` 至少 300 秒**（默认 60 秒短于 180 秒的健康检查，会把 rollback 中途杀掉；rollback 被中断时自身的 TERM 处理只记日志、不回切，标记留给人工）；首次部署没有上一版本时保持服务停止（`systemctl stop forcitis-one`），修复后发新版本。处置后删除 `activation` 文件；`processed-requests` 中仍为 `started` 的请求追加一行 `<request_id>\t<时间>\tfailed\tterminated: manual`，结果参数改为该请求的 `failed`。
- **核对**：`forcitis-one-release status` 的 `current` 为预期版本，health 为 `UP`，`/actuator/prometheus` 含 `forcitis_` 指标行（`curl -s … | grep -c '^forcitis_'` 大于 0），`releases/HISTORY` 末行为 `restored` 或 `active`；运维群收到的告警与此一致。
17. 健康：上一版本记为 `previous`；保留最近 5 个版本（`current`、`previous` 之外），更旧的删除；每次结果追加到 `releases/HISTORY`。

### 回滚

- **降级只走运维的显式回滚**：`pull` 拒绝低于当前版本的请求（按 `MAJOR.MINOR.PATCH`，`-rc.N` 低于对应正式版），因此不能用 `deploy` 部署旧版本号；能调用固定命令的人也就无法借安装请求把系统退回旧版本。
- 回滚：运维经云助手执行 `forcitis-one-release rollback`（回到 `previous`）或 `forcitis-one-release rollback <已安装的版本>`，**`--Timeout` 至少 300 秒**（健康检查最长 180 秒；被中断的 rollback 不会被自动撤销，见 §3 半切换状态）；旧版本的文件仍在 `releases/` 中，已在首次安装时校验过；回滚不跑迁移（旧版本不认识的新迁移被 Flyway 默认的 `*:future` 忽略）。修复问题后发更高的版本号再部署。
- `forcitis-one-release status` 查看当前、上一版本、历史与最近的安装请求处理结果；回滚只用本机已装的版本，不访问网络，GitHub 不可用时照样可做。
- 回滚只换应用，不回滚数据库（§4）。
- **跨基线变化不回滚**（ADR-0038（已采纳，部分取代 ADR-0033 迁移方案第 5 条等） 第 2 项第 4 条）：基线开放期内 dev 按 dev 库重建剧本（PREDEPLOY_CHECKLIST C-16）重置后，重置前的版本一律不能 `rollback`（剧本已移除其版本目录）；要退回旧代码，在 main 上回退提交后发新版本，再按需重建。同一基线之内的回滚照旧。§4「只扩不缩」的前提在开放期内不成立，由这一条覆盖。

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

任一为空，`build.sh` 失败（不静默构建出不能登录的包）。

现值（2026-10-15 写入仓库级变量并回读；release.yml 的 build 作业不绑定 environment，只读仓库级变量）：

| 变量 | 值 | 出处 |
|---|---|---|
| `DEV_VITE_WECOM_CORP_ID`、`DEV_TARO_APP_WECOM_CORP_ID` | `wwadff8d21724e2e3e` | B-2 新自建应用的企业 ID（与 ECS `one.env` 的 `WECOM_CORP_ID` 相同） |
| `DEV_VITE_WECOM_AGENT_ID` | `1000142` | B-2 新自建应用「FORCITIS one」（与 `one.env` 的 `WECOM_AGENT_ID` 相同） |
| `DEV_VITE_SSO_PORTAL_URL` | `https://bpm.forcitis.com/seeyon/` | **临时**：A8 根地址（登录后进门户）。**待替换**：B-3 回传 A8 门户中新入口所在页面的地址后替换并重新发布 |
| `PROD_*` 四个 | 与 `DEV_*` 相同 | **待替换**：`build.sh` 每次构建 dev 与 prod 两份，`PROD_*` 必填；生产前确认 prod 是否用同一企业、同一自建应用与门户地址，不同则替换后重新发布（prod 部署另受 D 节出口条件与批准行约束） |不放在 GitHub environment 里的原因：一个作业只能绑定一个 environment，且绑定 `prod` 会触发 prod 的审批与部署记录，发布时同时构建两个环境做不到。长期做法是前端改为一次构建、运行时读取配置（例如 nginx 提供的 `/config.json`），届时这些变量移到 `one.env`，发布包只含一份静态资源（给 1a 的建议）。

## 7. 一次性步骤（用户或运维执行，执行前确认）

本文不建 OIDC 身份提供商、RAM 角色、environment、变量、secret 或云助手命令，下列各项由用户或运维执行。缺任何一项，相应作业失败关闭，不会降级执行。部署链路不再有任何长期密钥：阿里云凭据由 GitHub OIDC 临时换取，ECS 上没有 GitHub 令牌；用户决定「需要我做的，都让ops 会话自己做」，凡能由运维完成的步骤都归运维（R-4、R-6、R-7、R-11），one-coordinator 已批准该设计。

### R-0 专用部署 runner 重建（必需；只有 deploy 作业依赖它，release 在 GitHub 托管机上）

**已完成（2026-10-15，one-ops 引导、用户输入口令）**：脚本 `tools/ops/runner_rebuild.sh`（参数：发行包、GitHub 公布的 SHA256、注册令牌文件）。runner 2.338.0（osx-arm64，SHA256 与发布页一致），`mac-one-deploy` 在线，标签 `self-hosted, macOS, ARM64, forcitis-one-deploy`，进程以 `forcitis-deploy` 运行。

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
| R-1 | 建 GitHub environments `release`、`dev`、`prod`；三者「Deployment branches and tags」都只允许 `main`（Team 私有仓库可用）。不设 required reviewers / wait timer（Team 私有仓库不可用，§11）；prod 的人工批准改为 `prod-approvals.txt` 批准行 **✅ 2026-10-15**：三个 environment 已建，部署分支策略均只有 `main`。 **补充（2026-10-15）**：dev、prod 另加标签部署策略 `v*`（deployment 的 ref 是标签 `v<版本>`，只允许 `main` 时可能被拒；用户「允许，首次部署前加上 (Recommended)」）；回读：release=main，dev / prod=main + v*（tag）。**撤销（2026-10-15）**：OIDC 方案起 deploy 作业不再建以标签为 `ref` 的部署记录，`v*` 策略已无用处（从标签触发的部署被 `gate` 的 `if` 与 §8.2 信任条件两道拒绝，见 §3），只多允许标签触发的运行进入 dev / prod environment；用户「撤掉，只留 main (Recommended)」，已删 dev、prod 的 `v*`（tag）策略，回读：release、dev、prod 均只有 `main`。 | 仓库 Settings → Environments |
| R-2 | 仓库级 Actions variables：§6 的 8 个构建期变量（B-2、B-3 完成后填） | Settings → Secrets and variables → Actions → Variables |
| R-3 | ~~OSS bucket~~ **不再需要**：发布包改由 ECS 自取 GitHub Release（§8，用户「先测 GitHub 直拉，可行就用 (Recommended)」），不建发布用 bucket；B-13 归档 bucket 与此无关 | — |
| R-4 | **OIDC 身份提供商 + RAM 角色（运维执行，经用户确认；不建 RAM 用户、不生成 AccessKey）**：① GitHub 仓库 OIDC `sub` 定制：`PUT /repos/FORCITIS/forcitis-one-psa/actions/oidc/customization/sub`，`{"use_default": false, "include_claim_keys": ["repo", "context", "job_workflow_ref"], "use_immutable_subject": true}`，回读 `GET` 同路径（2026-10-15 已执行，回读 `sub_claim_prefix` 为 `repo:FORCITIS@282818954/forcitis-one-psa@1418536044`）；② 阿里云 IMS `CreateOIDCProvider`：`IssuerUrl=https://token.actions.githubusercontent.com`、`ClientIds=sts.aliyuncs.com`、`Fingerprints`（运维创建时从该地址的证书链现场取得并核对；登记链上的 CA 证书，不登记叶子证书，见 §10）、名称 `github-actions`（2026-10-15 已执行，`acs:ram::1980847222980247:oidc-provider/github-actions`）；③ RAM `CreateRole`（每环境一个，如 `forcitis-one-deploy-dev`，`MaxSessionDuration=3600`，信任策略见 §8.2；dev 2026-10-15 已执行，`acs:ram::1980847222980247:role/forcitis-one-deploy-dev`，回读信任策略逐字一致）；④ RAM `CreatePolicy`（§8.2 的部署策略 `forcitis-one-deploy-dev`：五个请求参数只有 `oos:UpdateParameter`、结果参数只有 `oos:GetParameter`，**没有任何 ECS 接口**）+ `AttachPolicyToRole`（dev 2026-10-15 已执行，回读 v1 逐字一致，角色上只此一份）；⑤ RAM `CreatePolicy`（§8.2 的拉取策略 `forcitis-one-deploy-pull`：五个请求参数只有 `oos:GetParameter`、结果参数只有 `oos:UpdateParameter`）+ `AttachPolicyToRole`（附到 ECS 现有实例角色 `forcitis-one-ecs-archive`；**不新建、不改名、不重绑实例角色**：一台 ECS 只能绑一个实例角色。该实例角色承载归档写入与部署请求拉取两份最小策略；dev 2026-10-15 已执行，回读 v1 逐字一致，实例角色上恰为 `forcitis-one-archive-writer` 与本策略两份，实例绑定不变）；⑥ 回读：`GetRole`、`ListPoliciesForRole`（两个角色）、`GetOIDCProvider`、`GetPolicyVersion` | 阿里云 RAM / IMS OpenAPI；GitHub REST |
| R-5 | 每个 environment 的 secrets：**dev / prod 不需要任何阿里云 secret**（凭据由 OIDC 临时换取）；只剩 environment `release` 的 `RELEASE_SIGNING_KEY`（R-12）。SSH 触发方式已删除，`DEPLOY_SSH_KEY`、`DEPLOY_KNOWN_HOSTS` 不再使用 | Environments |
| R-6 | 每个 environment 的 variables（运维以 `gh api` 写入并回读；都不是机密）：`ALIBABA_CLOUD_ROLE_ARN`（R-4 ③ 的角色 ARN）、`ALIBABA_CLOUD_OIDC_PROVIDER_ARN`（R-4 ②）、`ECS_REGION=cn-hangzhou`（OOS 参数所在地域）。拉取模式下部署作业不再知道实例与命令：原 `ECS_INSTANCE_ID`、`ECS_COMMAND_ID`（以及 `DEPLOY_TRIGGER`、`DEPLOY_HOST`、`DEPLOY_USER`、`DEPLOY_PORT`、`OSS_*`）不再使用，已建的删除；dev 的 ECS 为 `i-bp122xlpar8w2uylmal2`，prod 的 ECS（待建）只出现在 prod 的策略与 R-7 中；企业微信校验文件名在 ECS 本机 `release-source` 的 `WECOM_VERIFY_FILE` **✅ dev 2026-10-15**：三个变量已写入并回读；仓库级与 dev / prod / release 都没有 `ECS_INSTANCE_ID`、`ECS_COMMAND_ID`；prod 待 prod ECS 建好后写。 | Environments → dev / prod |
| R-7 | ECS（运维经云助手，root）：C-4 E-3、E-5、E-9、E-10 的新做法；安装器、签名公钥、`release-source`（`ENVIRONMENT`、可选 `WECOM_VERIFY_FILE`）、拉取定时器与服务（`forcitis-one-release-pull.timer` / `.service`，下方命令块）、OOS 请求参数五个与结果参数一个（下方表格，运维一次性预建）；已按旧做法装过令牌文件与令牌检查的 ECS 先按下方「卸载」清理。ECS 上不需要 aliyun CLI 或任何 GitHub 令牌；读写参数用现有实例角色（R-4 ⑤），ECS 出方向须能访问 `oos.cn-hangzhou.aliyuncs.com:443`（E-14 白名单若按域名限制，须加上）；告警经已装的 `/usr/local/sbin/one_alert_send`。不建云助手命令 **✅ dev 2026-10-15**（云助手 t-hz07039tq9kcpvk）：首次安装的安装器与拉取单元取自 main `7f04d6f5`（发布包尚未构建；ECS 上 `sha256sum -c` 核对后安装，安装器 SHA256 `122af6a5…2c76`），**此后一律以签名发布包 `deploy/` 中的文件为准**（不一致时按下方「安装器更新」替换）；`release-signers` 与 `allowed_signers` 公钥行一致；旧令牌文件与检查单元已删，`release-source` 去掉 `GITHUB_REPOSITORY`；定时器已启用，首轮拉取 Result=success、无告警（读到占位 `{}` 静默结束，元数据加固流程、实例角色与 OOS 读取均通）。 | ECS；OOS OpenAPI |
| R-8 | 标签保护：Rules → Rulesets 新建标签规则 `v*`，限制创建、更新、删除（仅管理员与 release 工作流） **✅ 2026-10-15（偏差）**：仓库级规则不允许把 GitHub Actions 设为例外（须组织级规则），若限制创建，release 工作流打不了新标签；因此规则「release tags v*」只限制**更新与删除**（例外：仓库管理员），不限制创建。残余：有写权限者可新建 `v*` 标签，但产生不了签名的发布声明，部署验签不过。 | Settings → Rules |
| R-9 | 启用 immutable releases（本仓库 API 返回 `enabled:false`：功能存在、未启用；只对启用后的新 Release 生效）；确认 Actions 允许工作流声明 `contents: write`，且允许使用 GitHub 托管 runner 运行 `publish`（Team 计划含托管分钟数） **✅ 2026-10-15**：immutable releases 已启用；Actions 允许全部、默认令牌只读（工作流按需声明 `contents: write`）、托管 runner 可用。 | Settings → General（Releases）/ Actions |
| R-10 | 首次部署前 `/etc/forcitis-one/one.env` 补 `SPRING_FLYWAY_ENABLED=false`；按 §4 在 `/etc/forcitis-one/migrate/` 放迁移凭据文件（首次另加 Flowable 安装项） | ECS |
| R-11 | 连通演练（不部署，R-4、R-6、R-7 之后；不需要任何令牌；运维以自己的身份读写参数，观察 ECS 上 `journalctl -u forcitis-one-release-pull.service` 与运维群）：① **OIDC 换凭据**：信任策略只认 main 上 deploy.yml 的 deploy 作业，`blocks: deploy` 存在时 gate 失败、到不了该作业，因此在首次 dev 部署时以 `ship.sh` 输出的「STS session for … until …」为证；信任条件写错时 STS 拒绝，日志只显示错误码与说明，运维按 §8.2 回读角色核对；② **真实读写参数与上限**：把 `forcitis-one-deploy-request-dev` 设为 `invalid`，下一分钟内安装器应以「install request: is not JSON」失败并发一次 P2 告警——证明元数据加固模式、实例角色凭据、OOS 签名与 `GetParameter` 权限都通；以 `UpdateParameter` 把一个地址参数设为 4096 个字符应被接受、4097 个应被拒，验证 OOS 值上限；查 `ListParameterVersions` 记下版本是否累积（版本上限文档未写，见 §10）；③ **告警限频**：保持 `invalid` 再等两分钟，不应再有告警与日志；④ **混搭**：把请求参数设为合格式的请求（`request_id` 与运行号一致，`asset_url_sha256` 四个值取任意 64 位小写十六进制），应以「link parameters … do not match」拒绝、发一次 P2 告警（含 `request_id` 与参数名、不含地址），`processed-requests` 不变，结果参数为 `retrying` / `link-mismatch`（非终态，不消耗记录；§3 第 8 条）——同时证明实例角色能写结果参数；⑤ **已处理请求静默跳过**：把请求参数设回刚才那个 `request_id` 已在 `processed-requests` 中的请求（例如运维手工追加一行 `<id>\t<时间>\tfailed\tdrill`），之后各轮无日志、无告警；⑥ **单实例**（须在服务至少运行过一次、`/run/forcitis-one-release/` 已建立之后）：在 ECS 上 `flock /run/forcitis-one-release/lock sleep 120` 持锁期间手工 `systemctl start forcitis-one-release-pull.service`，应立即成功结束、无日志；⑦ **合法请求**只能用真实 Release 验证：首次 dev 部署即是（同时记下 `ship.sh` 打印的签名地址长度）；⑧ **沙箱**：`systemd-analyze verify` 与 `systemd-analyze security forcitis-one-release-pull.service` 无错误；②④⑤⑥ 都在沙箱内运行即证明读写参数、告警与锁不受影响；安装路径（`/data/forcitis-one` 写入、`setfacl`、`systemctl restart`、`systemd-run` 迁移）由首次 dev 部署验证，若因沙箱失败（日志出现 `Read-only file system` 等）据实补 `ReadWritePaths` 并记入 R-7；⑨ **ECS 上实跑一次 `pull`**（`systemctl start forcitis-one-release-pull.service`，请求参数为 `{}` 时应静默结束）：确认沙箱（不含 `AF_NETLINK`）下 Python urllib 与 curl 的域名解析、元数据与 OOS 访问都正常；⑩ 演练后把请求参数恢复为 `{}`、结果参数恢复为 `{}`，删去手工追加的记录行 **✅ dev 2026-10-15 15:41–15:53**（one-ops；授权：用户在 one-ops 会话「运维群已知会，开始演练」，intent #152；one-coordinator 批准范围与顺序 ②→③→④→⑤→⑥→⑧→⑨→⑩）：② 设 `invalid` 后下一轮「FAILED: install request: is not JSON」并发 1 次 P2 告警，无处理记录、无切换；地址参数 4096 字符接受、4097 字符被拒（`Field cannot be longer than 4096`），参数版本会累积；③ 保持 `invalid` 约 2.5 分钟无日志、无告警；④ 合格式请求（`request_id` 900000001:1，哈希任取）以「link parameters … do not match」拒绝，日志只有参数名、不含地址，告警 1 次，`processed-requests` 不变，无下载（releases/、tmp/ 空，无 `.incoming`），结果参数 `retrying` / `link-mismatch`（原文预期写作 `failed`，系文档未跟进 §3 第 8 条的实现，已更正）；⑤ 手工追加 drill 记录后各轮 Result=success、无日志、无告警；⑥ 持锁期间手工启动：每轮 CPU 21–24 ms（不持锁时完整一轮约 125 ms），无日志，即在 `flock -n` 处退出；⑧ `systemd-analyze verify` 退出码 0（只有阿里云监控代理单元的警告），`security` 无错误，评分 8.3 EXPOSED（root 服务需 systemctl 与 systemd-run，可后续收紧 UMask 等）；⑨ 请求为 `{}` 时手工跑一轮完整读取后静默结束（沙箱下域名解析、元数据与 OOS 正常）；⑩ 六个参数恢复初值并回读（request v4 `{}`、bundle v3 `unset`、其余地址 v1 `unset`、result v3 `{}`），drill 记录已删（`processed-requests` 0 行，root 0600），无演练进程残留，下一轮自动拉取静默成功。① 与 ⑦ 留待首次 dev 部署。 | 运维 |
| R-12 | **发布签名密钥**：在可信机器上 `ssh-keygen -t ed25519 -N '' -C forcitis-one-release -f release_signing`；私钥存为 environment `release` 的 secret `RELEASE_SIGNING_KEY`，随后删除本地私钥文件；公钥写成一行 `forcitis-one-release namespaces="forcitis-one-release" ssh-ed25519 AAAA… forcitis-one-release`，经 PR 写入 `scripts/release/allowed_signers`，并由运维装到 ECS `/etc/forcitis-one/release-signers`（root 0644）。轮换：两处同时替换；旧版本的声明随之不可验证，回滚到旧版本需用新钥重签或临时并列两把公钥 **✅ 2026-10-15**：在用户本机生成，私钥直接存为 environment `release` 的 secret `RELEASE_SIGNING_KEY` 后删除（未显示、未落盘他处）；公钥写入 `scripts/release/allowed_signers`；ECS 侧 `/etc/forcitis-one/release-signers` 随 R-7 安装。 | 可信机器；GitHub；ECS |
| R-13 | ~~GitHub 只读令牌~~ **不再需要**：ECS 不再访问 GitHub API，所装内容由安装请求指明，而只有 main 上为该环境运行的 deploy 作业能经 OIDC 写请求参数（§8.2）。理由：用户决定「需要我做的，都让ops 会话自己做」，令牌须用户本人生成、每 90 天轮换，与之相悖；改为 OIDC 方案经 one-coordinator 批准。已装的令牌文件与检查按 R-7「卸载」清理 | — |

R-7 命令（`<version>` 用将要部署的发布包；先在可信机器上 `verify_bundle.sh` 校验，再经云助手上传其中的 `deploy/remote_install.sh`）：

```bash
install -o root -g root -m 0755 remote_install.sh /usr/local/sbin/forcitis-one-release
install -o root -g root -m 0644 release-signers /etc/forcitis-one/release-signers      # R-12 的公钥行

cat > /etc/forcitis-one/release-source <<'EOF'
ENVIRONMENT=dev
WECOM_VERIFY_FILE=<static-pinned 中现有的 WW_verify_*.txt；没有则删去本行>
EOF
chown root:root /etc/forcitis-one/release-source && chmod 0644 /etc/forcitis-one/release-source

# 拉取定时器（取自同一发布包的 deploy/；安装器把它们列入「服务器文件一致」检查）
install -o root -g root -m 0644 forcitis-one-release-pull.service forcitis-one-release-pull.timer /etc/systemd/system/
systemctl daemon-reload && systemctl enable --now forcitis-one-release-pull.timer
systemctl list-timers forcitis-one-release-pull.timer --no-pager

forcitis-one-release status
```

**升级过渡期**：旧安装器的 `rollback` 用旧锁 `/run/forcitis-one-release.lock`，新 `pull` 用 `/run/forcitis-one-release/lock`，两者不互斥；因此换装新安装器与拉取单元要安排在没有回滚操作的时段，换装后 `rollback` 也由新安装器执行。预建参数之前定时器就可以启用：读不到请求参数时每小时至多告警一次（`read-parameters`）。服务单元带 `ExecStopPost=forcitis-one-release finish-interrupted`（被杀后的兜底，§3）与沙箱：`ProtectSystem=strict`，可写的只有 `/data/forcitis-one`、`RuntimeDirectory=forcitis-one-release`（`/run/forcitis-one-release`：锁与告警标记，`RuntimeDirectoryPreserve=yes`）、`StateDirectory=forcitis-one-release`（`/var/lib/forcitis-one-release`）与 `LogsDirectory=forcitis-one-release`（迁移日志 `/var/log/forcitis-one-release`）；不加 `AF_NETLINK`；`ProtectHome=yes`、`PrivateTmp=yes`、`NoNewPrivileges=yes`、`RestrictAddressFamilies=AF_UNIX AF_INET AF_INET6`（systemd 与 D-Bus 用 AF_UNIX，OOS、GitHub、本机健康检查与 `one_alert_send` 用 AF_INET；`one_alert_send` 只读 `/etc/forcitis-one` 的配置、不写文件）。迁移由 `systemd-run` 起的独立单元执行，不受此沙箱限制。

`release-source` 中旧的 `GITHUB_REPOSITORY=` 行已不再读取，可删去。

**卸载（已按旧做法装过的 ECS）**：旧做法在 ECS 上装了 GitHub 令牌文件（`/etc/forcitis-one/github-release-token`，空文件，与 `github-release-token.expires`）、令牌到期检查脚本 `/usr/local/sbin/one_release_token_check.sh` 与单元 `/etc/systemd/system/one-release-token-check.service`、`.timer`（已停用）。新安装器不再使用它们，运维经云助手删除：

```bash
systemctl disable --now one-release-token-check.timer 2>/dev/null || true
rm -f /etc/systemd/system/one-release-token-check.service /etc/systemd/system/one-release-token-check.timer \
      /usr/local/sbin/one_release_token_check.sh \
      /etc/forcitis-one/github-release-token /etc/forcitis-one/github-release-token.expires
systemctl daemon-reload
# 旧的 /var/lib/forcitis-one-release/processed-deployments 是历史记录，保留；新记录写在同目录的 processed-requests
```

OOS 参数（运维一次性预建，`CreateParameter`，类型 `String`，地域 cn-hangzhou；请求参数的初值为占位，安装器把 `{}` 当作「没有请求」；部署角色对请求参数只能 `UpdateParameter`、对结果参数只能 `GetParameter`，ECS 实例角色反之）。OOS 普通参数值上限 4096 字符（[UpdateParameter 文档](https://help.aliyun.com/zh/oos/developer-reference/api-oos-2019-06-01-updateparameter)），真实上限在 R-11 ② 验证：

| 参数名（dev；prod 把 `-dev` 换成 `-prod`） | 内容 | 初值 |
|---|---|---|
| `forcitis-one-deploy-request-dev` | 请求 JSON（§3） | `{}` |
| `forcitis-one-deploy-request-dev-bundle` | `forcitis-one-<版本>.tar.gz` 的签名地址 | `unset` |
| `forcitis-one-deploy-request-dev-bundle-sha256` | `.tar.gz.sha256` 的签名地址 | `unset` |
| `forcitis-one-deploy-request-dev-statement` | `.release` 的签名地址 | `unset` |
| `forcitis-one-deploy-request-dev-statement-sig` | `.release.sig` 的签名地址 | `unset` |
| `forcitis-one-deploy-result-dev` | 安装器写的结果 JSON（`request_id`、`status`、`reason`、`time`），只用于回报 | `{}` |

不建云助手命令：拉取模式下部署作业没有任何执行接口。若已按本 PR 早先的草稿建过 `forcitis-one-release-install` 命令，运维删除它，并删除 environment 变量 `ECS_INSTANCE_ID`、`ECS_COMMAND_ID`。

安装器更新：新版本的 `deploy/remote_install.sh`、`deploy/forcitis-one-release-pull.{service,timer}` 与已装的不同，部署会因「服务器文件不一致」失败；运维以同样的 `install` 命令替换（单元文件替换后 `systemctl daemon-reload`）后再部署。

## 8. 发布包到 ECS 的通路

用户决定：「先测 GitHub 直拉，可行就用 (Recommended)」。在 ECS 上实测（北京时间夜间）从 `release-assets.githubusercontent.com` 下载约 **9 MB/s**，可行；因此不经 OSS 中转、不建发布用 bucket（原 R-3 取消）。用户随后决定「需要我做的，都让ops 会话自己做」，并要求部署链路不留长期密钥：部署作业以 GitHub OIDC 换取阿里云临时凭据，把版本、环境、运行号与四个附件的短期签名下载地址写进该环境固定的 **OOS 参数**；ECS 上 root 的定时器每分钟以实例 RAM 角色**拉取**参数、严格校验，不访问 GitHub API，只按请求下载、用本机公钥验签后安装，把结果写回结果参数（one-coordinator 批准）。演进：请求最初设计为云助手命令的参数——复核发现参数是文本替换、可注入；改为 OOS 参数加无参数命令后，复核又发现 `InvokeCommand` 允许调用方附带 `Launcher`、`Frequency`、`Timeout` 等执行参数；因此改为拉取模式，部署角色不再有任何执行接口。调用方传不进任何命令、文件或其他主机的地址。

```
部署 runner（deploy 作业，main 上的 deploy.yml，environment dev / prod）
  (1) GITHUB_TOKEN（contents: read）：附件 API ──302 Location──▶ 四个短期签名地址（不跟随、不打印）
  (2) OIDC 令牌（aud sts.aliyuncs.com）──AssumeRoleWithOIDC──▶ 阿里云 STS：该环境部署角色的 1 小时临时凭据
  (3) oos:UpdateParameter ──▶ OOS：先 4 个签名地址参数，最后 forcitis-one-deploy-request-<env>（请求 JSON，含 request_id 与 4 个地址的 SHA-256）
  (4) oos:GetParameter 轮询 forcitis-one-deploy-result-<env>，只认本次 request_id（只用于回报）
                                                     ▲ (7) 写结果 started / succeeded / failed
杭州 ECS：forcitis-one-release-pull.timer（每分钟）→ forcitis-one-release pull（root，单实例）
  (5) 元数据服务（加固模式）取实例角色凭据 → oos:GetParameter 读请求；占位或已处理：静默结束；核对地址哈希（不符：拒绝 + P2 告警）
  (6) HTTPS 直接下载签名地址（不重定向、无凭据）──▶ objects / release-assets.githubusercontent.com → 验签 → 安装
```

### 8.1 谁持有什么、泄露的后果

| 对象 | 持有 / 有效期 | 被拿到时能做的 |
|---|---|---|
| GitHub OIDC 令牌 | 只在 deploy 作业的一条管道中，向 GitHub 现取、立即换成 STS 凭据，不落盘；短期 | 只能换取**该环境**部署角色的凭据，且须在有效期内；信任策略要求 `sub` 恰为 main 上 deploy.yml 在该 environment 的作业 |
| 部署角色临时凭据 | `ship.sh` 进程的变量与环境，1 小时 | 只能改写该环境的五个请求参数、读结果参数——**没有任何让 ECS 执行命令的接口**。请求只是数据：安装器只接受合格式、地址哈希一致的请求，只从 GitHub 的两个附件主机下载，只装本机公钥验签通过且版本一致的发布包，不降级，prod 不装预发布，同一 `request_id` 只受理一次——**装不上 release 工作流没有签过的东西**。如实列出能做的坏事：**升级到任何已签名的更高版本**（ECS 不知道 deploy_gate、阻塞条目与 prod 批准行，这些只在 deploy 作业中核对）；**触发重启**（不同版本的安装会迁移并重启；同版本且健康时不重启）；**下载拒绝服务**（让 ECS 下载至多 1 GiB 的附件，20 分钟内占用带宽与磁盘，验签失败后删除）；**告警刷屏**（每次写不同的请求，受每小时 6 条的全局上限约束） |
| ECS 实例角色临时凭据 | ECS 元数据服务（加固模式，须先取会话令牌）；安装器只在 Python 进程内使用 | 读五个请求参数、写结果参数（另有该角色原有的归档写入权限，B-13）。写结果参数只能误导 `ship.sh` 的回报，不影响任何安装判断。在 ECS 上取得它须已有该机的 root 或能发起元数据请求的进程 |
| 附件签名地址 | OOS 参数中（直到下一次部署覆盖）；部署 runner 上 `aliyun` 的命令行（§10） | 在 GitHub 的短期有效期内下载附件；附件本身对仓库读者本就可下载，不是机密 |
| 发布签名私钥 | 只在 environment `release` 的 secret，GitHub 托管机上的 `publish` 作业使用 | 能签发布声明——这是整条链路唯一的长期密钥，保护见 R-12、§11.2 |

能让 ECS 装上一个版本的前提：在 main 上运行 deploy.yml 并通过 gate、environment 分支策略与 runner hook（否则拿不到满足信任策略的 OIDC 令牌，也就写不了请求参数）；所装的是 release 工作流签名的 Release；不低于当前版本；prod 不是预发布。

### 8.2 RAM 策略与信任策略（草稿）

每个环境一个部署角色（以 dev 为例；prod 换角色名与策略名中的 `dev`、`environment:prod`、prod 的参数名，prod 的 ECS 待建）。`<account-id>`、`<provider-name>` 由运维在 R-4 中填；保存前以 RAM 接口校验，资源格式以接口提示为准（OOS 参数 ARN 格式 `acs:oos:{regionId}:{accountId}:parameter/{name}`，见 UpdateParameter 文档的授权信息）。

**部署策略** `forcitis-one-deploy-dev`（`CreatePolicy` 后 `AttachPolicyToRole` 到部署角色）：

```json
{
  "Version": "1",
  "Statement": [
    {
      "Effect": "Allow",
      "Action": ["oos:UpdateParameter"],
      "Resource": [
        "acs:oos:cn-hangzhou:<account-id>:parameter/forcitis-one-deploy-request-dev",
        "acs:oos:cn-hangzhou:<account-id>:parameter/forcitis-one-deploy-request-dev-bundle",
        "acs:oos:cn-hangzhou:<account-id>:parameter/forcitis-one-deploy-request-dev-bundle-sha256",
        "acs:oos:cn-hangzhou:<account-id>:parameter/forcitis-one-deploy-request-dev-statement",
        "acs:oos:cn-hangzhou:<account-id>:parameter/forcitis-one-deploy-request-dev-statement-sig"
      ]
    },
    {
      "Effect": "Allow",
      "Action": ["oos:GetParameter"],
      "Resource": ["acs:oos:cn-hangzhou:<account-id>:parameter/forcitis-one-deploy-result-dev"]
    }
  ]
}
```

不授 `oos:CreateParameter`、`oos:DeleteParameter`、对请求参数的读、对结果参数的写、其他参数的任何权限，也不授任何 `ecs:*`（`InvokeCommand`、`RunCommand`、`DescribeInvocation*` 都没有）。

**拉取策略** `forcitis-one-deploy-pull`（`CreatePolicy` 后 `AttachPolicyToRole` 到 ECS 现有实例角色 `forcitis-one-ecs-archive`；该实例角色承载归档写入与部署请求拉取两份最小策略，不改名、不重绑；prod 的 ECS 另建 prod 版本）：

```json
{
  "Version": "1",
  "Statement": [
    {
      "Effect": "Allow",
      "Action": ["oos:GetParameter"],
      "Resource": [
        "acs:oos:cn-hangzhou:<account-id>:parameter/forcitis-one-deploy-request-dev",
        "acs:oos:cn-hangzhou:<account-id>:parameter/forcitis-one-deploy-request-dev-bundle",
        "acs:oos:cn-hangzhou:<account-id>:parameter/forcitis-one-deploy-request-dev-bundle-sha256",
        "acs:oos:cn-hangzhou:<account-id>:parameter/forcitis-one-deploy-request-dev-statement",
        "acs:oos:cn-hangzhou:<account-id>:parameter/forcitis-one-deploy-request-dev-statement-sig"
      ]
    },
    {
      "Effect": "Allow",
      "Action": ["oos:UpdateParameter"],
      "Resource": ["acs:oos:cn-hangzhou:<account-id>:parameter/forcitis-one-deploy-result-dev"]
    }
  ]
}
```

**信任策略**（`CreateRole` 的 `AssumeRolePolicyDocument`；`MaxSessionDuration` 3600）：

```json
{
  "Version": "1",
  "Statement": [
    {
      "Effect": "Allow",
      "Action": "sts:AssumeRole",
      "Principal": {
        "Federated": ["acs:ram::<account-id>:oidc-provider/<provider-name>"]
      },
      "Condition": {
        "StringEquals": {
          "oidc:iss": "https://token.actions.githubusercontent.com",
          "oidc:aud": "sts.aliyuncs.com",
          "oidc:sub": "repo:FORCITIS@282818954/forcitis-one-psa@1418536044:environment:dev:job_workflow_ref:FORCITIS/forcitis-one-psa/.github/workflows/deploy.yml@refs/heads/main"
        }
      }
    }
  ]
}
```

`oidc:sub` 的这一形式依赖 R-4 ① 的仓库 `sub` 定制（`repo`、`context`、`job_workflow_ref`，保留不可变 sub）：`repo` 段带组织与仓库的数字 ID（`FORCITIS@282818954/forcitis-one-psa@1418536044`，回读的 `sub_claim_prefix`），仓库改名或转移后不会被同名仓库冒用；`context` 对绑定 environment 的作业是 `environment:<名称>`，`job_workflow_ref` 把工作流文件与分支一并钉住。`job_workflow_ref` 段在不可变模式下是否也带 ID，官方文档未写明，首次真实换 STS 凭据时核对实际 `sub`，不符则按实际值改信任策略（仍精确匹配）。定制前 GitHub 发出的默认 `sub` 与条件不符，STS 拒绝——失败关闭。用 `StringEquals` 精确匹配，不用通配。

### 8.3 GitHub 直拉的链路（杭州 ECS）

| 项 | 情况 | 处理 |
|---|---|---|
| 速度 | 北京时间夜间在 ECS 上实测 `release-assets.githubusercontent.com` 约 9 MB/s；60 MB 左右的发布包约 7 秒 | 白天未测：**首次部署时在白天测**（安装器日志中「fetched … bytes」前后的时间），结果记入本节 |
| 可用性 | 经公网跨境，偶有抖动或中断 | 安装器下载 20 分钟超时；失败即本次部署失败，Re-run 或重新运行 deploy（新的运行尝试号、新的签名地址） |
| 签名地址有效期 | GitHub 文档未写明附件签名地址的有效期，只说是短期地址 | `ship.sh` 打印地址中的到期时间（`se`，不打印地址本身），**首次部署时记下实测值**入本节；拉取模式下 ECS 最迟在写入后约 1 分钟（加上前一次运行未结束的时间）开始下载，四个文件依次取，发布包最后取；过期则下载失败（原因 `download`），部署失败，重新运行即可 |
| 访问的地址 | ECS 访问元数据服务 `100.100.100.200`、`oos.cn-hangzhou.aliyuncs.com`（每分钟读请求参数，写结果参数）与请求中的签名地址（主机限 `objects.githubusercontent.com`、`release-assets.githubusercontent.com`）；不再访问 `api.github.com` | ECS 出方向 443 已放行（E-14），GitHub 实测可达；OOS 在 R-11 ② 验证 |
| GitHub 不可用 | 期间无法部署 | 回滚只用本机已装版本，不受影响 |

## 9. 首次部署（dev）的先后

1. C-1（扩容，可后置）→ C-3（建库、角色、口令；RDS SSL 与 CA）→ C-4 E-1..E-10（ECS 初始化，含 E-3、E-5、E-9、E-10 新做法）→ B-4（证书，nginx 启用）。
2. 本文 R-0..R-12（R-3、R-13 不再需要；R-4、R-6、R-7、R-11 由运维执行）；B-2、B-3 完成后填 R-2 的构建期变量。
3. 1a 修复批合并、opus 复审通过，调度会话删除 `status/blockers/080-*`（或其 `blocks: deploy` 标记）；1a 的「可执行 jar」修复已合并，否则 `build.sh` 拒绝出包。
4. `release` 发 `0.1.0`（或 `-rc.1`），核对后发布 Release。
5. `deploy` dev：首次的迁移凭据目录含 Flowable 安装项（§4）；部署成功后跑 C-3 的 V8，删除 Flowable 安装项。
6. C-5（首任超管与应急账号；运维命令未实现，阻塞于 blockers/070）。
7. INFRA_RUNBOOK §7 第 5 步冒烟、`ecs_check.sh --public-check`；D 节出口条件在 dev 上逐项验收。

prod 的部署还要求：正式版本（非 `-rc`）、清单 D 节全部 ✅、C-9 ✅、`prod-approvals.txt` 中有该构建的批准行（版本、提交、发布包 SHA256 都对上）。

## 10. 风险与已知限制

- **部署 runner 的隔离靠 R-0 落实**：runner 程序、hook 与其配置归 root；PATH 与所用程序均为 root 所有或按 SHA256 下载；进程主目录为 root 所有的空目录、作业改用新 `HOME`；`forcitis-deploy` 非管理员。任何一项被放松，CI 作业就可能影响部署。
- release 构建在 GitHub 托管机上执行第三方依赖中的代码（Maven 插件、前端构建工具；依赖的安装脚本已用 `--ignore-scripts` 关掉），它们能影响本次构建产物，这一点任何套餐都不能消除；签名在另一个托管作业中，私钥不与之同机。
- 临时凭据与签名地址被拿到的后果见 §8.1：装不上 release 工作流没有签过的东西，也不能让 ECS 执行任何命令（部署角色没有任何 ECS 接口）。怀疑部署角色被滥用时，运维在 RAM 中改信任策略或删除角色即全部失效，不需要轮换任何密钥。
- **request_id 可被提前占用**：能写请求参数的身份可以抢先写一个以将来某次运行的 `run_id:run_attempt` 为 `request_id` 的请求，让那次运行到来时被当作已处理而静默跳过（拒绝服务）。GitHub 的 `run_id` 是全站递增的大整数，难以预先猜中具体值，且只有部署角色（或账号管理员）能写请求参数，风险低；发生时 `ship.sh` 会等到超时并提示查看 ECS 日志。
- **结果参数只用于回报**：`ship.sh` 只按 `request_id` 读它、据此决定作业成败与提示，任何安全判断（装不装、装什么）都不依赖它。实例角色或账号管理员能改写它，后果只是 Actions 上的回报不实；以 ECS 上的日志、`processed-requests` 与 `forcitis-one-release status` 为准。
- **部署作业的成败是回报，不是控制**：作业超时或被取消后，ECS 仍可能在之后的某一轮受理同一请求（参数还在）。`ship.sh` 失败时提示运维到 ECS 查看定时器日志，需要撤回时把请求参数改回 `{}`。
- **兜底迁移单元**：`systemd-run` 起的迁移单元带 `RuntimeMaxSec=900`；拉取被中断时 TERM 处理与 `ExecStopPost` 都会 `systemctl stop 'forcitis-one-migrate-*'`，迁移进程不会作为孤儿常驻。被中断的迁移结果未知，以 `one_migration.flyway_schema_history` 为准（Flyway 每条迁移在事务中执行）。
- **拉取有 1 分钟左右的延迟**，每分钟有 4 次元数据请求（会话令牌、地域、角色名、角色凭据）与 1 次 `GetParameter`（只在有新请求时多读 4 个参数、写 2 次结果）。OOS 的接口调用与参数数量都在免费额度与配额内（每地域 10000 个普通参数）。
- **签名地址留在 OOS 参数中，并在部署 runner 的命令行上出现**：四个签名地址保存在 OOS 参数里直到下一次部署覆盖，能读这些参数的身份（部署角色没有读权限；ECS 实例角色、账号管理员）都看得到；`ship.sh` 以 `aliyun oos UpdateParameter --Value <地址>` 写入时，地址在部署 runner 上该进程的命令行中可见（同机其他用户可用 `ps` 看到，部署 runner 只有 `forcitis-deploy` 一个作业用户）。ECS 上的安装器不把它们放进命令行、日志或磁盘。GitHub 只把它们签为短期有效（有效期文档未写明，首次部署时按 `ship.sh` 打印的 `se` 记录实测值，§8.3），过期即无用；而且附件对仓库的任何读者本就可以下载，不是机密。Actions 日志不打印签名地址。
- **安装内容由请求指明，可信度来自调用方身份与签名**：ECS 不再向 GitHub 核对部署记录与运行；「谁能写请求参数」由 RAM 策略（只有部署角色能 `UpdateParameter`）与阿里云信任策略（`iss`、`aud`、`sub` 精确匹配，`sub` 钉住仓库、environment 与 main 上的 deploy.yml）保证，「装的是什么」由本机公钥验签的发布声明保证。账号中其他拥有 `oos:UpdateParameter` 通配权限的身份（如管理员）也能改写请求参数，但同样只能让 ECS 装经签名的版本。部署角色的信任策略或 GitHub 的 `sub` 定制被改宽，前一条保证就随之变弱，两者的修改都应经用户确认并回读（R-4）。prod 批准行仍只由 deploy 作业核对（U-5）。
- **OOS 参数版本**：普通参数每次 `UpdateParameter` 生成一个新版本（`ListParameterVersions` 可查）；官方文档没有写单个参数的版本数上限（只写了每地域 10000 个普通参数与值长 4096 字符）。每次部署写 5 次请求参数、2 次结果参数；安装器只在受理新请求时写结果，静默跳过的轮次不写。若将来遇到版本数上限（`UpdateParameter` 报配额错误，部署失败关闭），处理办法：运维 `DeleteParameter` 后以同名、同类型、占位初值重建（ARN 不变，两份策略无需改动），R-11 ② 记录版本是否累积。
- **OOS 参数值上限 4096 字符**（官方文档）：一个签名地址实测约 930 字符，因此每个地址各占一个参数，请求 JSON 约 470 字符；`ship.sh` 与安装器都拒绝超过 4096 字符的值。GitHub 若把签名地址加长到超过 4096，部署失败关闭（`ship.sh` 报「signed link has N characters」），届时再定方案。真实上限在 R-11 ② 验证。
- **请求参数不是原子写入**：五个参数逐个写，安装器可能读到一次运行的请求配另一次运行的地址（deploy.yml 的 `concurrency: deploy-<环境>` 只让运行排队，前一次运行超时或被取消时仍可能交错；拉取定时器也可能恰在写入途中读取；运维手工改写参数也会造成）。防护：请求带 `request_id` 与四个地址值的 SHA-256，安装器在防重放记录与下载之前逐个核对，不符即拒绝并发 P2 告警。不只靠验签兜底：错配的地址也可能指向另一个已签名的版本，验签会通过、版本核对才拦住；哈希核对让错配在下载前就被认出并告警。这类拒绝**不消耗**防重放记录：混搭是写入竞态，不是重放，记下它反而会让修正后的同一请求无法再发。
- `remote_install.sh`、`ship.sh`、`toolchain.sh` 依赖 Linux / 阿里云接口 / GitHub API / 网络下载，本机只做了语法、静态检查与可本地运行部分的验证（请求校验、下载、验签与版本核对由 `install_request_test.sh` 以本地签名的测试附件覆盖）；`ship_test.sh` 以替身 gh / curl / aliyun 覆盖 Location 解析、STS 应答解析、屏蔽、参数写入与结果轮询，`install_request_test.sh` 以本机替身服务覆盖元数据加固模式、OOS 签名（另以阿里云 SDK 文档的 RPC 签名示例做已知答案测试）、读写参数、静默跳过、告警限频与单实例；真实的 `AssumeRoleWithOIDC`、`UpdateParameter`、`GetParameter`、systemd 定时器与附件 API 的 302 应答在 R-11 与首次 dev 部署时验证。迁移以 `Started OneApplication` 判定是在 migrate profile 合入前的过渡做法（§4）。
- **OIDC 签发方的证书链会变**：阿里云 OIDC 身份提供商按登记的 CA 证书指纹（至多 5 个）核对 `token.actions.githubusercontent.com` 的证书链。2026-10-15 登记的是当时链上的三张 CA：Let's Encrypt YR2（中间，2028-09 到期）`2D74D6DF…DC37`、Root YR（交叉签名，2032-09）`AB9D0263…E998`、ISRG Root X1（自签根，2035-06）`CABD2A79…A5E8`；叶子证书约 90 天一换，不登记。GitHub 以前用过 DigiCert 链，以后也可能再换。链上没有一张对得上时 STS 拒绝换凭据（失败关闭，部署失败、不会放错身份进来）。处置：运维在运维机 `openssl s_client -connect token.actions.githubusercontent.com:443 -showcerts` 取当前链，逐张算 SHA-1 指纹（根证书再与操作系统根证书库核对），`UpdateOIDCProvider` 增补（保留仍有效的旧指纹，总数不超过 5），`GetOIDCProvider` 回读后重新运行 deploy；变更报 one-coordinator 并记入本节。
- **拉取单元的沙箱可再收紧**（R-11 ⑧：`systemd-analyze security` 8.3 EXPOSED，主要因为以 root 运行、要用 systemctl 与 systemd-run 切换、重启和迁移，属设计需要）：候选项 `UMask=0027`（安装器已 `umask 027`，敏感文件 `umask 077`，单元里只是重复声明，约 0.1 分）、`PrivateDevices`、`ProtectKernelTunables`、`ProtectKernelModules`、`ProtectKernelLogs`、`ProtectClock`、`ProtectHostname`、`RestrictNamespaces`、`LockPersonality`、`SystemCallArchitectures=native`。**触发条件：首次 dev 部署跑通后、下一次改安装器或单元时一并做**（改单元会使发布包与 ECS 已装文件不一致，须重装单元再发布）；逐项验证 `systemd-run` 迁移、`setfacl`、`systemctl restart` 与健康检查不受影响，在 dev 上实测后再推广。
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
| Environment secrets | 可用：私有仓库须 GitHub Pro、Team 或 Enterprise | 同上 | 只有 `RELEASE_SIGNING_KEY`；阿里云凭据改由 OIDC 临时换取，不存 secret |
| Runner groups | 可用（组织级）：「Organization owners using the GitHub Team plan can create additional organization-level runner groups.」 | [Managing access to self-hosted runners using groups](https://docs.github.com/en/actions/how-tos/manage-runners/self-hosted-runners/manage-access) | 专用 runner 注册在仓库级 |
| Runner group 只限指定工作流（Workflow access → Selected workflows） | **视为不可用**：该节只出现在 Enterprise Cloud 版文档，Free / Pro / Team 版同一页没有；组织现有 runner group 的 `restricted_to_workflows` 均为 `false` | [Enterprise Cloud 版同页](https://docs.github.com/en/enterprise-cloud@latest/actions/how-tos/manage-runners/self-hosted-runners/manage-access) 对照 [Team 版](https://docs.github.com/en/actions/how-tos/manage-runners/self-hosted-runners/manage-access) | 改由 runner 侧 pre-job hook 实现 |
| Pre-job hook（`ACTIONS_RUNNER_HOOK_JOB_STARTED`） | 可用（runner 功能，不分套餐）：「If there is any other exit code, the job will not run and will be marked as failed.」；「Scripts have access to the default variables. The full webhook event payload can be found in `GITHUB_EVENT_PATH`.」 | [Running scripts before or after a job](https://docs.github.com/en/actions/how-tos/manage-runners/self-hosted-runners/run-scripts) | `runner_job_started.sh` |
| Immutable releases | 文档未写套餐限制；本仓库 `GET /repos/FORCITIS/forcitis-one-psa/immutable-releases` 返回 `{"enabled":false}`，判断为可用、未启用；「immutability will only apply to future releases」 | [Immutable releases](https://docs.github.com/en/code-security/supply-chain-security/understanding-your-software-supply-chain/immutable-releases)、[Preventing changes to your releases](https://docs.github.com/en/code-security/how-tos/secure-your-supply-chain/establish-provenance-and-integrity/prevent-release-changes) | R-9 启用；与签名声明互补 |
| Rulesets（含 tag rulesets） | 可用：「Rulesets are available in public repositories with GitHub Free and GitHub Free for organizations, and in public and private repositories with GitHub Pro, GitHub Team, and GitHub Enterprise Cloud.」 | [About rulesets](https://docs.github.com/en/repositories/configuring-branches-and-merges-in-your-repository/managing-rulesets/about-rulesets) | R-8 `v*` 标签保护 |
| Push rulesets（按文件路径限制推送） | 可用：「Push rulesets are available for the GitHub Team plan in internal and private repositories …」 | 同上 | 可选，见 U-3 |

### 11.2 H-2 的 Team 方案：签名发布声明

目标不变：只部署由 main 上 release 工作流构建的包，Release 附件被替换能发现。

- **签名**：私钥只存在 environment `release` 的 secret 中，该 environment 只允许 main；`publish` 在 GitHub 托管的 `ubuntu-24.04` 上运行，只检出工作流自身提交的 `scripts/release`，签完立即删除私钥文件。声明写明 `GITHUB_WORKFLOW_REF` 与已证明的提交，并断言等于 `manifest.json` 的 `gitSha`。
- **校验两处**：deploy 作业用仓库 main 上的 `allowed_signers` 验签；ECS 安装器用本机 root 所有的 `/etc/forcitis-one/release-signers` 再验一次，发布包、声明、签名由 ECS 按安装请求中的签名地址从 GitHub 的附件主机直接下载。两处都核对构建工作流必须是 `FORCITIS/forcitis-one-psa/.github/workflows/release.yml@refs/heads/main`、版本、发布包名，以及发布包与 `manifest.json`、`SHA256SUMS` 的 SHA256；deploy 作业另核对声明中的提交等于标签所指提交，ECS 核对 `manifest.json` 的 `gitSha` 等于声明中的提交。
- 本机验证：用运行时生成的测试密钥，正常声明通过；空公钥、改提交、改声明、换密钥、分支工作流签出的声明、替换发布包、写声明时提交与 manifest 不符，全部被拒。
- **专用 runner 被任意分支利用的封堵**：pre-job hook（R-0）只放行 main 上手动运行的 deploy.yml，并把 `GITHUB_*` 变量与 runner 写在 `_work/_temp` 的事件载荷交叉核对；hook、其环境变量（在 root 所有的 plist 中）与 runner 程序都归 root。hook 不再做残留进程检查：那只能报告、不能阻止，不算控制。

### 11.3 用户决定（升级 / 接受）

| # | 事项 | 需要 Enterprise Cloud 才能做到的 | Team 下现状 | 选项 / 结论 |
|---|---|---|---|---|
| U-1 | 出处由 GitHub 背书 | 私有仓库的 artifact attestations | 签名发布声明 | **接受**（建议） / 升级后改回 attestations |
| U-2 | prod 部署前由指定的人在 GitHub 上批准 | environment required reviewers、wait timer | `prod-approvals.txt` 批准行（版本、提交、发布包 SHA256）经 PR 合入 main；所有会话以同一 GitHub 账号推送与合并，批准行证明「经过 main 的评审流程」，不能证明「由某个特定的人批准」 | **接受**并约定批准行只由用户本人发起的 PR 加入 / 升级 |
| U-3 | runner 只接指定工作流 | runner group 的 Selected workflows | runner 侧 pre-job hook + R-0 的 root 所有权 | **接受**（建议）/ 升级 / 另加 push ruleset 限制 `.github/workflows/**`（须先让代理改用单独账号才有意义） |
| U-4 | 签名私钥与构建依赖代码同机 | — | **已决定**：签名放 GitHub 托管机器（用户：「签名步骤放 GitHub 托管机器 (Recommended)」）；随后发布打包也移到托管机器（用户：「发布打包改到 GitHub 托管机器 (Recommended)」），部署 runner 只跑 deploy | 已实施 |
| U-5 | prod 只装已批准的构建，即使部署凭据泄露 | — | 批准行在仓库中、由 deploy 作业核对，ECS 不知道批准；请求参数只有 prod 部署角色能写，而只有 main 上 deploy.yml 的 prod deploy 作业能经 OIDC 扮演它（§8.2），该作业在写请求前已核对批准行；拿到临时凭据者也装不上未签名的东西，且没有任何执行接口（§8.1）。残余：批准仍由 deploy 作业而非 ECS 验证（§10）。未做「批准签名由 ECS 验证」：immutable releases 下 Release 附件发布后不可追加，批准签名需要另一套签发与存放流程（例如单独的批准工作流、由 `release` environment 的密钥签批准声明并另存），成本与本阶段收益不相称 | **接受**（prod 部署角色只信任 prod environment 中 main 上的 deploy.yml，凭据 1 小时有效、只在部署 runner 上出现）/ 生产前再加批准签名 |
