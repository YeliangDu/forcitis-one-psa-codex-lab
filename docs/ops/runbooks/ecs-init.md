# C-4 剧本：ECS 初始化 E-1..E-17

依据：INFRA_RUNBOOK §3、§5、§7。对象：i-bp122xlpar8w2uylmal2（forcitis-one-production，Alibaba Cloud Linux 4）。时点：dev 前。性质：对外变更，执行前须用户确认；**首次部署（本文末节）须等 1a 修复批合并（status/blockers/080）**。

运维经云助手 / Workbench 以 root 执行，不设登录口令与密钥。

| 文件 | 用途 |
|---|---|
| `scripts/ecs_init.sh` | E-1..E-10；默认只打印，`--apply` 才执行；`--only E-2,E-3` 只做部分 |
| `scripts/ecs_check.sh` | E-1..E-17 只读检查，PASS / FAIL / MANUAL；`--public-check` 加公网检查 |
| `files/forcitis-one.service` | systemd 单元（E-6） |
| `files/forcitis-one.logrotate` | 本机日志轮转 7 天（SLS 保留 30 天） |
| `files/nginx-one.conf` | nginx（E-8..E-10），访问日志不记查询串 |
| `files/one.env.template` | `/etc/forcitis-one/one.env` 模板，只有变量名与非机密默认值 |

把仓库的 `tools/ops/ecs/` 目录（`scripts/` 与 `files/`，#742 起从 `runbooks/` 搬到这里）经云助手「发送文件」上传到 ECS 的 `/root/one-runbooks/`（只含脚本与模板，无机密），再执行；服务器上的目录结构不变。

## 执行前确认点

1. 用户已确认本次执行 C-4（以及是否包含 `dnf -y update`，它会升级内核，需重启）。
2. `lsblk` 确认 200 GB 数据盘设备名（通常 `/dev/vdb`）未分区、未挂载、无文件系统——脚本会再次检查，有文件系统则拒绝格式化。
3. Java 包：确认来源与固定版本（Temurin 25 与 CI 一致；若用 Dragonwell 25 先在 dev 跑完整测试），作为 `--java-pkg` 传入。不从未经确认的地址下载。**实测（2026-10-15）**：Alibaba Cloud Linux 4 自带源（alinux4-os / plus / updates）只有 `java-21-alibaba-dragonwell`，没有任何 Java 25 包；须由用户确认外部来源（Adoptium 的 RPM 源，或 Dragonwell 25 官方发行包）后才能做 E-4。 **已完成（2026-10-15，one-ops，one-coordinator 定 Eclipse Temurin 25）**：Adoptium 官方 RPM 源 `https://packages.adoptium.net/artifactory/rpm/rhel/9/$basearch`（`/etc/yum.repos.d/adoptium.repo`，`gpgcheck=1`），签名公钥指纹 `3B04D753C9050D9A5D343F39843C48A565F8F04B`；装 `temurin-25-jdk-25.0.4.1.0.1-0` 并 `dnf versionlock`；`java -version` 为 Temurin-25.0.4.1+1。
4. B-4（DNS、证书）未完成时，跳过 E-9 并保持 nginx 未启用。

## 步骤

```bash
cd /root/one-runbooks/scripts
./ecs_init.sh --data-disk /dev/vdb --java-pkg <确认的包>            # 先看打印
./ecs_init.sh --data-disk /dev/vdb --java-pkg <确认的包> --apply    # 确认无误后执行
reboot                                                              # 若 E-2 升级了内核；重启后 /data 仍应挂载
./ecs_check.sh | tee /root/c4-check-$(date +%F).txt
```

## 各项：做法 → 校验 → 回滚

