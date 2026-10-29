#!/usr/bin/env bash
# FORCITIS one · C-4 · ECS 检查 E-1..E-17（只读，不改任何东西）。经云助手以 root 执行，输出留档。
# 用法：ecs_check.sh [--public-check]   --public-check 额外从本机访问 https://one.forcitis.com（需 DNS 与证书就绪）
# 机器上做不到的项（控制台、外网扫描）标为 MANUAL，按 C-4-ecs-init.md 人工核对。
set -uo pipefail

PUBLIC=0
[[ "${1:-}" == "--public-check" ]] && PUBLIC=1
pass=0; failed=0
ok()     { echo "PASS   $1"; pass=$((pass + 1)); }
bad()    { echo "FAIL   $1"; failed=$((failed + 1)); }
manual() { echo "MANUAL $1"; }
check()  { local name="$1"; shift; if eval "$*" >/dev/null 2>&1; then ok "$name"; else bad "$name"; fi; }

release_source_ok() {
  local source=/etc/forcitis-one/release-source profile
  [[ $(stat -c '%U %a' "$source") == 'root 644' ]] || return 1
  profile=$(grep -E '^SPRING_PROFILES_ACTIVE=' /etc/forcitis-one/one.env | tail -1 | cut -d= -f2 | tr -d "'\"")
  grep -qx "ENVIRONMENT=${profile}" "$source"
}

check "E-1 /data 已挂载且为 xfs"            "findmnt -n -o FSTYPE /data | grep -qx xfs"
check "E-1 fstab 以 UUID 挂载 /data 且含 nofail" "grep -E '^UUID=[^ ]+ +/data +xfs +[^ ]*nofail' /etc/fstab"
check "E-2 时区 Asia/Shanghai"              "timedatectl show -p Timezone --value | grep -qx Asia/Shanghai"
check "E-2 chronyd 运行且已同步"            "systemctl is-active chronyd && chronyc tracking | grep -q 'Leap status *: Normal'"
check "E-3 用户 forcitis 存在且不可登录"    "getent passwd forcitis | grep -qE ':(/usr)?/sbin/nologin$|:/bin/false$'"
check "E-4 Java 主版本 25"                  "java -version 2>&1 | grep -q 'version \"25'"
check "E-3 迁移用户 forcitis-migrate 存在且不可登录" "getent passwd forcitis-migrate | grep -qE ':(/usr)?/sbin/nologin$|:/bin/false$'"
check "E-5 /data/forcitis-one 属 root:forcitis、750" "[[ \$(stat -c '%U:%G %a' /data/forcitis-one) == 'root:forcitis 750' ]]"
check "E-5 releases 属 root:forcitis、750"  "[[ \$(stat -c '%U:%G %a' /data/forcitis-one/releases) == 'root:forcitis 750' ]]"
check "E-5 logs、tmp 属 forcitis、750"      "[[ \$(stat -c '%U %a' /data/forcitis-one/logs) == 'forcitis 750' && \$(stat -c '%U %a' /data/forcitis-one/tmp) == 'forcitis 750' ]]"
check "E-5 nginx 可穿过发布目录"            "getfacl -p /data/forcitis-one /data/forcitis-one/releases 2>/dev/null | grep -c '^user:nginx:--x' | grep -qx 2"
check "E-5 发布签名公钥 root 0644 且非空" "[[ -s /etc/forcitis-one/release-signers && \$(stat -c '%U %a' /etc/forcitis-one/release-signers) == 'root 644' ]]"
check "E-5 发布来源配置 root 0644，环境与 one.env 一致" release_source_ok
check "E-5 安装器 root 0755"                "[[ \$(stat -c '%U %a' /usr/local/sbin/forcitis-one-release) == 'root 755' ]]"
check "R-7 发布拉取定时器启用"             "systemctl is-enabled --quiet forcitis-one-release-pull.timer && systemctl is-active --quiet forcitis-one-release-pull.timer"
check "E-5 迁移凭据目录 root 700"           "[[ \$(stat -c '%U %a' /etc/forcitis-one/migrate) == 'root 700' ]]"
check "E-5 /etc/forcitis-one 属 root:forcitis、750" "[[ \$(stat -c '%U:%G %a' /etc/forcitis-one) == 'root:forcitis 750' ]]"
check "E-5 one.env 为 0640 root:forcitis"   "[[ \$(stat -c '%U:%G %a' /etc/forcitis-one/one.env) == 'root:forcitis 640' ]]"
check "E-5 one.env 无迁移凭据"              "! grep -qE '^SPRING_FLYWAY_(USER|PASSWORD)=' /etc/forcitis-one/one.env"
check "E-5 常驻进程不跑 Flyway"             "grep -qx 'SPRING_FLYWAY_ENABLED=false' /etc/forcitis-one/one.env"
check "E-6 forcitis-one.service 已安装"     "systemctl cat forcitis-one"
if [[ -e /data/forcitis-one/current/forcitis-one.jar ]]; then
  check "E-6 forcitis-one.service 已启用"   "systemctl is-enabled forcitis-one"
