#!/bin/bash
# FORCITIS one · RELEASE.md §7 R-0：重建专用部署 runner（mac-one-deploy）。以 sudo 运行，口令由操作者在终端输入。
# 用法：sudo tools/ops/runner_rebuild.sh <runner 发行包 .tar.gz> <GitHub 公布的 SHA256> <注册令牌文件>
#   注册令牌文件由操作者（非 root）用 gh api -X POST repos/FORCITIS/forcitis-one-psa/actions/runners/registration-token --jq .token 写出（0600），本脚本读后即删。
set -euo pipefail
[ "$(id -u)" -eq 0 ] || { echo "请用 sudo 运行"; exit 1; }
TAR="${1:?runner 发行包}"
TAR_SHA="${2:?发行包 SHA256}"
TOKEN_FILE="${3:?注册令牌文件}"
HOOK_SRC="$(cd "$(dirname "$0")/../../scripts/release" && pwd)/runner_job_started.sh"
U=forcitis-deploy
DIR=/opt/forcitis-one-runner
RHOME=/opt/forcitis-one-runner-home
HOOK=/usr/local/libexec/forcitis-one/runner-job-started.sh
PLIST=/Library/LaunchDaemons/actions.runner.forcitis-one-deploy.plist
LABEL=actions.runner.forcitis-one-deploy
step() { echo; echo "==== $*"; }

step "0 核对发行包、令牌与作业钩子"
[ -f "$HOOK_SRC" ] || { echo "缺少作业钩子 ${HOOK_SRC}（在任何删除之前停下）"; exit 1; }
echo "$TAR_SHA  $TAR" | shasum -a 256 -c -
[ -s "$TOKEN_FILE" ] || { echo "缺少注册令牌文件"; exit 1; }
if dseditgroup -o checkmember -m $U admin >/dev/null 2>&1; then echo "$U 在 admin 组，停止"; exit 1; fi

step "1 停旧服务、删旧 plist、清空 $U 主目录"
launchctl bootout system/$LABEL 2>/dev/null || true
rm -f "$PLIST"
find /Users/$U -mindepth 1 -delete 2>/dev/null || echo "（主目录中有系统保护的条目未删除，忽略）"
for f in Library/Python .gitconfig .config/git .aliyun .curlrc; do
  [ ! -e "/Users/$U/$f" ] || { echo "主目录仍有禁止的配置：$f"; exit 1; }
done
chown $U:staff /Users/$U; chmod 700 /Users/$U

step "2 组 $U 与 runner 目录"
dscl . -read /Groups/$U >/dev/null 2>&1 || dseditgroup -o create -r "FORCITIS one deploy runner" $U
dseditgroup -o edit -a $U -t user $U
rm -rf "$DIR"; install -d -o root -g wheel -m 755 "$DIR"

step "3 解压并以 $U 注册（--disableupdate）"
tar -xzf "$TAR" -C "$DIR"
chown -R $U:$U "$DIR"
TOKEN="$(cat "$TOKEN_FILE")"; rm -f "$TOKEN_FILE"
(cd "$DIR" && sudo -u $U HOME="$DIR" ./config.sh --url https://github.com/FORCITIS/forcitis-one-psa --token "$TOKEN" \
   --name mac-one-deploy --labels forcitis-one-deploy --work _work --disableupdate --unattended --replace)
unset TOKEN

step "4 收回所有权"
install -d "$DIR/_work" "$DIR/_diag"
chown -R root:wheel "$DIR"; chmod -R u+rwX,go+rX,go-w "$DIR"
for f in .runner .credentials .credentials_rsaparams; do [ -e "$DIR/$f" ] && { chown root:$U "$DIR/$f"; chmod 0640 "$DIR/$f"; }; done
printf '/usr/bin:/bin:/usr/sbin:/sbin\n' > "$DIR/.path"; touch "$DIR/.env"
chown root:wheel "$DIR/.path" "$DIR/.env"; chmod 0644 "$DIR/.path" "$DIR/.env"
chown -R $U:$U "$DIR/_work" "$DIR/_diag"; chmod 750 "$DIR/_work" "$DIR/_diag"

# run.sh copies run-helper.sh from its template into the runner directory on every start; that directory is root-owned and read-only
# to the runner user, so root writes run-helper.sh once and launchd starts it directly
install -o root -g wheel -m 0755 "$DIR/run-helper.sh.template" "$DIR/run-helper.sh"

step "5 hook"
install -d -o root -g wheel -m 755 /usr/local/libexec/forcitis-one
install -o root -g wheel -m 0755 "$HOOK_SRC" "$HOOK"

step "6 LaunchDaemon"
rm -rf "$RHOME"; install -d -o root -g wheel -m 555 "$RHOME"
cat > "$PLIST" <<P
<?xml version="1.0" encoding="UTF-8"?>
<!DOCTYPE plist PUBLIC "-//Apple//DTD PLIST 1.0//EN" "http://www.apple.com/DTDs/PropertyList-1.0.dtd">
<plist version="1.0"><dict>
  <key>Label</key><string>$LABEL</string>
  <key>ProgramArguments</key><array><string>$DIR/run-helper.sh</string></array>
  <key>UserName</key><string>$U</string>
  <key>GroupName</key><string>$U</string>
  <key>WorkingDirectory</key><string>$DIR</string>
  <key>RunAtLoad</key><true/>
  <key>KeepAlive</key><true/>
  <key>ProcessType</key><string>Interactive</string>
  <key>StandardOutPath</key><string>$DIR/_diag/launchd.out.log</string>
  <key>StandardErrorPath</key><string>$DIR/_diag/launchd.err.log</string>
  <key>EnvironmentVariables</key><dict>
    <key>ACTIONS_RUNNER_HOOK_JOB_STARTED</key><string>$HOOK</string>
    <key>PATH</key><string>/usr/bin:/bin:/usr/sbin:/sbin</string>
    <key>PYTHONNOUSERSITE</key><string>1</string>
    <key>GIT_CONFIG_GLOBAL</key><string>/dev/null</string>
    <key>HOME</key><string>$RHOME</string>
  </dict>
</dict></plist>
P
chown root:wheel "$PLIST"; chmod 0644 "$PLIST"; plutil -lint "$PLIST"
launchctl bootstrap system "$PLIST"
sleep 5; launchctl print system/$LABEL | grep -E 'state =|pid =' | head -2
echo; echo "R-0 完成：gh api repos/FORCITIS/forcitis-one-psa/actions/runners 应显示 mac-one-deploy online。"