| # | 做法 | 校验（`ecs_check.sh`） | 回滚 |
|---|---|---|---|
| E-1 | xfs 挂 `/data`，fstab 用 UUID + `nofail` | 挂载与 fstab；重启后仍在 | 删 fstab 行、`umount /data`（盘内无数据时） |
| E-2 | `dnf -y update`、Asia/Shanghai、chronyd | 时区、同步状态 | 不回滚（内核问题从启动菜单选旧内核） |
| E-3 | 系统用户 `forcitis`（运行）与 `forcitis-migrate`（迁移，主组 forcitis），均 nologin | 用户存在且 nologin | `userdel forcitis-migrate forcitis` |
| E-4 | Java 25，`dnf versionlock` 固定 | `java -version` 为 25 | `dnf remove` |
| E-5 | `/data/forcitis-one` 与 `releases` **root:forcitis** 750（安装器以 root 在其中操作，应用用户不得可写）；`logs`、`tmp` forcitis 750；nginx 对前两层只有穿过（`--x`）ACL；`/etc/forcitis-one` root:forcitis 750；`/etc/forcitis-one/migrate` root 700（迁移凭据，RELEASE.md §4）；one.env 0640 | 属主与权限、ACL；one.env 无迁移凭据且 `SPRING_FLYWAY_ENABLED=false` | 删目录 |
| E-6 | 安装单元与 logrotate（不启动）；**首次部署时再 enable**：发布包放好之前 enable，重启后 jar 不存在，单元按 `Restart=on-failure` 每 10 秒重启一次、不会停 | 首次部署前：单元已安装、disabled；部署后：enabled（`remote_install.sh` 首次安装时发现未 enabled 即 enable，enable 失败则安装失败；此后只 restart）；User=forcitis 与加固项 | `systemctl disable`、删单元 |
| E-7 | one.env：`SERVER_ADDRESS=127.0.0.1`、`MANAGEMENT_SERVER_PORT=8081`、`MANAGEMENT_ENDPOINTS_WEB_EXPOSURE_INCLUDE=health,prometheus` | 8080 / 8081 只在 127.0.0.1；非本机地址只 80 / 443；管理端点只 health、prometheus | 改 one.env |
| E-8 | nginx：80→301、TLS 1.2/1.3、HSTS、覆盖 XFF、20m、屏蔽 `/actuator` `/v3/api-docs` `/swagger-ui`、`Referrer-Policy: no-referrer`、日志不记查询串 | `nginx -t`；日志格式；屏蔽规则；`--public-check` | 删 `/etc/nginx/conf.d/one.conf`，`systemctl reload nginx` |
| E-9 | 证书放 `/etc/nginx/tls/`，600（B-4）。**已装 certbot（`certbot-renew.timer`）的现状**：`/etc/nginx/tls/one.forcitis.com.pem`、`.key` 做成指向 `/etc/letsencrypt/live/one.forcitis.com/fullchain.pem`、`privkey.pem` 的软链，nginx 模板不变；若用 webroot 方式续期，`/etc/letsencrypt/renewal/one.forcitis.com.conf` 的 `webroot_path` 改为 `/data/forcitis-one/static-pinned`（ACME 校验已移出发布目录），续期后 `systemctl reload nginx`（`--deploy-hook`），改完 `certbot renew --dry-run` | 私钥 600（软链看目标）；30 天内不过期；certbot 定时器启用、webroot 指向 static-pinned | 删证书文件 / 还原 renewal 配置 |
| E-10 | 发布包中 `web/`（PC）、`web/h5/`（H5）随 `current` 软链切换。**不随发布切换的文件**放 `/data/forcitis-one/static-pinned/`（root 755，文件 root 0644）：企业微信可信域名校验文件 `WW_verify_*.txt`（nginx 以正则 location 精确匹配该文件名提供）与 ACME 校验目录；安装器不触碰该目录，部署后的健康检查断言其中每个校验文件经 nginx 返回原文，文件名由 ECS 本机 `/etc/forcitis-one/release-source` 的 `WECOM_VERIFY_FILE` 给出时还要求它存在（RELEASE.md R-7）。早先手工放在 `current/web` 的校验文件由 `ecs_init.sh --only E-10` 复制过来；`current` 若是实目录，换用新 nginx 模板并验证后改名移开（安装器遇到实目录拒绝切换） | `current` 为软链；`current/web/index.html` 存在；static-pinned 属主与权限；校验文件经 nginx 返回原文 | 随版本回滚（static-pinned 不随之变化） |
| E-11 | SLS 控制台生成 Logtail 安装命令执行；采集 `logs/*.json` 与 nginx 两类日志；Project 与门户分开 | `ilogtaild` 运行；SLS 能按 `correlationId` 检索 | 卸载 Logtail |
| E-12 | ARMS Prometheus 抓 `127.0.0.1:8081/actuator/prometheus`（job 名 `forcitis-one`） | 端点有 `forcitis_` 指标；ARMS 看板有数 | 删抓取任务 |
| E-13 | 见 `C-7-alerts.md` | MANUAL：演练记录 | 停用规则 |
| E-14 | 安全组出方向（下表），其余出站拒绝 | RDS 端口可达；企业微信 API 可达；MANUAL：安全组截图 | 恢复原规则 |
| E-15 | B-2 | MANUAL：gettoken 正常 | B-2 回滚 |
| E-16 | B-3 | MANUAL：登录冒烟 | B-3 回滚 |
| E-17 | RDS 备份见 C-2；ECS 数据盘快照策略每日、保留 7 天；SLS Logstore 保留 30 天 | MANUAL：控制台截图 | 删策略 |

