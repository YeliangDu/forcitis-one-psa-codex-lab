#!/usr/bin/env bash
# 轻检查的同一入口（GOV-1023-FV2 O9，定稿 A39）：CI 轻作业跑它，本机交付前也可以选跑它；取代 scripts/precheck.sh。
# 用法：scripts/ci/light.sh [<基线>]   基线默认 origin/main（本脚本不 fetch）；传空串或 40 个 0（新分支的推送）时只跑自测，不比基线。
# 真实检查每次全跑（每项先用 rule 登记它防的失败与改法）；shell 语法、各脚本的自测与 deploy.yml 检查（ci_steps_selftest）在「按改动触发」一节，按 light_triggers.sh 的路径模式挑（没有基线时全跑；自测 light_selftest.sh）；一项失败不中断，最后汇总，有 FAIL 退出 1，基线认不出退出 2。
# CI 里由 .github/workflows/ci.yml 的 backend 作业调用（每个事件都跑）。不在这里的：
# - 后端与前端测试、SQL Server 集成测试：backend、frontend 作业的重步骤（本机要跑就用 scripts/ci/backend_verify.sh、legacy_it.sh，起容器先问用户）；
# - gitleaks 全史扫描、发布脚本自测：secret scan 作业；判档（relevant_change.py 输出）要写 ${GITHUB_OUTPUT}：只在 CI 里；
# OpenAPI 与前端 generated 不入仓（frontend/packages/api/README.md）；本入口不读它们。
# Bash 3.2（macOS）可用：不用关联数组与 mapfile。
set -uo pipefail
export PYTHONDONTWRITEBYTECODE=1  # 不留 __pycache__：backend 作业随后在同一工作区跑测试，遍历目录的测试读到 .pyc 会崩（#693）；python3 -I 不认它，另由 rm_new_pyc 删
root=$(git rev-parse --show-toplevel 2>/dev/null) || { echo "light.sh：不在 git 仓库里" >&2; exit 2; }
cd "$root" || exit 2
base=${1-origin/main}
[ "$base" = 0000000000000000000000000000000000000000 ] && base=""
if [ -n "$base" ] && ! git rev-parse --verify -q "$base^{commit}" >/dev/null; then
  echo "light.sh：基线 $base 不是本机的提交（先 fetch，或传别的基线）" >&2
  exit 2
fi
work=$(mktemp -d "${TMPDIR:-/tmp}/light.XXXXXX") || exit 2
trap 'rm_new_pyc >/dev/null 2>&1; rm -rf "$work"' EXIT  # 中途退出也删本次新增的字节码；trap 不调 exit，退出码不变
changed=$work/changed
# shellcheck source=scripts/ci/light_triggers.sh
. scripts/ci/light_triggers.sh
pyc >"$work/pyc_before" || exit 2  # 跑前已有的被忽略字节码，只删这之后新增的
if [ -n "$base" ] && ! list_changed "$changed"; then
  echo "light.sh：列不出相对 $base 的改动（与 HEAD 没有共同祖先？）" >&2
  exit 2
fi

passed=0 failed=0 skipped=0 count=0 shown=0
started_all=$SECONDS

against_base() {  # against_base <名字> <命令…>：命令后面接上基线；没有基线时 SKIP
  local name=$1
  shift
  if [ -n "$base" ]; then
    run "$name" "$@" "$base"
  else
    skip "$name" "没有基线（新分支的推送）：只跑了自测"
  fi
}

pyyaml() {  # SUPPLY_CHAIN S1：生成文档与若干守护读 YAML，版本钉死
  python3 -c 'import sys, yaml; sys.exit(None if yaml.__version__ == "6.0.3" else f"PyYAML {yaml.__version__} != 6.0.3（SUPPLY_CHAIN S1，CI_RUNNERS.md）")'
}

echo "light.sh：基线 ${base:-无}，HEAD $(git rev-parse --short HEAD)"
command -v python3 >/dev/null || { echo "FAIL  找不到 python3" >&2; exit 1; }

