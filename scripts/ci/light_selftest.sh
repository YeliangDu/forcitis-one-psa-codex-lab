#!/usr/bin/env bash
# light.sh「按改动触发」一节的自测（GOV-1023-FV2 gap-2）：逐路径核对 light_triggers.sh 的模式（#742 起含各脚本自测）与检查表，并在临时仓库里核对 list_changed 与字节码清理（#693）。
# 用法：scripts/ci/light_selftest.sh   light.sh 在 scripts/ci/light* 改了时跑它；通过时末行 light_selftest: ok，否则逐条说哪里不对并退出 1。
# 示例路径用 x/ 代替文档根目录前缀：写全了会被 relevant_change 当成读文档（模式本身不锚定前缀）。
set -uo pipefail
here=$(cd "$(dirname "$0")" && pwd)
# shellcheck source=scripts/ci/light_triggers.sh
. "$here/light_triggers.sh"

tmp=$(mktemp -d "${TMPDIR:-/tmp}/light-self.XXXXXX") || exit 2
trap 'rm -rf "$tmp"' EXIT
changed=$tmp/changed
bad=0
LIGHT_ALL=""  # light.sh 在 CI 推送时带着 LIGHT_ALL=1 调本自测；逐路径核对要在它关掉时做
no() { echo "不对：$*"; bad=1; }

expect() {  # expect <路径> <应触发的模式名…>：每个模式逐个核对，应触发的触发、其余不触发
  local path=$1 key want got
  shift
  printf '%s\n' "$path" >"$changed"
  for key in $TRIGGER_KEYS; do
    case " $* " in *" $key "*) want=1 ;; *) want=0 ;; esac
    if touches "${!key}"; then got=1; else got=0; fi
    [ "$want" = "$got" ] || no "$path 对 $key 应为 ${want}，实为 $got"
  done
}
base=x
expect scripts/gates/install.sh t_shell t_gates
expect tools/ops/Runner_Watch.SH t_shell t_runner_watch
expect backend/src/test/java/com/forcitis/one/a/FooTest.java t_unit_classes
expect x/notes.txt
expect x/install.sh.bak
expect x/scripts/guards/ci_git.py
expect scripts/guards/ci_git.py t_guards t_guards_config
expect backend/pom.xml t_guards_config
expect scripts/ci/evidence_imports.py t_evidence_imports
expect policy.json t_gates t_session t_mq t_evidence t_post_review
expect .claude/settings.json t_gates
expect .pre-commit-config.yaml t_gates
expect scripts/merge/session_map.json t_gates t_mq
expect .github/workflows/nightly-legacy-it.yml t_guards_config
expect scripts/session/new_intent.py t_session
expect tools/docs/new_adr.py t_new_adr
expect scripts/ci/relevant_change_selftest.py t_mq
expect scripts/merge/gapscan.py t_mq
expect scripts/ci/light_unit_classes.py t_unit_classes
expect scripts/ci/heavy_gate.py t_heavy_gate
expect scripts/gates/prod_targets.json t_gates t_evidence
expect scripts/codegen/scan_acceptance_tests.py t_scan
expect scripts/merge/post_review.py t_mq t_post_review
expect scripts/review/PROMPT.md t_post_review
expect .github/workflows/ci.yml t_guards_config t_ci_steps
expect .github/workflows/deploy.yml t_ci_steps
expect scripts/release/runner_watch_selftest.py t_runner_watch
expect scripts/ci/light.sh t_shell t_light
expect scripts/release/docker_ensure.sh t_shell t_docker
expect tools/ops/com.forcitis.docker-watch.plist t_docker
direct() {  # direct <路径> <模式名> <0|1>：light.sh 直接用、不进检查表的模式
  printf '%s\n' "$1" >"$changed"
  if touches "${!2}"; then got=1; else got=0; fi
  [ "$got" = "$3" ] || no "$1 对 $2 应为 ${3}，实为 $got"
}
direct backend/src/test/java/com/forcitis/one/acceptance/a/FooAT.java t_acceptance 1
direct backend/src/test/java/com/forcitis/one/a/FooTest.java t_acceptance 0
direct CLAUDE.md t_start_sheet 1
direct scripts/session/build_decisions.py t_start_sheet 1
direct x/CLAUDE.md t_start_sheet 0
direct "$onesql" t_onesql 1
direct x/OneSql.java t_onesql 0
direct "$onesql_evidence/q.sql" t_onesql 1
: >"$changed"
! touches "$t_shell" || no "有基线而没有改动时不该触发"
LIGHT_ALL=1
for key in $TRIGGER_KEYS; do
  touches "${!key}" || no "LIGHT_ALL=1 时 $key 应触发"
