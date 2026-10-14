# 发布与部署（release / deploy 工作流）

整理：one-coordinator 子代理，2026-10-14。补 `PREDEPLOY_CHECKLIST.md` 遗漏项「CI 无发布产物」与 `CROSSCHECK.md` 第 15 条。

**当前禁止部署任何环境**（`status/blockers/080`）。`deploy` 工作流在该阻塞存在时直接失败；本文的一次性步骤只是准备，涉及 GitHub 设置、ECS、安全组的每一步仍须用户确认后执行（清单 B-10）。

| 文件 | 作用 |
|---|---|
| `.github/workflows/release.yml` | 由 main 上已通过 ci 的提交构建发布包，附到 GitHub Release |
| `.github/workflows/deploy.yml` | 把已发布（非草稿）的版本部署到 dev / prod；只写好，未运行过 |
| `scripts/release/build.sh` | 构建发布包；本机可直接运行，工作流只是调用它 |
| `scripts/release/verify_bundle.sh` | 按部署时的方式校验发布包（归档 SHA256、`SHA256SUMS`、`manifest.json`、jar 可启动） |
| `scripts/release/ci_passed.py` | 证明提交在 main 上且 ci 三项必需检查成功 |
| `scripts/release/deploy_gate.py` | 部署守护：阻塞条目、prod 出口条件 |
| `scripts/release/remote_install.sh` | ECS 上的安装器（装为 `/usr/local/sbin/forcitis-one-release`） |

## 1. 版本号

- 形如 `MAJOR.MINOR.PATCH`，预发布加 `-rc.N`（`0.1.0`、`0.1.0-rc.1`）；标签为 `v<版本>`。Phase 1a 期间用 `0.x`。
- 一个版本只构建一次：同名 Release 已存在则 `release` 失败；ECS 上同一版本内容不同则拒绝安装。修复一律发新版本。
- `backend/pom.xml` 的 `<version>` 目前固定为 `0.0.1`，jar 内的 `Implementation-Version` 与发布版本不一致；以 `manifest.json` 的 `version` 与 `gitSha` 为准（给 1a 的建议见报告）。

## 2. 发布（`release`）

触发：推送 `v*` 标签，或手动运行（输入版本、可选提交 sha、是否草稿）。标签推送生成**草稿**；手动运行默认草稿。`deploy` 只接受已发布的 Release，草稿经人核对后在 GitHub 上点「Publish」。

步骤：

1. **证明这个提交测过**（`ci_passed.py`）：提交必须是 `origin/main` 的祖先；GitHub check-runs 中 `ci` 工作流的三项必需检查（backend verify、frontend verify、secret scan）最新一次都是 `success`。不重跑测试。三项检查名取自 `ci.yml`，`ci.yml` 改名而脚本未同步时直接失败。
2. **构建**（`build.sh`）：工作区必须干净；校验 Java 25、Node 24、锁定的 pnpm；
   - 后端：`./mvnw -B clean package -DskipTests`。POM 尚未声明 `spring-boot-maven-plugin` 时，脚本临时加 `repackage` 目标并指定启动类，然后检查 jar 能被 `java -jar` 启动（Boot 可执行 jar、Start-Class 是 `OneApplication`、该类有 `main`），不满足即失败。
   - 前端：PC（web）与 H5 按 dev、prod 各构建一份（构建期变量不同，见 §6），H5 放在 PC 产物的 `h5/` 下。
3. **打包与校验**：`forcitis-one-<版本>.tar.gz` 与 `.sha256`；`verify_bundle.sh` 解包后复核一遍。
4. **发布**：`gh release create v<版本> --target <sha>`，两个文件作为附件。

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
| 保留期 | 永久，随标签 | 最长 90 天（私有仓库默认 90），过期即无法回滚到该版本 |
| 寻址 | `v<版本>` 标签，`gh release download` | 运行 ID，需先查运行 |
| 草稿 / 人工发布 | 支持，`deploy` 只认已发布的 | 无 |
| 文件形式 | 原样 | 自动再打 zip |
| 成本 | 不计入 Actions 存储 | 计入 Actions 存储配额 |

## 3. 部署（`deploy`）

