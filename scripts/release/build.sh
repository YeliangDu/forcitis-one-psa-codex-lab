#!/usr/bin/env bash
# Builds the FORCITIS one release bundle from a clean checkout: backend jar, PC and H5 static files per environment, deployment
# files, migration list, manifest.json and SHA256SUMS, packed as forcitis-one-<version>.tar.gz plus its .sha256.
# Runbook: RELEASE.md among the Phase 1a infra runbooks
#
#   scripts/release/build.sh --version 0.1.0 [--out DIR] [--environments dev,prod] [--isolated-caches DIR] [--source DIR --commit SHA]
#
# --source DIR --commit SHA: build the checkout in DIR, which must be clean and at SHA, with the scripts of this checkout (the release
# workflow runs its own, already reviewed scripts from main against the commit it proved, never the scripts of the commit it builds).
# --isolated-caches DIR: Maven repository, pnpm store and HOME under DIR instead of the user's, so a release never reuses artifacts or
# settings an earlier job could have planted (the release workflow passes a fresh directory).
#
# Frontend build-time values are read from <ENV>_VITE_WECOM_CORP_ID, <ENV>_VITE_WECOM_AGENT_ID, <ENV>_VITE_SSO_PORTAL_URL and
# <ENV>_TARO_APP_WECOM_CORP_ID (ENV = DEV / PROD); every one must be set and non-empty.
set -euo pipefail

usage() { sed -n '2,15p' "$0" | sed 's/^# \{0,1\}//'; exit 2; }
fail() { echo "build.sh: $*" >&2; exit 1; }

version=""
out=""
caches=""
source_dir=""
expected_commit=""
environments="dev,prod"
while [ $# -gt 0 ]; do
  case "$1" in
    --version) version="${2:-}"; shift 2 ;;
    --out) out="${2:-}"; shift 2 ;;
    --isolated-caches) caches="${2:-}"; shift 2 ;;
    --source) source_dir="${2:-}"; shift 2 ;;
    --commit) expected_commit="${2:-}"; shift 2 ;;
    --environments) environments="${2:-}"; shift 2 ;;
    -h|--help) usage ;;
    *) fail "unknown argument: $1" ;;
  esac
done

tools="$(cd "$(dirname "$0")/../.." && pwd)"
# shellcheck source=scripts/release/lib.sh
. "$tools/scripts/release/lib.sh"

release_version_valid "$version" || fail "--version must match ${RELEASE_VERSION_PATTERN} (got '${version}')"
out="${out:-$tools/target/release}"
if [ -n "$source_dir" ]; then
  [[ "$expected_commit" =~ ^[0-9a-f]{40}$ ]] || fail "--source needs --commit with the full sha it must be at"
  repo="$(cd "$source_dir" && pwd)"
else
  repo="$tools"
fi
FRONTEND_VARIABLES="VITE_WECOM_CORP_ID VITE_WECOM_AGENT_ID VITE_SSO_PORTAL_URL TARO_APP_WECOM_CORP_ID"

IFS=',' read -r -a envs <<< "$environments"
[ "${#envs[@]}" -gt 0 ] || fail "--environments is empty"
missing=()
for env in "${envs[@]}"; do
  case "$env" in dev|prod) ;; *) fail "unknown environment '$env' (dev, prod)" ;; esac
  upper="$(printf '%s' "$env" | tr '[:lower:]' '[:upper:]')"
  for name in $FRONTEND_VARIABLES; do
    var="${upper}_${name}"
    [ -n "${!var:-}" ] || missing+=("$var")
  done
done
[ "${#missing[@]}" -eq 0 ] || fail "frontend build-time variables not set: ${missing[*]}"

cd "$repo"
[ -z "$(git status --porcelain)" ] || fail "working tree is not clean; a release is built from a committed state only"
sha="$(git rev-parse HEAD)"
[ -z "$expected_commit" ] || [ "$sha" = "$expected_commit" ] || fail "$repo is at $sha, not at $expected_commit"
commit_epoch="$(git log -1 --format=%ct HEAD)"

java_version="$(java -version 2>&1 | head -1)"
printf '%s' "$java_version" | grep -q '"25' || fail "Java 25 required, found: $java_version"
node_version="$(node --version)"
case "$node_version" in v24.*) ;; *) fail "Node 24 required, found: $node_version" ;; esac
pnpm_pinned="$(node -p "require('./frontend/package.json').packageManager.split('@')[1].split('+')[0]")"
# corepack picks pnpm from the nearest package.json; only frontend/ names it, elsewhere a fresh corepack falls back to its own default
pnpm_found="$(cd frontend && pnpm --version)" || fail "pnpm not runnable in frontend/ (corepack enabled?)"
[ "$pnpm_found" = "$pnpm_pinned" ] || fail "pnpm $pnpm_pinned required, found: $pnpm_found"

name="forcitis-one-${version}"
staging="$(mktemp -d "${TMPDIR:-/tmp}/forcitis-one-release.XXXXXX")"
trap 'rm -rf "$staging"' EXIT
bundle="$staging/$name"
mkdir -p "$bundle/deploy"

echo "== backend jar (tests ran in CI on $sha)"
# Without spring-boot-maven-plugin in the POM, `package` yields a plain jar; repackage it here with the application class
maven_goals=(clean package)
if ! grep -q '<artifactId>spring-boot-maven-plugin</artifactId>' backend/pom.xml; then
  maven_goals+=(org.springframework.boot:spring-boot-maven-plugin:repackage "-Dspring-boot.run.main-class=${RELEASE_APPLICATION_CLASS}")
