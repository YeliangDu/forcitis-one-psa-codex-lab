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

pyyaml() {  # 供应链规则 S1：守护与生成器读写 YAML，版本钉死
  python3 -c 'import sys, yaml; sys.exit(None if yaml.__version__ == "6.0.3" else f"PyYAML {yaml.__version__} != 6.0.3（供应链规则 S1，装法见 runner 运维页）")'
}

echo "light.sh：基线 ${base:-无}，HEAD $(git rev-parse --short HEAD)"
command -v python3 >/dev/null || { echo "FAIL  找不到 python3" >&2; exit 1; }

# 供应链、正本校验与被代码读的生成物（#742：build_all 与派生文档已删，留下的检查直接在这里跑）
if [ -z "${CI:-}" ] && ! pyyaml >/dev/null 2>&1; then skip "python3 has PyYAML 6.0.3" "本机 PyYAML 不是 6.0.3：生成物可能与 CI 不同，CI 里这项会红（装 6.0.3 见 runner 运维页）"
else
  rule "生成物与守护按钉死的 PyYAML 6.0.3 读写 YAML（供应链规则 S1）；版本不同，生成结果会漂移" "装 PyYAML 6.0.3（见 runner 运维页）"
  run "python3 has PyYAML 6.0.3" pyyaml
fi
rule "新词条违反用语登记规则（terms.yaml 与 backend/glossary/proposed）" "按输出逐条改词条，规则见 scripts/guards/term_rules.py"
run "glossary term rules (terms.yaml, proposed terms)" python3 -I scripts/guards/term_rules.py --check
rule "运行时的 BPMN 流程没在流程目录登记" "在 architecture/ssot/processes/catalog.yaml 登记该流程"
run "every runtime BPMN process is catalogued" python3 -I scripts/guards/build_catalog.py --check
rule "代码读了未定稿的提议稿目录" "改读正本；确需暂读的在 architecture/proposal-reads.yaml 登记来源与到期日"
run "code reads no proposals/ (registered exceptions only)" python3 -I scripts/guards/proposal_reads.py
rule "built.yaml 与源码里的聚合不一致，两个改名集成测试会读到旧登记" "运行 python3 scripts/codegen/scan_aggregates.py 重新生成并提交"
run "built.yaml is up to date" python3 scripts/codegen/scan_aggregates.py --check
rule "asyncapi.yaml 与事件分片不一致，事件目录测试会读到旧目录" "重新运行 python3 scripts/codegen/build_asyncapi.py 生成并提交"
run "asyncapi.yaml is up to date" python3 -I scripts/codegen/build_asyncapi.py --check
rule "决定片段格式错、引用断或现行页超限" "按输出改 decisions/ 下的片段或 decisions/decision-digest/ 清单"
run "decision fragments are valid" python3 -I scripts/session/build_decisions.py --check
rule "老系统读取登记片段缺字段或格式错（ADR-0037）" "按输出改 ops/legacy-read/ 下的片段"
run "legacy-read fragments are valid" python3 -I tools/evidence/new_legacy_read.py --check
rule "只在生产上跑的取证工具缺模块、缺名字或缺同目录文件，到生产才崩" "恢复被删的模块或文件，或改掉对它的引用"
run "evidence tools: imports resolve" python3 -I scripts/ci/evidence_imports.py
rule "剧本或卡模板超长、缺节、点名不存在的路径或授权替用户拍板；CLAUDE.md、规则、角色、PROMPT.md、PR 模板或脚本说明点名不存在的路径（P6）" "按输出的规则号改 rules/playbooks/、.github/ISSUE_TEMPLATE/card.md 或所列文件"
run "playbooks and the card template are well-formed" python3 -I scripts/guards/check_playbooks.py
rule "规则或角色文件的「Enforced by」、编码与命名规范的检查列点名了不存在的脚本、作业、测试或工具，或 coding-core §A 的范例类与成员不存在（规则看似有人执行，实则没有）" "按输出的 RE 规则号改成真实的检查名，或写 manual: <角色> / <频次>（规范表里写 manual: review、planned: <编号>）"
run "every rule names an enforcer that exists" python3 -I scripts/guards/check_rules_enforced.py
rule "policy.json 有数值或字符串键没有脚本读、也没登记为规则文本读者（上限或名字看似生效、实则无人执行）" "按输出的 PK 规则号删键，或让需要它的检查读它，或在 policy_keys_read.py 的 RULE_READERS 登记"
run "every numeric or string policy.json key has a reader" python3 -I scripts/guards/policy_keys_read.py
rule "JdbcClient 的 SQL 里拼进了值（可注入、计划不复用）；或登记的构造方法、调用点已不再使用" "按输出的 SV 规则号把值改成 :param 绑定，或在 scripts/guards/sql_values.py 登记构造方法并写理由"
run "no value concatenated into SQL" python3 -I scripts/guards/sql_values.py
rule "智能体读的规则文件超出层上限（A 开局、B 每路径，policy.json rules.layer_*_max_kb），或 .claude/rules 不是指向带 paths: 文档的相对软链（#827），会话读不全或开局多读" "按输出的 RS 规则号删短所列文件、收窄 paths:、把内容移到按编号寻址的文档（C 层），或按提示修软链"
run "agent-read rules: layer A/B sizes, .claude/rules links" python3 -I scripts/guards/rule_sizes.py
rule "代码文件超过 1000 行（复审必改），或登记的超长文件变长、登记过期" "按输出的 FL 规则号拆文件，或按提示更新 scripts/guards/file_lines.py 的 OVER"
run "code files within 1000 lines" python3 -I scripts/guards/file_lines.py
rule "文档或脚本引用的仓库路径在树上不存在（旧文档引用搬走、没建成或写错的脚本与文件）；或登记的已知缺失过期" "按输出的 CP 规则号改成真路径，或说明已删除（git show），登记只减不增：scripts/guards/cited_paths_known.txt"
run "cited repo paths exist" python3 -I scripts/guards/cited_paths.py
rule "后端有包既没标 @NullMarked 也没登记在 null-unmarked-packages.txt（NullAway 不查它），或登记过期" "按输出的 NM 规则号给包的 package-info.java 加 @NullMarked，或删去过期的登记行"
run "every backend package is @NullMarked or listed" python3 -I scripts/guards/null_marked.py
rule "dev 与 prod 建库脚本、参考 DDL 不一致，生产库结构与测试不同" "改 dev-db 脚本后运行 ops/runbooks/sql/render-prod-sql.sh 重新生成 prod-db"
run "database scripts aligned" python3 -I scripts/guards/db_scripts_aligned.py