手动运行，输入环境（dev / prod）、版本；prod 另须在 `confirm` 再输一次版本。

1. `gate` 作业（不进 environment，失败不必等审批）：版本格式；prod 的确认；`deploy_gate.py`：
   - 仓库 `docs/modernization/status/blockers/` 中有 `080-*` 或文件名含 `no-deploy` 的条目 → 失败。修复批合并后由调度会话删除该条目，部署才可能进行。
   - prod：`PREDEPLOY_CHECKLIST.md` D 节（Phase 1a 出口条件）每一行状态都为 ✅，且行数不少于 9；表格改了形状时失败而不是放行。出口条件只记在清单一处，守护直接读它，不另设第二份清单；`confirm` 输入防止误点。
2. `deploy` 作业（`environment: dev / prod`；prod 须 required reviewers 批准后才开始）：
   - 再跑一次 `deploy_gate.py`（等待审批期间 main 可能新增阻塞）；
   - Release 非草稿；标签所指提交仍满足 `ci_passed.py`；
   - 下载附件，`verify_bundle.sh` 校验；
   - SSH 把发布包经标准输入交给 ECS 上的 `forcitis-one-release install <版本> <环境> <发布包 sha256>`。私钥与主机公钥来自 environment secret，`StrictHostKeyChecking=yes`、只用该 known_hosts，不读 ssh-agent，作业结束删除临时文件；不在日志打印密钥。

### ECS 上的安装器（`remote_install.sh`）

deploy key 在 `authorized_keys` 中是强制命令，只能运行安装器的 `install`、`rollback`、`status`，拿不到 shell；安装器以 sudo（仅此一个命令）切到 root。`install` 依次：

1. 检查 `/etc/forcitis-one/one.env` 的 `SPRING_PROFILES_ACTIVE` 等于目标环境（同一台 ECS 先作 dev、切生产时改配置，防止把 prod 包装到 dev 配置上或反之）；取部署锁。
2. 接收发布包（≤ 1 GiB），SHA256 与 Release 公布的一致；归档条目只能在 `forcitis-one-<版本>/` 下，无链接、无 `..`；解包后 `sha256sum --strict -c SHA256SUMS`，且不允许有清单外的文件；`manifest.json` 的版本一致。
3. **服务器文件一致**（新装的版本）：已安装的安装器、systemd 单元、nginx 配置、logrotate 与发布包中 `deploy/` 的同名文件逐字节相同，`one.env` 含模板中的每个键；不一致即失败并列出（服务器配置的变更按 §5 走，经用户确认，不由部署顺带改）。
4. 放到 `/data/forcitis-one/releases/<版本>/`（root:forcitis，目录 750、文件 640），`web` 软链指向 `web-<环境>`；给 nginx 用户只读 ACL（`/data/forcitis-one` 是 750，nginx 默认进不去）。
5. 迁移（§4）。
6. 原子切换 `current` 软链，`systemctl restart forcitis-one`，经本机回环查 `http://127.0.0.1:<MANAGEMENT_SERVER_PORT>/actuator/health` 为 `UP`（最长 180 秒）。
7. 不健康：自动切回原来的 `current` 并重启，原版本健康则以「已回切」报错退出，原版本也不健康则报「需人工处理」；首次部署没有上一版本时停止服务并报错。
8. 健康：上一版本记为 `previous`；保留最近 5 个版本（`current`、`previous` 之外），更旧的删除；每次结果追加到 `releases/HISTORY`。

静态资源与 jar 在同一版本目录里，随 `current` 一起切换和回切。

### 回滚

- 首选：用 `deploy` 部署上一个版本号。发布包照样上传、校验；ECS 上已装过且内容一致的版本直接复用，不再比对服务器文件（首次安装时已比对过），因此回到旧版本不会被「服务器文件不一致」挡住。
- 应急（GitHub 不可用）：运维经云助手执行 `forcitis-one-release rollback`（回到 `previous`）或 `forcitis-one-release rollback <版本>`；`forcitis-one-release status` 查看当前、上一版本与历史。
- 回滚只换应用，不回滚数据库（§4）。

## 4. 数据库迁移