done
LIGHT_ALL=""
base=""
for key in $TRIGGER_KEYS; do
  touches "${!key}" || no "没有基线时 $key 应触发"
done

# 检查表：每个模式名都有定义、每个模式至少一行、命令的第一个词可执行或是函数
keys_used=""
while IFS='|' read -r key name cmd; do
  case " $TRIGGER_KEYS " in *" $key "*) ;; *) no "检查表「${name}」的模式名 $key 不在 TRIGGER_KEYS" ;; esac
  [ -n "$name" ] && [ -n "$cmd" ] || no "检查表有空的名字或命令：$key"
  type "${cmd%% *}" >/dev/null 2>&1 || no "检查表「${name}」的命令 ${cmd%% *} 找不到"
  keys_used="$keys_used $key "
done <<<"$TRIGGERED_CHECKS"
for key in $TRIGGER_KEYS; do
  case "$keys_used" in *" $key "*) ;; *) no "模式 $key 在检查表里没有检查" ;; esac
done


# surefire_guards：在假仓库里跑（假的类清单与 mvnw）；有一个类没出报告就失败，都出了才通过
fake=$tmp/fake && mkdir -p "$fake/scripts/ci" "$fake/backend/target/surefire-reports"
printf 'print("ATest,BTest")\n' >"$fake/scripts/ci/light_unit_classes.py"
printf '#!/bin/sh\nfor c in $FAKE_REPORTS; do : > "target/surefire-reports/TEST-x.$c.xml"; done\nexit ${FAKE_EXIT:-0}\n' >"$fake/backend/mvnw" && chmod +x "$fake/backend/mvnw"
(cd "$fake" && FAKE_REPORTS=ATest surefire_guards 2>/dev/null) && no "surefire_guards：BTest 没出报告时应失败"
: >"$fake/backend/target/surefire-reports/TEST-x.BTest.xml"
(cd "$fake" && FAKE_REPORTS=ATest surefire_guards 2>/dev/null) && no "surefire_guards：上次留下的 BTest 报告不能顶替这次"
(cd "$fake" && FAKE_REPORTS="ATest BTest" FAKE_EXIT=1 surefire_guards 2>/dev/null) && no "surefire_guards：Maven 失败时应失败"
(cd "$fake" && FAKE_REPORTS="ATest BTest" surefire_guards 2>/dev/null) || no "surefire_guards：两个类都出了报告时应通过"

# run、skip、report：并行起跑，按起跑顺序打印；失败计 FAIL 并附输出，SKIP 带原因
work=$tmp/par && mkdir -p "$work" && passed=0 failed=0 skipped=0 count=0 shown=0
rule "slow rule" "slow fix"
run slow bash -c 'sleep 1; echo one'
rule "vanished rule" "vanished fix"
run vanished bash -c 'sleep 1; kill -9 $PPID'
rule "broken rule" "broken fix"
run broken bash -c 'echo boom; exit 3'
rule "absent rule" "absent fix"
skip absent "no input"
run unruled true
report >"$tmp/report.out" 2>/dev/null
out=$(cat "$tmp/report.out")
[ "$passed $failed $skipped" = "1 3 1" ] || no "report 计数不对：$passed PASS $failed FAIL $skipped SKIP"
printf '%s\n' "$out" | grep -q '^FAIL  broken.*退出 3' && printf '%s\n' "$out" | grep -q '      | boom' || no "失败项要报 FAIL、退出码与输出：$out"
printf '%s\n' "$out" | grep -q '^      规则：broken rule' && printf '%s\n' "$out" | grep -q '^      改法：broken fix' || no "FAIL 要打印规则与改法：$out"
printf '%s\n' "$out" | grep -q '^FAIL  unruled' && printf '%s\n' "$out" | grep -q '规则：（缺）' || no "没登记规则的检查要记 FAIL：$out"
printf '%s\n' "$out" | grep -q '^FAIL  vanished' || no "没写出退出码的检查要记 FAIL，不能沿用上一项：$out"
[ "$(printf '%s\n' "$out" | grep -oE '^(PASS|FAIL|SKIP)  [a-z]+' | awk '{print $2}' | tr '\n' ' ')" = "slow vanished broken absent unruled " ] || no "结果要按起跑顺序打印：$out"

