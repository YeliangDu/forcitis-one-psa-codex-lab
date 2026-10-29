#!/usr/bin/env bash
# FORCITIS one · C-6 · 在老系统应用服务器上以只读连接运行查询（通道 A）。经云助手以 root 执行，KeepCommand=false。
# 用法：legacy_probe_run.sh <工作目录> [--gzip-base64]   工作目录由调用方 mktemp 建好（0700），内含 LegacyReadOnlyProbe.java 与 q.sql；本脚本结束时删除整个目录。
# --gzip-base64：结果压缩后以一行 base64 输出（云助手单次输出约 24 KB 上限），由中转方解码。
# 连接取自老系统配置中心的主数据源；连接参数只写入工作目录（0600），不打印、不回传。
set -euo pipefail

work="$1"
[[ -d "$work" && -f "$work/LegacyReadOnlyProbe.java" && -f "$work/q.sql" ]] || { echo "work dir incomplete" >&2; exit 2; }
trap 'rm -rf "$work"' EXIT
chmod 700 "$work"; umask 077

app_dir=/usr/local/software/psa
app_jar="$app_dir/yudao-module-system-biz.jar"
# shellcheck disable=SC2012  # a glob over fixed JDK folder names without special characters
java_bin=$(ls -d /usr/lib/jvm/java-11-*/bin/java | head -1)
[[ -x "$java_bin" ]] || { echo "java 11 not found" >&2; exit 2; }

driver=$(unzip -l "$app_jar" | grep -oE 'BOOT-INF/lib/mssql-jdbc-[^ ]+\.jar' | head -1)
[[ -n "$driver" ]] || { echo "sql server driver not found in application jar" >&2; exit 2; }
unzip -p "$app_jar" "$driver" > "$work/driver.jar"

bootstrap=$(unzip -p "$app_jar" BOOT-INF/classes/bootstrap-prod.yaml)
config_block=$(printf '%s\n' "$bootstrap" | awk '/^[[:space:]]+config:[[:space:]]*$/{f=1;next} f&&/server-addr:|namespace:/{print} f&&/shared-configs:/{exit}')
addr=$(printf '%s\n' "$config_block" | sed -nE 's/^[[:space:]]*server-addr:[[:space:]]*([^ #]+).*/\1/p' | head -1)
namespace=$(printf '%s\n' "$config_block" | sed -nE 's/^[[:space:]]*namespace:[[:space:]]*([^ #]+).*/\1/p' | head -1)
# shellcheck disable=SC2016  # matches the literal ${ of an unresolved placeholder
[[ -n "$addr" && -n "$namespace" && "$addr" != *'${'* && "$namespace" != *'${'* ]] || { echo "config center address or namespace not resolvable" >&2; exit 2; }

curl -sf --max-time 10 -G "http://${addr}/nacos/v1/cs/configs" \
  --data-urlencode "dataId=psa-datasource.yaml" --data-urlencode "group=DEFAULT_GROUP" --data-urlencode "tenant=${namespace}" \
  -o "$work/datasource.yaml" || { echo "datasource config not readable" >&2; exit 2; }

if [[ "${2:-}" == --gzip-base64 ]]; then
  "$java_bin" -cp "$work/driver.jar" "$work/LegacyReadOnlyProbe.java" "$work/datasource.yaml" "$work/q.sql" > "$work/out.tsv"
  gzip -9 -c "$work/out.tsv" | base64 -w0; echo
else
  "$java_bin" -cp "$work/driver.jar" "$work/LegacyReadOnlyProbe.java" "$work/datasource.yaml" "$work/q.sql"
fi