**现状（核对代码与剧本）**：迁移只有 Spring Boot 启动时自动运行的 Flyway 一条路，代码中没有单独的迁移入口；未设 `spring.flyway.user`，所以 Flyway 用的是运行连接（`one_dev_app`）。而 C-3 的 `one_dev_app` 只有 DML、对 `one_migration` 没有 USAGE、`search_path` 为 `platform_approval`：常驻进程若照默认跑 Flyway，会在 `platform_approval` 找不到历史表、发现非空 schema 而拒绝启动。剧本（INFRA_RUNBOOK §2、§5，C-4「首次部署」）的意图是「迁移以 `one_dev_migrator` 单独执行，常驻进程不持有迁移凭据」，与代码默认行为不一致。本流水线按剧本意图落地：

- `one.env` 设 `SPRING_FLYWAY_ENABLED=false`（已加入 `files/one.env.template`）：常驻进程不跑 Flyway。
- 安装器在**有新迁移时**（新版本 `migrations.txt` 中有当前版本没有的文件；首次部署即全部）单独运行一次迁移：以 `forcitis` 用户起临时 systemd 单元，加载 `one.env` 与 `/etc/forcitis-one/migrate.env`（root 600，含 `SPRING_FLYWAY_USER=one_dev_migrator` 与口令），命令行打开 `spring.flyway.enabled`、指定 `default-schema=one_migration`，关闭各定时作业、企业微信投递与流程引擎异步执行器，非 Web 模式启动；看到 `Started OneApplication` 即视为迁移完成并停止该单元，失败或 15 分钟未完成则报错、不切换版本。迁移日志在 `/data/forcitis-one/logs/forcitis-one-migrate-*.json`。
  - 开关一律放在 java 命令行：systemd 中 `EnvironmentFile=` 的值覆盖 `Environment=`，C-4 原写法的 `-p Environment=…=false` 会被 `one.env` 中的同名值顶掉（C-4 已加注）。
  - 这是在应用尚无「迁移后退出」模式时的做法；1a 补上该模式后，安装器改为等待进程退出码（给 1a 的建议）。
- 两个版本中同名迁移文件内容不同 → 失败（迁移只追加，CI 的 `migrations_immutable.py` 已守护，部署再守一次）。
- **只前进不回滚**：不写 undo 迁移，回切应用版本时数据库保持新结构。因此每个迁移必须让**上一个版本的应用**照样能跑（只扩不缩）：
  - 允许：新表、新列（可空或有默认值）、新索引、新视图、放宽约束；
  - 不允许在同一版本里：删除或改名表 / 列、收紧约束、改列类型、改既有数据的含义。确需收缩时分两步：先发不再使用旧结构的版本，等它成为回滚基线（至少一个版本之后）再发删除旧结构的迁移；
  - 回切到不认识新迁移的旧版本时，安装器打警告并照常切换；旧版本常驻进程不跑 Flyway，不会因「库里有未知迁移」拒绝启动（即便以后打开 Flyway，其默认 `ignoreMigrationPatterns=*:future` 也容忍未来版本的迁移）。
  - 审查迁移时按上述规则判断，PR 中写明「上一版本兼容」。
- 首次部署另需建 Flowable 引擎表：首次的 `migrate.env` 按 C-4「首次部署」第 2 步再加 `FLOWABLE_SCHEMA_UPDATE=true`、迁移账号的 `SPRING_DATASOURCE_USERNAME` / `_PASSWORD` 与带 `&currentSchema=platform_approval` 的 `SPRING_DATASOURCE_URL`；首次部署成功并跑完 C-3 的 V8 后，从 `migrate.env` 删掉这几行。

## 5. 服务器文件的变更

systemd 单元、nginx 配置、logrotate、`one.env` 的键、安装器本身都随仓库版本管理（本目录 `files/` 与 `scripts/release/remote_install.sh`）。这些文件变了的版本，部署会因「服务器文件不一致」失败；须先由运维按 C-4 的方式（经用户确认）从**该版本的发布包** `deploy/` 中安装新文件（`nginx -t` 后 reload、`systemctl daemon-reload`），再部署。安装器的更新同理（见 §7 R-5）。

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