else
  manual "E-6 首次部署前不 enable（发布包未到位）"
fi
check "E-6 单元以 UTC 启动 JVM（-Duser.timezone=UTC）" "systemctl cat forcitis-one | grep -Eq '^ExecStart=/usr/bin/java .*-Duser\\.timezone=UTC( |\$)'"
if systemctl is-active -q forcitis-one; then
  check "E-6 运行中的进程带 -Duser.timezone=UTC" "tr '\\0' ' ' < /proc/\$(systemctl show -p MainPID --value forcitis-one)/cmdline | grep -q -- '-Duser.timezone=UTC '"
fi
check "E-6 单元以 forcitis 运行且加固"      "systemctl show forcitis-one -p User -p NoNewPrivileges -p ProtectSystem | tr '\n' ' ' | grep -q 'User=forcitis.*NoNewPrivileges=yes.*ProtectSystem=strict'"
check "E-7 8080 只监听 127.0.0.1"           "ss -ltnH 'sport = :8080' | awk '{print \$4}' | grep -qx '127.0.0.1:8080' && ! ss -ltnH 'sport = :8080' | awk '{print \$4}' | grep -vqx '127.0.0.1:8080'"
check "E-7 8081 只监听 127.0.0.1"           "ss -ltnH 'sport = :8081' | awk '{print \$4}' | grep -qx '127.0.0.1:8081'"
check "E-7 管理端点只开放 health、prometheus" "curl -fsS http://127.0.0.1:8081/actuator | python3 -c 'import json,sys; l=set(json.load(sys.stdin)[\"_links\"]); l.discard(\"self\"); sys.exit(0 if {x.split(\"-\")[0] for x in l} <= {\"health\",\"prometheus\"} else 1)'"
public_ports_only_web() {
  local extra
  extra=$(ss -ltnH | awk '{print $4}' | grep -vE '^(127\.0\.0\.1|\[::1\]):' | sed -E 's/.*:([0-9]+)$/\1/' | sort -u | grep -vxE '80|443')
  [[ -z "$extra" ]] || { echo "非本机监听的其他端口：$extra" >&2; return 1; }
}
access_log_without_query() {
  local conf=/etc/nginx/conf.d/one.conf
  grep -q 'log_format one_json' "$conf" || return 1
  # shellcheck disable=SC2016  # the awk program and the regex match a literal $request
  ! awk '/log_format one_json/{p=1} p{print} p&&/;[[:space:]]*$/{exit}' "$conf" | grep -qE '\$(request|args|query_string|request_uri|arg_[a-z_]+)\b'
}
check "E-7 非本机地址只监听 80、443"        public_ports_only_web
check "E-8 nginx 配置有效"                  "nginx -t"
check "E-8 访问日志不记查询串"              access_log_without_query
check "E-8 不转发 /actuator、/v3/api-docs"  "grep -q 'location ^~ /actuator' /etc/nginx/conf.d/one.conf && grep -q 'location ^~ /v3/api-docs' /etc/nginx/conf.d/one.conf"
check "E-9 私钥权限 600（软链时看目标）"    "[[ \$(stat -L -c '%a' /etc/nginx/tls/one.forcitis.com.key) == 600 ]]"
check "E-9 certbot 续期定时器启用（若装了 certbot）" "! systemctl list-unit-files certbot-renew.timer >/dev/null 2>&1 || systemctl is-enabled certbot-renew.timer"
check "E-9 certbot webroot 指向 static-pinned（若用 webroot）" "! grep -qs '^webroot_path' /etc/letsencrypt/renewal/one.forcitis.com.conf || grep -qsE '^webroot_path *= */data/forcitis-one/static-pinned' /etc/letsencrypt/renewal/one.forcitis.com.conf || grep -qsE '/data/forcitis-one/static-pinned' /etc/letsencrypt/renewal/one.forcitis.com.conf"
check "E-9 证书 30 天内不过期"              "openssl x509 -checkend 2592000 -noout -in /etc/nginx/tls/one.forcitis.com.pem"
check "E-10 PC 静态资源就位"                "test -s /data/forcitis-one/current/web/index.html"
check "E-10 current 是发布软链（非手工实目录）" "test -L /data/forcitis-one/current"
check "E-10 static-pinned 属 root、755"     "[[ \$(stat -c '%U %a' /data/forcitis-one/static-pinned) == 'root 755' ]]"
check "E-10 企业微信校验文件在 static-pinned 且经 nginx 返回原文" "for f in /data/forcitis-one/static-pinned/WW_verify_*.txt; do test -s \"\$f\" && curl -fsS --resolve one.forcitis.com:443:127.0.0.1 \"https://one.forcitis.com/\$(basename \"\$f\")\" | cmp -s - \"\$f\" || exit 1; done"
check "E-11 Logtail 运行"                   "systemctl is-active ilogtaild || /etc/init.d/ilogtaild status"
check "E-12 prometheus 端点有业务指标"      "curl -fsS http://127.0.0.1:8081/actuator/prometheus | grep -q '^forcitis_'"
manual "E-13 四类告警已配置并演练（C-7 演练记录）"
manual "E-14 安全组出方向只放行 RDS 5432、qyapi.weixin.qq.com:443、致远验票地址、A8 与老系统 1433"
check "E-14 RDS 可达"                       "timeout 5 bash -c '</dev/tcp/\$(grep -oP \"(?<=postgresql://)[^:/]+\" /etc/forcitis-one/one.env)/5432'"
check "E-15 企业微信 API 可达"              "curl -fsS -o /dev/null https://qyapi.weixin.qq.com/cgi-bin/gettoken"
manual "E-15 企业微信 gettoken 用新应用凭据返回 errcode=0（B-9 第 1 步）"
manual "E-16 致远入口登录冒烟（B-3 验收）"
manual "E-17 RDS 备份保留 ≥ 7 天、ECS 数据盘每日快照、SLS 保留 30 天（控制台）"
if [[ $PUBLIC -eq 1 ]]; then
  check "公网 80 → 301 https"               "curl -sI http://one.forcitis.com/ | grep -qE '^HTTP/[0-9.]+ 301'"
  check "公网 HSTS"                          "curl -sI https://one.forcitis.com/ | grep -qi '^strict-transport-security'"
  check "公网 /actuator 不可达"              "[[ \$(curl -s -o /dev/null -w '%{http_code}' https://one.forcitis.com/actuator/health) == 404 ]]"
  check "公网匿名 API 返回 401"              "[[ \$(curl -s -o /dev/null -w '%{http_code}' https://one.forcitis.com/api/v1/platform/sessions:current) == 401 ]]"
fi

echo
echo "通过 $pass 项，失败 $failed 项；MANUAL 项人工核对"
[[ $failed -eq 0 ]]
