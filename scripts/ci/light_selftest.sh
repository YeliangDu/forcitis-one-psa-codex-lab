#!/usr/bin/env bash
# light.sh「按改动触发」一节的自测（GOV-1023-FV2 gap-2）：逐路径核对 light_triggers.sh 的六个模式与检查表，并在临时仓库里核对 list_changed。
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
    [ "$want" = "$got" ] || no "$path 对 $key 应为 $want，实为 $got"
  done
}
base=x
expect x/rules/decide/build_decision_page.py t_decision_gen
expect x/rules/decide/page-template.html t_decision_gen
expect x/architecture/decision-page/D-01.json t_decision_pages
expect x/rules/decide/sheets/q.json t_decision_pages
expect x/architecture/decision-page/check_pages.py t_decision_pages
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
  case " $TRIGGER_KEYS " in *" $key "*) ;; *) no "检查表「$name」的模式名 $key 不在 TRIGGER_KEYS" ;; esac
  [ -n "$name" ] && [ -n "$cmd" ] || no "检查表有空的名字或命令：$key"
  type "${cmd%% *}" >/dev/null 2>&1 || no "检查表「$name」的命令 ${cmd%% *} 找不到"
  keys_used="$keys_used $key "
done <<<"$TRIGGERED_CHECKS"
for key in $TRIGGER_KEYS; do
  case "$keys_used" in *" $key "*) ;; *) no "模式 $key 在检查表里没有检查" ;; esac
done

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

[ $bad = 0 ] && echo "light_selftest: ok"
exit $bad