# 只增不改的登记与验收守护（原 backend 作业里的步骤；它们的自测在「按改动触发」一节）
rule "已发布的数据库迁移被改，生产库与代码对不上" "别改已发布的迁移，新增一个迁移"
against_base "migrations immutable" python3 scripts/guards/migrations_immutable.py
rule "系统参与者名录被删改，审计记录里的历史身份失效" "只追加新行，不删改已有行"
against_base "system actors append-only" python3 scripts/guards/system_actors_append_only.py
rule "实现方改了验收测试（底线：实现方不改验收测试）" "撤掉对验收目录的改动；确需改的由测试工程师提交或按裁定加 Acceptance-Ruling 行"
against_base "acceptance guard" python3 scripts/guards/acceptance_guard.py
rule "提交引用了不存在、复用或范围不符的验收裁定" "引用 rules/rulings/acceptance-rulings.yaml 里真实的裁定号"
against_base "acceptance rulings" python3 scripts/guards/acceptance_rulings.py
rule "已建 BC 的业务规则没有测试引用且不在未引用清单里，或清单变长、登记过期（#1271）" "按输出的 RT 规则号：给规则补测试（名字或 @Covers 写规则号）；不要往 scripts/guards/rule_tests_known.txt 加行，已被引用的行要删"
against_base "built-BC rules cited by tests" python3 scripts/guards/rule_tests.py
rule "文件、目录不合命名规则（全仓）" "照输出给出的改正名改名；新的顶层目录先问 lead"
run "file and directory names (whole tree)" python3 -I scripts/guards/check_names.py