| # | 步骤 | 位置 / 做法 |
|---|---|---|
| R-1 | 建 GitHub environments `dev`、`prod`；两者「Deployment branches and tags」只允许 `main`；`prod` 设 required reviewers（至少 1 人，建议 2 人，勾选禁止自己批准自己发起的部署） | 仓库 Settings → Environments |
| R-2 | 仓库级 Actions variables：§6 的 8 个变量（B-2、B-3 完成后填） | Settings → Secrets and variables → Actions → Variables |
| R-3 | 每个 environment 的 variables：`DEPLOY_HOST`（ECS 地址，见 §8）、`DEPLOY_USER=forcitis-deploy`、`DEPLOY_PORT`（默认 22，可不设） | Environments → dev / prod |
| R-4 | 每个 environment 的 secrets：`DEPLOY_SSH_KEY`（该环境专用 ed25519 私钥）、`DEPLOY_KNOWN_HOSTS`（ECS 主机公钥一行，形如 `<DEPLOY_HOST> ssh-ed25519 AAAA…`）。私钥在可信机器上 `ssh-keygen -t ed25519 -N '' -C forcitis-one-deploy-<环境>` 生成，私钥只进 secret，公钥给运维；主机公钥由运维经云助手读取 `/etc/ssh/ssh_host_ed25519_key.pub` 交来，**不要用 `ssh-keyscan` 从网络获取** | Environments → dev / prod |
| R-5 | ECS 准备（运维经云助手，root）：见下方命令块 | ECS |
| R-6 | 安全组入方向放行 TCP 22，**源地址只填 runner 所在网络的固定出口 IP/32**（§8）；同时 sshd 关闭口令登录、只允许 `forcitis-deploy` | 安全组 sg-bp1hjbq8dkoaxa2nljzq；`/etc/ssh/sshd_config` |
| R-7 | 标签保护：仓库 Rules → Rulesets 新建标签规则 `v*`，限制创建、更新、删除（仅管理员与 release 工作流） | Settings → Rules |
| R-8 | 确认 Actions 允许工作流声明 `contents: write`（release 要建 Release）；组织若把工作流权限上限设为只读，release 会在最后一步失败 | Settings → Actions → General |
| R-9 | 首次部署前在 `/etc/forcitis-one/one.env` 补 `SPRING_FLYWAY_ENABLED=false`，并按 §4 建 `/etc/forcitis-one/migrate.env`（root:root 600） | ECS |

R-5 命令（`<version>` 用将要部署的发布包；先在可信机器上 `verify_bundle.sh` 校验再经云助手上传 `deploy/remote_install.sh`）：

```bash
useradd --system --create-home --home-dir /var/lib/forcitis-deploy --shell /bin/bash forcitis-deploy
install -o root -g root -m 0755 remote_install.sh /usr/local/sbin/forcitis-one-release
install -d -o forcitis-deploy -g forcitis-deploy -m 0700 /var/lib/forcitis-deploy/.ssh
# 一行：from= 填 §8 的 runner 出口 IP；command= 使 deploy key 只能运行安装器
echo 'restrict,from="<runner 出口 IP>",command="/usr/local/sbin/forcitis-one-release" ssh-ed25519 AAAA… forcitis-one-deploy-dev' \
  > /var/lib/forcitis-deploy/.ssh/authorized_keys
chown forcitis-deploy:forcitis-deploy /var/lib/forcitis-deploy/.ssh/authorized_keys && chmod 0600 /var/lib/forcitis-deploy/.ssh/authorized_keys
echo 'forcitis-deploy ALL=(root) NOPASSWD: /usr/local/sbin/forcitis-one-release' > /etc/sudoers.d/forcitis-deploy
chmod 0440 /etc/sudoers.d/forcitis-deploy && visudo -cf /etc/sudoers.d/forcitis-deploy
dnf -y install acl && setfacl -m u:nginx:--x /data/forcitis-one /data/forcitis-one/releases
forcitis-one-release status
```

安装器更新：新版本的 `deploy/remote_install.sh` 与已装的不同，部署会提示；运维以同样的 `install` 命令替换后再部署。

## 8. 从本机 runner 到 ECS 的连通

