# shellcheck shell=bash disable=SC2034,SC2154  # 被 source 的库：变量在读入方用，base 与 changed 由读入方设
# light.sh 的辅助：按改动触发的检查（GOV-1023-FV2 gap-2；#742 起含各脚本自测）与字节码清理（#693）；由 light.sh 与 light_selftest.sh 用 source 读入，不单独运行。
# 路径模式照搬原 precheck.sh 的 touches 模式（ERE，对仓库相对路径）；改动＝相对基线的提交、未提交与未跟踪文件；没有基线（$base 为空）时全算改到。
# 读入方须先设 base（基线，可为空）与 changed（改动列表文件，一行一个路径）；用 run、skip、report 还须设 work（临时目录）与 passed=0 failed=0 skipped=0 count=0 shown=0。Bash 3.2 可用。

t_shell='\.sh$'
# 自测只在被测脚本或它读的仓库文件改了才跑（#742）：自测的结果只随这些文件变；推到 main（LIGHT_ALL=1）与没有基线时全跑，兜住环境变化
t_guards='^scripts/guards/'
t_guards_config='^scripts/guards/|^backend/pom\.xml$|^backend/src/test/java/com/forcitis/one/acceptance/|^scripts/ci/(legacy_it|backend_verify)\.sh$|^scripts/codegen/generate_api\.sh$|^docs/rules/rulings/|^\.github/workflows/(ci|nightly-legacy-it)\.yml$'
t_evidence_imports='^scripts/ci/evidence_imports\.py$'
t_gates='^scripts/(gates|hooks)/|^policy\.json$|^\.claude/|^\.pre-commit-config\.yaml$|^scripts/merge/session_map\.json$'
t_session='^scripts/session/|^policy\.json$|^docs/rules/roles/|^docs/decisions/decision-digest/'
t_new_adr='^tools/docs/|^docs/adr/'
t_mq='^scripts/merge/|^scripts/ci/relevant_change|^policy\.json$'
t_unit_classes='^scripts/ci/light_unit_classes\.py$|^backend/src/test/java/'
t_heavy_gate='^scripts/ci/heavy_gate\.py$'
t_backend_verify='^scripts/ci/backend_verify'
t_evidence='^tools/evidence/|^scripts/gates/prod_targets\.json$|^policy\.json$'
t_scan='^scripts/codegen/scan_'
t_post_review='^scripts/merge/(post_review|issue_gate)|^scripts/review/|^policy\.json$'
t_ci_steps='^scripts/ci/ci_steps_selftest\.py$|^\.github/workflows/(ci|deploy)\.yml$'
t_runner_watch='^scripts/release/runner_watch|^tools/ops/(runner_watch|com\.forcitis\.runner)'
t_start_sheet="$t_gates|$t_session|^CLAUDE\.md$|^docs/rules/(rules\.md$|playbooks/)"  # 工具库开工单（仓库外）读的文件：light.sh 直接用，不进检查表
t_acceptance='^backend/src/test/java/com/forcitis/one/acceptance/'  # 验收测试改了：跑全部免容器单测（含各守护类），light.sh 直接用
t_docker='^scripts/release/docker_ensure|^tools/ops/(docker_watch|com\.forcitis\.docker)'
onesql=tools/ops/dba/OneSql.java
onesql_evidence=docs/data/evidence/dba-handoff
t_onesql='^tools/ops/dba/OneSql\.java$|^docs/data/evidence/dba-handoff/.*\.sql$'  # light.sh 直接用：要 java，缺了记 SKIP
t_light='^scripts/ci/light(_selftest|_triggers)?\.sh$'
TRIGGER_KEYS='t_shell t_guards t_guards_config t_evidence_imports t_gates t_session t_new_adr t_mq t_unit_classes t_heavy_gate t_backend_verify t_evidence t_scan t_post_review t_ci_steps t_runner_watch t_docker t_light'