# 工具库开工单（仓库外）读的规则与角色文件；脚本自身的自测都在「按改动触发」一节
sheet=${ONE_SESSION_TOOLS:-$HOME/.modernize/psa/tools/one-session}/start_sheet/start_sheet_selftest.py  # 开工单固定由 /usr/bin/python3 跑（#47），3.10 以上语法在这里就红；工具库不在本机（CI）时同 acceptance_v2 V05 不判红
rule "开工单在 /usr/bin/python3 下跑不起来（会话开工即失败）" "按输出改工具库 start_sheet 或它读的规则与角色文件"
if [ ! -f "$sheet" ]; then skip "start_sheet under /usr/bin/python3" "本机没有工具库的 $sheet"
elif touches "$t_start_sheet"; then run "start_sheet under /usr/bin/python3" /usr/bin/python3 -I "$sheet"
else skip "start_sheet under /usr/bin/python3" "相关路径没改（按改动触发）"; fi
rule "开工单最坏体量（交接写满 2 KB 加最长剧本）超过 4 KB，会话开工时剧本尾部被截掉" "删短最长的剧本（rules/playbooks/ 下，最坏体量＝路由提示＋2 KB 交接＋最长剧本）；/usr/bin/python3 start_sheet.py --check 列出各会话最坏体量"
if [ ! -f "$sheet" ]; then skip "start_sheet worst case within 4 KB" "本机没有工具库的 $sheet"
elif touches "$t_start_sheet"; then run "start_sheet worst case within 4 KB" /usr/bin/python3 -I "${sheet%_selftest.py}.py" --check
else skip "start_sheet worst case within 4 KB" "相关路径没改（按改动触发）"; fi
# 老系统取证用的 OneSql.java 只读（#752）：自测不连库，要本机有 java
rule "取证工具 OneSql.java 放进了写语句或不回滚（生产只读底线）" "按输出修 OneSql.java，或改掉会被拒的取证脚本"
if ! touches "$t_onesql"; then skip "OneSql.java read only (self-test)" "相关路径没改（按改动触发）"
elif ! command -v java >/dev/null; then skip "OneSql.java read only (self-test)" "本机没有 java（CI 的 backend 作业有）"
else run "OneSql.java read only (self-test)" java "$onesql" --self-test "$onesql_evidence"; fi
# shellcheck disable=SC2016  # the rule text shows $VAR literally
rule '$VAR 后紧跟全角字符，bash 3.2 下 set -u 即崩' '把 $VAR 写成 ${VAR}'
run "shell: no \$VAR right before non-ASCII" var_before_non_ascii
# 后端新增错误码而前端文案表没加：CI 的前端类型检查才红（#888），本机只读源码对一遍
rule "后端错误码与前端文案表（error-messages.ts）不一致，CI 的前端类型检查会红" "照输出：后端新增的错误码在文案表里加一条（没有专属文案写 null）；表里多出的删掉"
if touches "$t_error_catalogue"; then run "error_catalogue (backend error codes vs frontend table)" python3 -I scripts/ci/error_catalogue.py .
else skip "error_catalogue (backend error codes vs frontend table)" "相关路径没改（按改动触发）"; fi
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

# 静态检查 shell：.sh 改了才跑（与 CI 同一命令，#1194）；本机没装就跳过并说明，不算失败
if ! touches "$t_shell"; then skip "shellcheck (tracked *.sh)" "相关路径没改（按改动触发）"
elif ! command -v shellcheck >/dev/null 2>&1; then skip "shellcheck (tracked *.sh)" "本机没有 shellcheck（CI 仍会跑，固定 0.11.0）：brew install shellcheck"
else rule "CI 的 shellcheck 步骤会拦下这个 .sh（本机版本可能与 CI 的 0.11.0 略有出入）" "按输出修，或在原处用 # shellcheck disable=SCxxxx 并写明原因（#856）"; run "shellcheck (tracked *.sh)" shellcheck_tracked; fi
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