fi
maven_cache=()
pnpm_cache=(--prefer-offline)
if [ -n "$caches" ]; then
  mkdir -p "$caches/m2" "$caches/pnpm-store" "$caches/home"
  # A fresh HOME as well: ~/.m2/settings.xml, ~/.mavenrc, ~/.npmrc or ~/.gitconfig left by an earlier job must not steer this build
  export HOME="$caches/home"
  maven_cache=("-Dmaven.repo.local=$caches/m2")
  pnpm_cache=(--store-dir "$caches/pnpm-store")
fi
(cd backend && ./mvnw -B -q -DskipTests "-Drevision=${version}" ${maven_cache[@]+"${maven_cache[@]}"} "${maven_goals[@]}")
# Exactly one application jar: test builds leave other forcitis-one-* jars (e.g. forcitis-one-arch-proof-*.jar) in target
jars=()
while IFS= read -r found; do jars+=("$found"); done < <(find backend/target -maxdepth 1 -name 'forcitis-one-[0-9]*.jar' ! -name '*-sources.jar' ! -name '*-javadoc.jar' ! -name '*-tests.jar' | sort)
[ "${#jars[@]}" -eq 1 ] || fail "backend/target must hold exactly one forcitis-one-[0-9]*.jar, found ${#jars[@]}: ${jars[*]-}"
jar="${jars[0]}"
[ "$(basename "$jar")" = "forcitis-one-${version}.jar" ] || fail "built $(basename "$jar"), expected forcitis-one-${version}.jar (-Drevision not applied?)"
cp "$jar" "$bundle/forcitis-one.jar"
release_check_jar_runnable "$bundle/forcitis-one.jar" "$staging/jar-check" || exit 1
release_check_jar_version "$bundle/forcitis-one.jar" "$version" || exit 1

echo "== frontend"
# Dependency install scripts never run: the build has been checked to work without them (RELEASE.md §10)
(cd frontend && pnpm install --frozen-lockfile --ignore-scripts "${pnpm_cache[@]}" --reporter=silent)
# The release checkout has no API snapshots; export from this exact source before either app build.
(cd backend && ./mvnw -B test-compile surefire:test@export-openapi "-Drevision=${version}" ${maven_cache[@]+"${maven_cache[@]}"})
(cd frontend && pnpm run generate:api)
for env in "${envs[@]}"; do
  upper="$(printf '%s' "$env" | tr '[:lower:]' '[:upper:]')"
  build_env=(NODE_ENV=production)
  for name_ in $FRONTEND_VARIABLES; do
    var="${upper}_${name_}"
    build_env+=("${name_}=${!var}")
  done
  rm -rf frontend/apps/web/dist frontend/apps/h5/dist
  for app in web h5; do
    echo "-- $app ($env)"
    build_log="$staging/build-$app-$env.log"
    if ! (cd frontend && env "${build_env[@]}" pnpm --filter "@forcitis/$app" run build) > "$build_log" 2>&1; then
      tail -60 "$build_log" >&2
      fail "$app build for $env failed"
    fi
  done
  [ -f frontend/apps/web/dist/index.html ] || fail "web build for $env produced no index.html"
  [ -f frontend/apps/h5/dist/index.html ] || fail "h5 build for $env produced no index.html"
  mkdir -p "$bundle/web-$env"
  cp -R frontend/apps/web/dist/. "$bundle/web-$env/"
  [ ! -e "$bundle/web-$env/h5" ] || fail "web build for $env already has an h5 entry"
  cp -R frontend/apps/h5/dist "$bundle/web-$env/h5"
done
rm -rf frontend/apps/web/dist frontend/apps/h5/dist

echo "== deployment files"
files_dir=docs/modernization/phase1a/infra/runbooks/files
for f in forcitis-one.service nginx-one.conf forcitis-one.logrotate one.env.template forcitis-one-release-pull.service forcitis-one-release-pull.timer; do
  cp "$files_dir/$f" "$bundle/deploy/$f"
done
cp scripts/release/remote_install.sh "$bundle/deploy/remote_install.sh"
chmod 0755 "$bundle/deploy/remote_install.sh"

echo "== migrations"
(cd backend/src/main/resources && find db/migration -type f -name '*.sql' | LC_ALL=C sort | while read -r f; do
  printf '%s  %s\n' "$(release_sha256 "$f")" "$f"
done) > "$bundle/migrations.txt"
[ -s "$bundle/migrations.txt" ] || fail "no migration files found"

echo "== manifest"
python3 -I "$tools/scripts/release/manifest.py" write "$bundle" \
  --version "$version" --git-sha "$sha" --commit-epoch "$commit_epoch" \
  --jdk "$java_version" --node "$node_version" --pnpm "$pnpm_pinned" \
  --environments "$environments" --frontend-variables "$FRONTEND_VARIABLES"
python3 -I "$tools/scripts/release/manifest.py" verify "$bundle"

mkdir -p "$out"
archive="$out/$name.tar.gz"
release_pack "$staging" "$name" "$archive" "$commit_epoch"
(cd "$out" && printf '%s  %s\n' "$(release_sha256 "$name.tar.gz")" "$name.tar.gz" > "$name.tar.gz.sha256")
echo "release bundle: $archive"
cat "$out/$name.tar.gz.sha256"
