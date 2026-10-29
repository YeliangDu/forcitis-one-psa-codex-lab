#!/usr/bin/env bash
# FORCITIS one · C-4 · ECS 初始化 E-1..E-10（i-bp122xlpar8w2uylmal2，Alibaba Cloud Linux 4）。经云助手以 root 执行。
# 默认只打印将要执行的命令；加 --apply 才执行。执行前须用户确认（对外变更）。
#
# 用法：
#   ecs_init.sh --data-disk /dev/vdb --java-pkg <已确认的 Java 25 包名或 RPM 路径> [--only E-1,E-3] [--apply]
#   需要与本脚本同目录的 ../files/（forcitis-one.service、nginx-one.conf、forcitis-one.logrotate、one.env.template）
# E-11..E-17 是控制台或交接事项，脚本只打印指引（见 C-4-ecs-init.md）。
set -euo pipefail

APPLY=0
ONLY=""
DATA_DISK=""
JAVA_PKG=""
FILES="$(cd "$(dirname "$0")/../files" && pwd)"

while [[ $# -gt 0 ]]; do
  case "$1" in
    --apply) APPLY=1 ;;
    --only) ONLY="$2"; shift ;;
    --data-disk) DATA_DISK="$2"; shift ;;
    --java-pkg) JAVA_PKG="$2"; shift ;;
    *) echo "未知参数：$1" >&2; exit 2 ;;
  esac
  shift
done

[[ $EUID -eq 0 || $APPLY -eq 0 ]] || { echo "--apply 须以 root 执行" >&2; exit 2; }

run() {
  echo "+ $*"
  if [[ $APPLY -eq 1 ]]; then bash -c "$*"; fi
}
wanted() { [[ -z "$ONLY" || ",$ONLY," == *",$1,"* ]]; }
step() { echo; echo "===== $1 ====="; }
fail() { echo "停止：$*" >&2; exit 1; }

[[ $APPLY -eq 1 ]] && echo "模式：执行" || echo "模式：只打印（加 --apply 执行）"

if wanted E-1; then
  step "E-1 数据盘挂载到 /data（xfs，fstab 用 UUID + nofail）"
  [[ -n "$DATA_DISK" ]] || fail "E-1 需要 --data-disk（先用 lsblk 确认 200G 未挂载的盘）"
  [[ -b "$DATA_DISK" ]] || fail "$DATA_DISK 不是块设备"
  if [[ -n "$(blkid -o value -s TYPE "$DATA_DISK" 2>/dev/null || true)" ]]; then
    fail "$DATA_DISK 已有文件系统，拒绝格式化；人工确认后再处理"
  fi
  if lsblk -no MOUNTPOINT "$DATA_DISK" | grep -q .; then fail "$DATA_DISK 已挂载"; fi
  run "mkfs.xfs -L one-data $DATA_DISK"
  run "mkdir -p /data"
  run "echo \"UUID=\$(blkid -o value -s UUID $DATA_DISK) /data xfs defaults,nofail 0 2\" >> /etc/fstab"
  run "systemctl daemon-reload && mount /data"
fi

if wanted E-2; then
  step "E-2 系统更新、时区、chronyd"
  run "dnf -y update"
  run "timedatectl set-timezone Asia/Shanghai"
  run "systemctl enable --now chronyd"
fi

if wanted E-3; then
  step "E-3 运行用户 forcitis"
  run "id forcitis >/dev/null 2>&1 || useradd --system --home /data/forcitis-one --shell /sbin/nologin forcitis"
  # 迁移以独立用户运行（RELEASE.md §4）：主组 forcitis 只为读取发布目录
  run "id forcitis-migrate >/dev/null 2>&1 || useradd --system --no-create-home --home-dir / --gid forcitis --shell /sbin/nologin forcitis-migrate"
fi

if wanted E-4; then
  step "E-4 Java 25 运行时（与 CI 的 Temurin 25 一致）"
  [[ -n "$JAVA_PKG" ]] || fail "E-4 需要 --java-pkg（执行前确认点：来源与固定小版本）"
  run "dnf -y install $JAVA_PKG"
  run "dnf -y install 'dnf-command(versionlock)' && dnf versionlock add $JAVA_PKG"
fi