# 供应链、正本校验与被代码读的生成物（#742：build_all 与派生文档已删，留下的检查直接在这里跑）
if [ -z "${CI:-}" ] && ! pyyaml >/dev/null 2>&1; then skip "python3 has PyYAML 6.0.3" "本机 PyYAML 不是 6.0.3：生成物可能与 CI 不同，CI 里这项会红（装 6.0.3 见 CI_RUNNERS.md）"
else
  rule "生成物与守护按钉死的 PyYAML 6.0.3 读写 YAML（SUPPLY_CHAIN S1）；版本不同，生成结果会漂移" "装 PyYAML 6.0.3（CI_RUNNERS.md）"
  run "python3 has PyYAML 6.0.3" pyyaml
fi
rule "新词条违反用语登记规则（terms.yaml 与 backend/glossary/proposed）" "按输出逐条改词条，规则见 scripts/guards/term_rules.py"
run "glossary term rules (terms.yaml, proposed terms)" python3 -I scripts/guards/term_rules.py --check
rule "运行时的 BPMN 流程没在流程目录登记" "在 architecture/ssot/09-processes/catalog.yaml 登记该流程"
run "every runtime BPMN process is catalogued" python3 -I scripts/guards/build_catalog.py --check
rule "代码读了未定稿的提议稿目录" "改读正本；确需暂读的在 architecture/proposal_reads.yaml 登记来源与到期日"
run "code reads no proposals/ (registered exceptions only)" python3 -I scripts/guards/proposal_reads.py
rule "built.yaml 与源码里的聚合不一致，两个改名集成测试会读到旧登记" "运行 python3 scripts/codegen/scan_aggregates.py 重新生成并提交"
run "built.yaml is up to date" python3 scripts/codegen/scan_aggregates.py --check
rule "asyncapi.yaml 与事件分片不一致，事件目录测试会读到旧目录" "重新运行 python3 scripts/codegen/build_asyncapi.py 生成并提交"
run "asyncapi.yaml is up to date" python3 -I scripts/codegen/build_asyncapi.py --check
rule "决定片段格式错、引用断或现行页超限" "按输出改 intent/ 下的片段或 decision-digest/ 清单"
run "decision fragments are valid" python3 -I scripts/session/build_decisions.py --check
rule "老系统读取登记片段缺字段或格式错（ADR-0037）" "按输出改 phase1a/infra/legacy-read/ 下的片段"
run "legacy-read fragments are valid" python3 -I tools/evidence/new_legacy_read.py --check
rule "只在生产上跑的取证工具缺模块、缺名字或缺同目录文件，到生产才崩" "恢复被删的模块或文件，或改掉对它的引用"
run "evidence tools: imports resolve" python3 -I scripts/ci/evidence_imports.py
rule "剧本或卡模板超长、缺节、点名不存在的路径或授权替用户拍板" "按输出的规则号改 playbooks/ 或 .github/ISSUE_TEMPLATE/card.md"
run "playbooks and the card template are well-formed" python3 -I scripts/guards/check_playbooks.py
rule "dev 与 prod 建库脚本、参考 DDL 不一致，生产库结构与测试不同" "改 c3 脚本后运行 phase1a/infra/runbooks/sql/render_prod_sql.sh 重新生成 c9"
run "database scripts aligned" python3 -I scripts/guards/db_scripts_aligned.py

# 只增不改的登记与验收守护（原 backend 作业里的步骤；它们的自测在「按改动触发」一节）
rule "已发布的数据库迁移被改，生产库与代码对不上" "别改已发布的迁移，新增一个迁移"
against_base "migrations immutable" python3 scripts/guards/migrations_immutable.py
rule "系统参与者名录被删改，审计记录里的历史身份失效" "只追加新行，不删改已有行"
against_base "system actors append-only" python3 scripts/guards/system_actors_append_only.py
rule "实现方改了验收测试（底线：实现方不改验收测试）" "撤掉对验收目录的改动；确需改的由测试工程师提交或按裁定加 Acceptance-Ruling 行"
against_base "acceptance guard" python3 scripts/guards/acceptance_guard.py
rule "提交引用了不存在、复用或范围不符的验收裁定" "引用 rulings/ACCEPTANCE_RULINGS.yaml 里真实的裁定号"
against_base "acceptance rulings" python3 scripts/guards/acceptance_rulings.py
rule "新增或改名的文件、目录不合命名规则" "照输出给出的改正名改名；新的顶层目录先问 lead"
against_base "file and directory names (added or renamed)" python3 -I scripts/guards/check_names.py

