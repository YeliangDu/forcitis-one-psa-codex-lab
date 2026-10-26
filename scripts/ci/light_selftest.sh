#!/usr/bin/env bash
# light.sh「按改动触发」一节的自测（GOV-1023-FV2 gap-2）：逐路径核对 light_triggers.sh 的六个模式与检查表，并在临时仓库里核对 list_changed 与字节码清理（#693）。
# 用法：scripts/ci/light_selftest.sh   light.sh 每次都跑它；通过时末行 light_selftest: ok，否则逐条说哪里不对并退出 1。
# 示例路径用 x/ 代替文档根目录前缀：写全了会被 relevant_change 当成读文档（模式本身不锚定前缀）。
set -uo pipefail
here=$(cd "$(dirname "$0")" && pwd)
# shellcheck source=scripts/ci/light_triggers.sh
. "$here/light_triggers.sh"

tmp=$(mktemp -d "${TMPDIR:-/tmp}/light-self.XXXXXX") || exit 2
trap 'rm -rf "$tmp"' EXIT
changed=$tmp/changed
bad=0
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
expect x/rules/decide/build_decision_page.py t_decision_gen
expect x/rules/decide/page-template.html t_decision_gen
expect x/architecture/decision-page/D-01.json t_decision_pages
expect x/rules/decide/sheets/q.json t_decision_pages
expect x/rules/decide/check_pages.py t_decision_gen t_decision_pages
expect x/rules/decide/answered_pages.txt t_decision_pages
expect x/architecture/decision-page/check_pages.py
expect x/architecture/decision-page/old/D-01.json
expect x/architecture/proposals/j_checks.py t_j_checks
expect x/architecture/proposals/vs_links_gen.py t_j_checks
expect backend/src/test/java/com/forcitis/one/a/FooTest.java t_shared_mock t_weak_registration
expect backend/src/main/java/com/forcitis/one/a/Foo.java t_weak_registration
expect scripts/ci/shared_mock_verify_scan.py t_shared_mock
expect scripts/ci/weak_registration_scan.py t_weak_registration
expect scripts/gates/install.sh t_shell
expect x/notes.txt
: >"$changed"
! touches "$t_weak_registration" || no "有基线而没有改动时不该触发"
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

# 仓库里的 shell（*.sh、工作流 run 段、.claude/settings.json 的挂载命令，后者由 /bin/sh 跑）：$VAR 后不得紧跟非 ASCII 字符。UTF-8 终端下 /bin/bash 3.2 会把全角标点读进变量名，set -u 即崩；写成 ${VAR}
var_cjk=$'\\$[A-Za-z_][A-Za-z0-9_]*[\x80-\xff]'
hits=$(cd "$here/../.." && git ls-files -z '*.sh' .github/workflows .claude/settings.json | LC_ALL=C xargs -0 grep -nE "$var_cjk")
[ -z "$hits" ] || no "\$VAR 后紧跟非 ASCII 字符，改成 \${VAR}：
$hits"

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