# 检查表：模式名|名字|命令（命令按空格切分，路径里不能有空格）
TRIGGERED_CHECKS='t_shell|shell syntax (bash -n)|shell_syntax
t_guards|migrations_immutable (self-test)|python3 scripts/guards/migrations_immutable.py --self-test
t_guards|system_actors_append_only (self-test)|python3 scripts/guards/system_actors_append_only.py --self-test
t_guards|acceptance_guard (self-test)|python3 scripts/guards/acceptance_guard.py --self-test
t_guards_config|acceptance_reports (self-test)|python3 scripts/guards/acceptance_reports.py --self-test
t_guards_config|acceptance_rulings (self-test)|python3 scripts/guards/acceptance_rulings.py --self-test
t_guards|db_scripts_aligned (self-test)|python3 -I scripts/guards/db_scripts_aligned.py --self-test
t_guards|proposal_reads (self-test)|python3 scripts/guards/proposal_reads.py --self-test
t_guards|build_catalog (self-test)|python3 scripts/guards/build_catalog.py --self-test
t_guards|term_rules (self-test)|python3 scripts/guards/term_rules.py --self-test
t_guards|check_names (self-test)|python3 -I scripts/guards/check_names.py --self-test
t_guards|check_playbooks (self-test)|python3 -I scripts/guards/check_playbooks.py --self-test
t_guards|policy_keys_read (self-test)|python3 -I scripts/guards/policy_keys_read.py --self-test
t_evidence_imports|evidence tools: imports resolve (self-test)|python3 -I scripts/ci/evidence_imports.py --self-test
t_gates|gates (gates_selftest.py)|python3 scripts/gates/gates_selftest.py
t_session|build_decisions (self-test)|python3 scripts/session/build_decisions.py --self-test
t_session|new_intent_selftest (self-test)|python3 scripts/session/new_intent_selftest.py --self-test
t_new_adr|new_adr (self-test)|python3 tools/docs/new_adr.py --self-test
t_mq|mq (self-test)|python3 scripts/merge/mq.py --self-test
t_unit_classes|light_unit_classes (self-test)|python3 scripts/ci/light_unit_classes.py --self-test
t_heavy_gate|heavy_gate (self-test)|python3 scripts/ci/heavy_gate.py --self-test
t_backend_verify|backend_verify_selftest (OpenAPI export skipped only for this commit)|bash scripts/ci/backend_verify_selftest.sh
t_evidence|evidence_selftest (self-test)|python3 tools/evidence/evidence_selftest.py --self-test
t_scan|scan_aggregates tests|python3 scripts/codegen/scan_aggregates_selftest.py
t_post_review|post_review_selftest|python3 scripts/merge/post_review_selftest.py
t_ci_steps|ci_steps_selftest (ci.yml cleanup spares the runner)|python3 scripts/ci/ci_steps_selftest.py
t_runner_watch|runner_watch_selftest (runner keep-alive and watch)|python3 scripts/release/runner_watch_selftest.py
t_docker|docker_ensure_selftest (about 85 s)|bash scripts/release/docker_ensure_selftest.sh
t_light|light_selftest (by-change triggers)|bash scripts/ci/light_selftest.sh'

touches() {  # touches <模式>：没有基线或 LIGHT_ALL=1（CI 推到 main）时一律算改到；否则看 $changed 里有没有匹配的路径（大小写不敏感：macOS 上大小写改名的路径也触发）
  [ -z "$base" ] || [ "${LIGHT_ALL:-}" = 1 ] || grep -Eqi "$1" "$changed"
}

list_changed() {  # list_changed <输出文件>：写出相对 $base 的改动路径；任一条 git 命令失败即返回非零（不当成「没有改动」）
  # 用 -z：含引号、反斜杠、制表符的文件名，git 不加引号原样给出（core.quotepath=off 只管非 ASCII）
  {
    git diff -z --name-only --no-renames "$base...HEAD" &&
      git diff -z --name-only --no-renames HEAD &&
      git ls-files -z --others --exclude-standard
  } >"$1.z" || return 1
  tr '\0' '\n' <"$1.z" | sed '/^$/d' | sort -u >"$1"
}

shell_syntax() {  # 改到的 .sh 逐个 bash -n（没有基线时全部已跟踪的 .sh）；已删除的跳过
  local file
  while IFS= read -r file; do
    [ ! -f "$file" ] || bash -n "$file" || return 1
  done < <(if [ -n "$base" ]; then grep -E "$t_shell" "$changed"; else git ls-files '*.sh'; fi)
}

maven_offline_ready() {  # 本机 ~/.m2 够离线编译后端测试：只有 Maven 报「拿不到依赖」才算没准备好（编译错误照常进检查）；CI 里不查
  local out
  [ -z "${CI:-}" ] || return 0
  out=$(cd backend && ./mvnw -q -o test-compile 2>&1) && return 0
  ! printf '%s\n' "$out" | grep -qiE 'offline mode|has not been downloaded|could not be resolved|Could not resolve dependencies|could not transfer'
}

