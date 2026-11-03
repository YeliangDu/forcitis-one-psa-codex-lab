#!/usr/bin/env bash
# 交付前的一条本机命令（#856，CLAUDE.md 交付第 2 步）：light.sh、gitleaks（本分支相对基线的提交）、改动范围内的免容器单测与守护类。
# 用法：scripts/ci/check_all.sh [<基线>]   基线默认 origin/main（本脚本不 fetch）。任一步失败退出 1，末尾逐步汇总；前置条件缺失算失败并写明怎么补，不跳过。
# 不在这里的：要起容器的集成测试（CI 的重步骤跑；迁移或数据访问登记表有改动时例外，多跑 DataArchitectureTest 与被改 schema 的表测试，#1056）；前端有改动时多跑与 CI 同一条 pnpm run verify（#1026）。Bash 3.2 可用。
set -uo pipefail
root=$(git rev-parse --show-toplevel 2>/dev/null) || { echo "check_all.sh：不在 git 仓库里" >&2; exit 2; }
cd "$root" || exit 2
base=${1-origin/main}
git rev-parse --verify -q "$base^{commit}" >/dev/null || { echo "check_all.sh：基线 $base 不是本机的提交（先 fetch，或传别的基线）" >&2; exit 2; }
# shellcheck source=scripts/ci/light_triggers.sh
. scripts/ci/light_triggers.sh
work=$(mktemp -d "${TMPDIR:-/tmp}/check_all.XXXXXX") || exit 2
trap 'rm -rf "$work"' EXIT
results=() failed=0

step() {  # step <名字> <命令…>
  local name=$1
  shift
  echo "== $name"
  if "$@"; then results+=("PASS  $name"); else results+=("FAIL  $name"); failed=1; fi
}

# shellcheck disable=SC2329  # called through step "$@"
secrets() {  # 本分支相对基线的提交里没有密钥（CI 的 gitleaks 作业在 PR 上扫同一范围，全史由 push 到 main 与夜间扫）
  command -v gitleaks >/dev/null || { echo "找不到 gitleaks：照 runner 运维页装 8.30.1" >&2; return 1; }
  gitleaks git --redact --no-banner --exit-code 1 --log-opts="$base..HEAD" .
}

# shellcheck disable=SC2329  # called through step "$@"
units() {  # 改动范围内的免容器单测（含守护类）；前置条件缺失即失败
  git diff --name-only "$base...HEAD" >"$work/changed" || return 1
  bash scripts/codegen/generate_api.sh --check >/dev/null 2>&1 || { echo "本机没有 OpenAPI 生成产物：先在仓库根跑 bash scripts/codegen/generate_api.sh" >&2; return 1; }
  maven_offline_ready || { echo "本机 Maven 仓库缺依赖：先跑 cd backend && ./mvnw dependency:go-offline" >&2; return 1; }
  surefire_guards "$work/changed"
}

# shellcheck disable=SC2329  # called through step "$@"
frontend_verify() {  # 前端有改动：跑 CI 前端 verify 同一条命令（#1026，#1011 事件：本机只跑 typecheck，check:copy 到 CI 才红），约 22 秒含 build；后端错误码与前端文案表的同步另由 light.sh 的 error_catalogue 查（#914）
  [ -d frontend/node_modules ] || { echo "本机没有前端依赖：先跑 cd frontend && pnpm install --frozen-lockfile --prefer-offline" >&2; return 1; }
  (cd frontend && pnpm run verify)
}

# shellcheck disable=SC2329  # called through step "$@"
schema_checks() {  # 迁移或数据访问登记表有改动（#1056：#1019、#1050 两次本机全绿、队列里 DataArchitectureTest 红）：跑 DataArchitectureTest，加被改 schema 的表结构测试；要起 Testcontainers，约 20 秒起，每个被改 schema 再加它的表测试
  local schema dir name tests="DataArchitectureTest" its="" file
  docker info >/dev/null 2>&1 || { echo "Docker 没起：先打开 Docker Desktop（或 bash scripts/release/docker_ensure.sh），迁移和登记表的检查要起 Testcontainers" >&2; return 1; }
  maven_offline_ready || { echo "本机 Maven 仓库缺依赖：先跑 cd backend && ./mvnw dependency:go-offline" >&2; return 1; }
  while IFS= read -r schema; do
    dir=${schema/_//}   # organization_workforce -> organization/workforce：schema 与测试包同名
    while IFS= read -r file; do
      name=$(basename "$file" .java)
      case "$name" in *IT) its="$its,$name" ;; *) tests="$tests,$name" ;; esac
    done < <(find "backend/src/test/java/com/forcitis/one/$dir" "backend/src/test/java/com/forcitis/one/acceptance/$dir" -name '*.java' 2>/dev/null \
      | grep -E '(Tables|Constraints?|Schema|Migrations?|Trigger)[A-Za-z]*(IntegrationTest|Test|IT)\.java$')
  done < <(sed -n 's#^backend/src/main/resources/db/migration/\([a-z0-9_]*\)/.*#\1#p' "$changed" | sort -u)
  echo "DataArchitectureTest 与被改 schema 的表测试：surefire ${tests}；failsafe ${its#,}"
  (cd backend && rm -rf target/surefire-reports target/failsafe-reports && ./mvnw -B -q -o test-compile surefire:test -Dtest="$tests" -Djacoco.skip=true) || return 1
  [ -z "$its" ] || (cd backend && ./mvnw -B -q -o failsafe:integration-test failsafe:verify -Dit.test="${its#,}" -Djacoco.skip=true -DfailIfNoSpecifiedTests=false) || return 1
  for name in ${tests//,/ }; do ls backend/target/surefire-reports/TEST-*."$name".xml >/dev/null 2>&1 || { echo "$name 没出报告：没跑" >&2; return 1; }; done
  for name in ${its//,/ }; do ls backend/target/failsafe-reports/TEST-*."$name".xml >/dev/null 2>&1 || { echo "$name 没出报告：没跑" >&2; return 1; }; done
}

list_changed "$work/changed_all" || { echo "check_all.sh：列不出改动的文件" >&2; exit 2; }
changed=$work/changed_all
step "light.sh (base $base)" bash scripts/ci/light.sh "$base"
step "gitleaks ($base..HEAD)" secrets
step "container-free unit and guard classes for the change range" units
if touches '^backend/src/main/resources/db/migration/|^backend/architecture/data-access-registry\.yaml$'; then step "data architecture (migrations or data-access registry changed)" schema_checks; fi
if touches '^frontend/'; then step "frontend verify (frontend/ changed)" frontend_verify; fi
echo
printf '%s\n' "${results[@]}"
exit "$failed"