runner 是本机 Mac（`CI_RUNNERS.md`），ECS 目前安全组入方向只开 80 / 443、不设登录密钥，运维经云助手操作。部署需要其中一种通路：

| 方案 | 条件 | 优点 | 缺点 |
|---|---|---|---|
| **A. SSH 推送（推荐，已实现）** | runner 所在网络有**固定公网出口 IP**；安全组 22 只对该 IP/32 开放；deploy key 强制命令 + `from=` 限源 | 实现简单，所有校验在 GitHub 侧与 ECS 侧各做一遍；不需要云账号凭据进 GitHub | ECS 多开一个入口（只对一个 IP）；出口 IP 变了要改安全组与 `from=`；违背「不设密钥」的原约定，须用户认可 |
| B. 云助手 + OSS（ECS 侧拉取） | 私有 OSS bucket；GitHub 里放只能写该 bucket 与调用云助手 RunCommand 的 RAM 子账号 AccessKey；ECS 绑定只读该 bucket 的 RAM 角色 | 不开任何入方向端口；与「运维经云助手」一致；出口 IP 无要求 | 阿里云长期 AccessKey 进 GitHub（权限再小也是云账号凭据）；多一套 OSS / RAM 配置；部署工作流改为「上传 OSS → RunCommand 调安装器（从 OSS 取包）」 |
| C. ECS 直接从 GitHub 拉取 | ECS 出方向能稳定访问 GitHub；ECS 上放只读 fine-grained token | 无入方向端口 | 从杭州访问 GitHub 下载不稳定；token 放在服务器上 |

推荐 A，前提是本机网络有固定出口 IP（用户确认）。没有固定出口 IP 时改用 B：`remote_install.sh` 不需改（它只读标准输入的发布包），只把 `deploy.yml` 的「install on the server」一步换成上传 OSS + 云助手调用，`DEPLOY_SSH_*` 换成 RAM 凭据。

`DEPLOY_HOST` 用 ECS 公网地址 47.114.56.157（INFRA_RUNBOOK §0）；若以后经 VPN / 专线到 VPC，改内网地址 172.16.89.86。

## 9. 首次部署（dev）的先后

1. C-1（扩容，可后置）→ C-3（建库、角色、口令；RDS SSL 与 CA）→ C-4 E-1..E-10（ECS 初始化）→ B-4（证书，nginx 启用）。
2. 本文 R-1..R-9；B-2、B-3 完成后填 R-2 的变量。
3. 1a 修复批合并、opus 复审通过，调度会话删除 `status/blockers/080-*`；1a 的「可执行 jar」修复（报告中的建议）已合并，否则 `build.sh` 拒绝出包。
4. `release` 发 `0.1.0`（或 `-rc.1`），核对后发布 Release。
5. `deploy` dev：首次的 `migrate.env` 含 Flowable 安装项（§4）；部署成功后跑 C-3 的 V8，删除 `migrate.env` 中的 Flowable 安装项。
6. C-5（首任超管与应急账号，用已部署的 jar 执行运维命令；运维命令未实现，阻塞于 blockers/070）。
7. INFRA_RUNBOOK §7 第 5 步冒烟、`ecs_check.sh --public-check`。

prod 的部署还要求清单 D 节全部 ✅、C-9 完成、prod environment 的 reviewers 批准。

## 10. 风险与已知限制

- **本机 runner 与部署密钥同机**：4 个 runner 以同一 macOS 用户运行，PR 的作业与部署作业可能同时在这台机器上。部署期间私钥短暂落在 `$RUNNER_TEMP`，同机其他作业理论上可读。强制命令把私钥能做的事限制为「安装一个自带 SHA256 的发布包」，但攻击者自带的包也能通过校验。建议另设一个专用 runner（独立 macOS 用户、标签 `forcitis-one-deploy`）只跑 `deploy`，届时把 `deploy.yml` 的 `runs-on` 改为该标签。
- `remote_install.sh` 依赖 Linux（systemd、GNU coreutils、flock、setfacl），本机只做了语法检查，首次在 dev 部署时验证；迁移完成以日志中的 `Started OneApplication` 判定，属于过渡做法（§4）。
- 前端按环境各构建一份，同一版本 dev 与 prod 的静态文件不是同一份产物（§6）。