if wanted E-5; then
  step "E-5 目录与权限"
  # 发布目录与 current 软链归 root：安装器以 root 在其中操作，应用用户不能替换其中任何东西；应用只写 logs、tmp
  run "install -d -o root -g forcitis -m 750 /data/forcitis-one /data/forcitis-one/releases"
  run "install -d -o forcitis -g forcitis -m 750 /data/forcitis-one/logs /data/forcitis-one/tmp"
  # nginx 只需穿过这两层目录读取各版本的 web/（安装器给 web 授只读 ACL）
  run "dnf -y install acl && setfacl -m u:nginx:--x /data/forcitis-one /data/forcitis-one/releases"
  run "install -d -o root -g forcitis -m 750 /etc/forcitis-one"
  run "install -d -o root -g root -m 700 /etc/forcitis-one/migrate"
  run "test -e /etc/forcitis-one/one.env || install -o root -g forcitis -m 0640 $FILES/one.env.template /etc/forcitis-one/one.env"
fi

if wanted E-6; then
  step "E-6 systemd 单元与日志轮转（不 enable、不启动；首次部署放好 jar 后再 enable）"
  run "install -o root -g root -m 0644 $FILES/forcitis-one.service /etc/systemd/system/forcitis-one.service"
  run "install -o root -g root -m 0644 $FILES/forcitis-one.logrotate /etc/logrotate.d/forcitis-one"
  # 发布包到位前 enable，重启后会因 jar 不存在按 Restart=on-failure 无限重启
  run "systemctl daemon-reload"
fi

if wanted E-7; then
  step "E-7 应用只监听本机：由 one.env 的 SERVER_ADDRESS / MANAGEMENT_SERVER_* 控制，此处只核对"
  run "grep -E '^(SERVER_ADDRESS|SERVER_PORT|MANAGEMENT_SERVER_ADDRESS|MANAGEMENT_SERVER_PORT|MANAGEMENT_ENDPOINTS_WEB_EXPOSURE_INCLUDE)=' /etc/forcitis-one/one.env"
fi

if wanted E-8; then
  step "E-8 nginx（发行版包）"
  run "dnf -y install nginx"
  run "install -o root -g root -m 0644 $FILES/nginx-one.conf /etc/nginx/conf.d/one.conf"
  run "install -d -o root -g root -m 700 /etc/nginx/tls"
  echo "（证书就位前 nginx -t 会失败，属预期；E-9 完成后再 enable --now nginx）"
fi

if wanted E-9; then
  step "E-9 证书（B-4 交接包；私钥 600）"
  run "test -s /etc/nginx/tls/one.forcitis.com.pem && test -s /etc/nginx/tls/one.forcitis.com.key"
  run "chmod 600 /etc/nginx/tls/one.forcitis.com.key /etc/nginx/tls/one.forcitis.com.pem"
  run "nginx -t && systemctl enable --now nginx"
fi

if wanted E-10; then
  step "E-10 静态资源：随发布切换的 current/web，与不随发布切换的 static-pinned（企业微信域名校验文件、ACME 校验）"
  run "install -d -o root -g root -m 755 /data/forcitis-one/static-pinned /data/forcitis-one/static-pinned/.well-known/acme-challenge"
  # 早先手工部署时 current 是实目录、校验文件放在 current/web：只复制到固定目录，不删原处；首次流水线部署前由运维把该目录改名移开
  if [[ -d /data/forcitis-one/current && ! -L /data/forcitis-one/current ]]; then
    run "find /data/forcitis-one/current/web -maxdepth 1 -name 'WW_verify_*.txt' -exec cp -p -n {} /data/forcitis-one/static-pinned/ \\;"
    run "chown root:root /data/forcitis-one/static-pinned/WW_verify_*.txt && chmod 0644 /data/forcitis-one/static-pinned/WW_verify_*.txt"
    echo "（/data/forcitis-one/current 是实目录：校验文件已复制到 static-pinned；nginx 换用新模板并验证后，执行 mv /data/forcitis-one/current /data/forcitis-one/current.manual-\$(date +%F)，再做首次流水线部署）"
  fi
fi

step "E-11..E-17 控制台 / 交接事项"
cat <<'TXT'
E-11 SLS Logtail：在 SLS 控制台为本 ECS 生成安装命令后执行；采集 /data/forcitis-one/logs/*.json 与 /var/log/nginx/one.*。
E-12 ARMS Prometheus：抓取 127.0.0.1:8081/actuator/prometheus（先确认 one.env 已暴露 prometheus）。
E-13 告警：见 docs/ops/runbooks/alerts.md。
E-14 安全组出方向：见 docs/ops/runbooks/ecs-init.md 表格，在 ECS 控制台配置。
E-15 企业微信：见 docs/ops/handoffs/b-2-wecom-app.md。
E-16 致远：见 docs/ops/handoffs/b-3-a8-sso-return.md。
E-17 备份：RDS 见 C-2；ECS 数据盘快照策略在控制台设置（每日、保留 7 天）。
TXT