# maven_offline_ready：本机离线解析不过算前提不满足（light.sh 记 SKIP）；CI 里不查
printf '#!/bin/sh\n[ -n "$FAKE_DEPS_MISSING" ] && { echo "Cannot access central in offline mode and the artifact x has not been downloaded"; exit 1; }\n[ -n "$FAKE_COMPILE_ERROR" ] && { echo "COMPILATION ERROR"; exit 1; }\nexit 0\n' >"$fake/backend/mvnw"
(cd "$fake" && FAKE_DEPS_MISSING=1 CI="" maven_offline_ready) && no "maven_offline_ready：本机缺依赖时应返回非零"
(cd "$fake" && FAKE_DEPS_MISSING="" CI="" maven_offline_ready) || no "maven_offline_ready：依赖齐全时应返回 0"
(cd "$fake" && FAKE_COMPILE_ERROR=1 CI="" maven_offline_ready) || no "maven_offline_ready：编译错误不是缺依赖，要进检查判红"
(cd "$fake" && FAKE_DEPS_MISSING=1 CI=1 maven_offline_ready) || no "maven_offline_ready：CI 里不查，由 surefire_guards 判红"

# list_changed：在临时仓库里核对三种来源、特殊字符文件名、git 出错时返回非零
repo=$tmp/repo
git init -q "$repo" && cd "$repo" || exit 2
g() { git -c user.name=t -c user.email=t@t -c commit.gpgsign=false "$@"; }
echo a >a.txt && g add a.txt && g commit -qm a && base=$(git rev-parse HEAD)
echo b >'q"z.sh' && echo c >'tab	x.java' && g add -A && g commit -qm b
echo d >>a.txt
echo e >new.sh
list_changed "$changed" || no "list_changed 在正常仓库里失败"
want=$(printf '%s\n' a.txt new.sh 'q"z.sh' 'tab	x.java' | sort)
[ "$(cat "$changed")" = "$want" ] || no "list_changed 结果不对：$(tr '\n' ' ' <"$changed")"
grep -Eq "$t_shell" "$changed" && [ "$(grep -Ec "$t_shell" "$changed")" = 2 ] || no "带引号的 .sh 文件名没被认出"
base=nosuchref
! list_changed "$changed" 2>/dev/null || no "基线认不出时 list_changed 应返回非零"
base=$(g commit-tree "$(git hash-object -w -t tree /dev/null)" -m orphan) && [ -n "$base" ] || no "建不出无共同祖先的提交"
! list_changed "$changed" 2>/dev/null || no "基线与 HEAD 没有共同祖先时 list_changed 应返回非零"

# $VAR 后跟非 ASCII：已跟踪的 .sh 里有就失败，改成 ${VAR} 就通过
printf 'echo "$x\xef\xbc\x8c"\n' >bad.sh && g add bad.sh && ! var_before_non_ascii 2>/dev/null || no "var_before_non_ascii 没认出 \$x 后紧跟全角逗号"
printf 'echo "${x}\xef\xbc\x8c"\n' >bad.sh && g add bad.sh && var_before_non_ascii || no "var_before_non_ascii 把 \${x} 也判成了问题"

# 字节码清理：一个没有时不报错；跑前已有的不动；新增的（含中文路径）删掉；中途 exit 也删，退出码不变
printf '__pycache__/\n' >.gitignore && work=$tmp/w && mkdir -p "$work"
pyc >"$work/pyc_before" || no "pyc 在没有字节码时失败"
rm_new_pyc >/dev/null || no "一个字节码都没有时 rm_new_pyc 应返回 0"
mkdir -p old/__pycache__ && : >old/__pycache__/a.pyc && pyc >"$work/pyc_before"
mkdir -p 中文/__pycache__ x/__pycache__ && : >中文/__pycache__/b.pyc && : >x/__pycache__/c.pyc
rm_new_pyc >/dev/null || no "rm_new_pyc 失败"
[ -f old/__pycache__/a.pyc ] || no "跑前已有的 __pycache__ 被删了"
[ ! -e 中文/__pycache__ ] && [ ! -e x/__pycache__ ] || no "新增的 __pycache__ 没删干净（含中文路径）"
bash -c '. "$1"; work=$2; trap "rm_new_pyc >/dev/null 2>&1" EXIT; mkdir -p z/__pycache__; exit 3' _ "$here/light_triggers.sh" "$work"
rc=$?
[ $rc = 3 ] && [ ! -e z/__pycache__ ] || no "中途 exit 时应删掉新增字节码且退出码不变（实为 ${rc}）"

[ $bad = 0 ] && echo "light_selftest: ok"
exit $bad