# 工具库开工单（仓库外）读的规则与角色文件；脚本自身的自测都在「按改动触发」一节
sheet=${ONE_SESSION_TOOLS:-$HOME/.modernize/psa/tools/one-session}/start_sheet/start_sheet_selftest.py  # 开工单固定由 /usr/bin/python3 跑（#47），3.10 以上语法在这里就红；工具库不在本机（CI）时同 acceptance_v2 V05 不判红
rule "开工单在 /usr/bin/python3 下跑不起来（会话开工即失败）" "按输出改工具库 start_sheet 或它读的规则与角色文件"
if [ ! -f "$sheet" ]; then skip "start_sheet under /usr/bin/python3" "本机没有工具库的 $sheet"
elif touches "$t_start_sheet"; then run "start_sheet under /usr/bin/python3" /usr/bin/python3 -I "$sheet"
else skip "start_sheet under /usr/bin/python3" "相关路径没改（按改动触发）"; fi
# 老系统取证用的 OneSql.java 只读（#752）：自测不连库，要本机有 java
rule "取证工具 OneSql.java 放进了写语句或不回滚（生产只读底线）" "按输出修 OneSql.java，或改掉会被拒的取证脚本"
if ! touches "$t_onesql"; then skip "OneSql.java read only (self-test)" "相关路径没改（按改动触发）"
elif ! command -v java >/dev/null; then skip "OneSql.java read only (self-test)" "本机没有 java（CI 的 backend 作业有）"
else run "OneSql.java read only (self-test)" java "$onesql" --self-test "$(dirname "$(dirname "$onesql")")/evidence"; fi
rule '$VAR 后紧跟全角字符，bash 3.2 下 set -u 即崩' '把 $VAR 写成 ${VAR}'
run "shell: no \$VAR right before non-ASCII" var_before_non_ascii
# 验收测试改了就跑全部免容器单测（CLAUDE.md 交付第 2 步）；export-openapi 绑在 process-test-classes 上要起容器，所以用 test-compile surefire:test
rule "改了验收测试却没跑守护类，要等 20 分钟的队列才发现" "按输出修失败的测试类；或本机先跑 bash scripts/codegen/generate_api.sh 生成产物"
if ! touches "$t_acceptance"; then skip "surefire guard classes (acceptance changed)" "相关路径没改（按改动触发）"
elif [ "${LIGHT_ALL:-}" = 1 ]; then skip "surefire guard classes (acceptance changed)" "推到 main：ci.yml 的 container-free unit tests 一步跑同一批"
elif ! bash scripts/codegen/generate_api.sh --check >/dev/null 2>&1; then skip "surefire guard classes (acceptance changed)" "本机没有 OpenAPI 生成产物，先跑 bash scripts/codegen/generate_api.sh"
elif ! maven_offline_ready; then skip "surefire guard classes (acceptance changed)" "本机 Maven 仓库缺依赖，离线解析不过：先跑 cd backend && ./mvnw dependency:go-offline"
else run "surefire guard classes (acceptance changed)" surefire_guards; fi
# relevant_change 的自测连带核对整个仓库的读者分档（读者随任何源文件变），每次都跑
rule "改动的分档漏了读者，该跑的测试被跳过" "按输出把新读者或新路径登记进 scripts/ci/relevant_change.py 的表"
run "relevant_change (self-test)" python3 scripts/ci/relevant_change.py --self-test

# 按改动触发（原 precheck.sh 的同名步骤，检查表在 light_triggers.sh；命令按空格切分）
while IFS='|' read -r key name cmd; do
  # shellcheck disable=SC2086
  if touches "${!key}"; then rule "${name%% (*}：工具做不到它的自测所说的事" "按输出修工具，或在同一改动里改它的自测"; run "$name" $cmd; else skip "$name" "相关路径没改（按改动触发）"; fi
done <<<"$TRIGGERED_CHECKS"
report
rule "本次留下的字节码让随后的后端测试读到 .pyc 而崩（#693）" "手动删掉输出里列的 __pycache__"
run "remove __pycache__ left by this run" rm_new_pyc
report

echo
echo "light.sh：$passed PASS，$failed FAIL，$skipped SKIP，共 $((SECONDS - started_all)) 秒（基线 ${base:-无}）"
[ "$failed" -eq 0 ]