### E-14 安全组出方向（sg-bp1hjbq8dkoaxa2nljzq）

| 目的 | 协议 / 端口 | 说明 |
|---|---|---|
| RDS PG 内网地址 | TCP 5432 | 同 VPC |
| A8 SQL Server | TCP 1433（以实际端口为准） | B-5；对端白名单放 172.16.89.86 |
| 老系统 RDS SQL Server | TCP 1433（以实际端口为准） | B-5 |
| `qyapi.weixin.qq.com` | TCP 443 | 安全组只能按 IP：先放行 443 到 0.0.0.0/0，后续用 NAT / 出口代理按域名收紧（INFRA_RUNBOOK「其余出站按需收紧」） |
| 致远验票地址（`SEEYON_OA_BASE_URL` 的主机） | TCP 443 / 80 | B-3 |
| 阿里云内部服务（SLS、ARMS、云助手、yum 源） | 100.100.0.0/16 等，按控制台默认 | 不要删除默认的云服务放行 |
| DNS | UDP / TCP 53 到 100.100.2.136、100.100.2.138 | 阿里云内网 DNS |

## 首次部署（dev；须 1a 修复批合并后，且 C-3、B-5 完成）

> 发布流水线已补（`RELEASE.md`）：首次部署改按 `RELEASE.md`「首次部署」执行，由 `deploy` 工作流与 ECS 上的 `forcitis-one-release` 完成下面第 1–3 步。**迁移凭据不再用 `migrate.env`**：改放 `/etc/forcitis-one/migrate/`（root 700，每个 Spring 属性一个 root 600 文件，经 systemd `LoadCredential` 交给以 `forcitis-migrate` 运行的迁移进程，RELEASE.md §4）。下面的手工步骤已过时，仅作流水线不可用时的参考，须按 `scripts/release/remote_install.sh` 的 `migrate` 改写后再用：第 2 步 `-p Environment=` 的开关会被 `EnvironmentFile` 中的同名值覆盖（systemd 规则），应改为 java 命令行参数；`one.env` 须有 `SPRING_FLYWAY_ENABLED=false`。

1. 发布包（版本号 `<ver>`）：`forcitis-one.jar`、`web/`、`web/h5/`、`SHA256SUMS`。发布包由 `release` 工作流产出（`RELEASE.md`），经云助手上传到 `/data/forcitis-one/releases/<ver>/`，`sha256sum -c SHA256SUMS`。
2. 迁移（迁移凭据只在这一步出现）：
   ```bash
   # /etc/forcitis-one/migrate.env（0600 root:root）：SPRING_FLYWAY_USER=one_dev_migrator 与 SPRING_FLYWAY_PASSWORD；
   # 以及 SPRING_DATASOURCE_USERNAME / _PASSWORD 同为 migrator，SPRING_DATASOURCE_URL 加 &currentSchema=platform_approval（让 ACT_* 建在
   # platform_approval），FLOWABLE_SCHEMA_UPDATE=true，SPRING_FLYWAY_DEFAULT_SCHEMA=one_migration（否则 currentSchema 会把 Flyway 历史表也带进 platform_approval）
   systemd-run --wait --pipe --unit=one-migrate --uid=forcitis --gid=forcitis \
     -p EnvironmentFile=/etc/forcitis-one/one.env -p EnvironmentFile=/etc/forcitis-one/migrate.env \
     -p Environment=WORKFORCE_SYNC_ENABLED=false -p Environment=WECOM_ENABLED=false -p Environment=APPROVAL_DEADLINE_ENABLED=false \
     -p Environment=EVENT_PUBLICATION_JOBS_ENABLED=false -p Environment=FLOWABLE_ASYNC_EXECUTOR=false \
     /usr/bin/java -Duser.timezone=UTC -jar /data/forcitis-one/releases/<ver>/forcitis-one.jar --spring.main.web-application-type=none
   ```
   应用会在迁移与引擎建表后保持运行（非 Web 模式没有退出逻辑时），看到 `Started` 日志后 `systemctl stop one-migrate`。随后跑 `sql/c3_dev_db_verify.sql` 的 V8。
3. `ln -sfn /data/forcitis-one/releases/<ver> /data/forcitis-one/current && systemctl start forcitis-one`；`curl -s 127.0.0.1:8081/actuator/health` 为 UP。
4. 冒烟（INFRA_RUNBOOK §7 第 5 步）与 `ecs_check.sh --public-check`。
5. 回滚：`current` 指回上一版本并重启；1a 不做破坏性迁移。