surefire_guards() {  # 全部免容器单测（light_unit_classes.py 列出，含守护类）；每个类都要出报告，没出报告即没跑，算失败
  local classes class
  classes=$(python3 scripts/ci/light_unit_classes.py) && [ -n "$classes" ] || return 1
  for class in ${classes//,/ }; do rm -f backend/target/surefire-reports/TEST-*."$class".xml; done  # 本机 target 里的旧报告不算数
  (cd backend && ./mvnw -q -o test-compile surefire:test -Dtest="$classes") || return 1  # -o：轻检查不联网
  for class in ${classes//,/ }; do
    ls backend/target/surefire-reports/TEST-*."$class".xml >/dev/null 2>&1 || { echo "$class 没出报告：没跑" >&2; return 1; }
  done
}

var_before_non_ascii() {  # 仓库里的 shell（*.sh、工作流 run 段、.claude/settings.json 的挂载命令）：$VAR 后不得紧跟非 ASCII 字符。UTF-8 下 bash 3.2 会把全角标点读进变量名，set -u 即崩；写成 ${VAR}
  local hits
  hits=$(git ls-files -z '*.sh' .github/workflows .claude/settings.json | LC_ALL=C xargs -0 grep -nE $'\\$[A-Za-z_][A-Za-z0-9_]*[\x80-\xff]')
  [ -z "$hits" ] || { printf '$VAR 后紧跟非 ASCII 字符，改成 ${VAR}：\n%s\n' "$hits" >&2; return 1; }
}

rule() {  # rule <防的是什么> <怎么修>：登记下一项 run 的规则与改法，FAIL 时随输出打印（输出给出位置）
  pending_rule=$1 pending_fix=$2
}

run() {  # run <名字> <命令…>：后台起跑，各项互不依赖，并行；结果由 report 按起跑顺序打印（PASS 或 FAIL 加用时，失败时附规则、改法与最后 30 行）
  local name=$1 i=$count
  shift
  printf '%s' "$name" >"$work/$i.name"
  if [ -z "${pending_rule:-}" ]; then  # 没登记规则的检查不算数：直接记 FAIL
    printf '规则：（缺）\n改法：在 scripts/ci/light.sh 里这项 run 之前加 rule <防的是什么> <怎么修>\n' >"$work/$i.rule"
    printf '1 0\n' >"$work/$i.rc"
    : >"$work/$i.log"
    count=$((count + 1))
    return
  fi
  printf '规则：%s\n改法：%s\n' "$pending_rule" "$pending_fix" >"$work/$i.rule"
  pending_rule="" pending_fix=""
  (started=$SECONDS; "$@" >"$work/$i.log" 2>&1; echo "$? $((SECONDS - started))" >"$work/$i.rc") &
  count=$((count + 1))
}

skip() {  # skip <名字> <原因>：前提不满足，记 SKIP 与原因，不算失败
  pending_rule="" pending_fix=""
  printf '%s' "$1" >"$work/$count.name"
  printf '%s' "$2" >"$work/$count.skip"
  count=$((count + 1))
}

report() {  # 等已起跑的各项都结束，按起跑顺序打印还没打印的结果
  local name code took
  wait
  while [ "$shown" -lt "$count" ]; do
    name=$(cat "$work/$shown.name")
    if [ -f "$work/$shown.skip" ]; then
      printf 'SKIP  %-46s %s\n' "$name" "$(cat "$work/$shown.skip")"
      skipped=$((skipped + 1))
    else
      code="" took=0
      read -r code took 2>/dev/null <"$work/$shown.rc" || code=${code:-"没写出退出码（检查被杀或中途退出）"}  # 读不到结果算失败，不沿用上一项
      if [ "$code" = 0 ]; then
        printf 'PASS  %-46s %4d s\n' "$name" "$took"
        passed=$((passed + 1))
      else
        printf 'FAIL  %-46s %4d s（退出 %s）\n' "$name" "$took" "$code"
        sed 's/^/      /' "$work/$shown.rule"
        tail -n 30 "$work/$shown.log" | sed 's/^/      | /'
        failed=$((failed + 1))
      fi
    fi
    shown=$((shown + 1))
  done
}

pyc() {  # 被忽略的未跟踪字节码，未跟踪目录折叠成一行；-z 防非 ASCII 路径被加引号；一个没有也返回 0
  git ls-files -z -oi --exclude-standard --directory | tr '\0' '\n' | { grep -E '(^|/)__pycache__/$|\.pyc$' || true; } | sort
}

rm_new_pyc() {  # 删掉 $work/pyc_before 之后新增的；跑前已有的 __pycache__ 目录整个不动（本机局限，CI 检出是干净的）
  local p
  [ -f "$work/pyc_before" ] || return 0
  pyc | comm -13 "$work/pyc_before" - | while IFS= read -r p; do
    echo "removed $p"
    rm -rf -- "./${p%/}" || return 1  # ./ 前缀：文件名含换行被 tr 拆开时，残片也只落在仓库内；去掉结尾斜杠：目录被换成符号链接时只删链接本身
  done
}
